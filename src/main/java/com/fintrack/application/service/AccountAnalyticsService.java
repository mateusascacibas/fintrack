package com.fintrack.application.service;

import com.fintrack.domain.model.Account;
import com.fintrack.domain.model.MonthlyAccountSummary;
import com.fintrack.domain.model.Transaction;
import com.fintrack.domain.model.TransactionType;
import com.fintrack.domain.port.out.AccountRepository;
import com.fintrack.domain.port.out.TransactionRepository;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Service de analytics financeiro — camada de aplicação.
 *
 * Esta classe aplica TODOS os conceitos dos Dias 4, 5 e 6 em contexto real:
 *
 *   Dia 4 — CompletableFuture + ExecutorService:
 *     Busca de conta e transações em paralelo (duas queries simultâneas).
 *     thenCombine para combinar quando AMBAS terminarem.
 *     exceptionally para fallback em caso de erro.
 *
 *   Dia 5 — Streams + Short-Circuit:
 *     flatMap para achatar listas de transações de várias contas.
 *     anyMatch para detectar risco (para no primeiro match).
 *
 *   Dia 6 — Parallel Streams + Collectors customizados:
 *     parallelStream() APENAS onde faz sentido: CPU-bound, ArrayList grande.
 *     teeing() para dois resultados em um loop.
 *     Collector customizado para estatísticas precisas com BigDecimal.
 *
 * WHY no application layer e não no domain?
 *   CompletableFuture e ExecutorService são infraestrutura de concorrência.
 *   O domínio (Account.java) não sabe que existe paralelismo — ele responde
 *   perguntas de negócio de forma pura. Este service orquestra.
 */
// @Service  ← descomentado na versão Spring (evita import Spring neste exemplo puro)
public class AccountAnalyticsService {

    private final AccountRepository     accountRepository;
    private final TransactionRepository transactionRepository;

    // Pool dedicado para operações de I/O (queries ao banco)
    // WHY separado do ForkJoinPool.commonPool()?
    //   Dia 6 — ForkJoinPool é global. Queries bloqueantes em I/O saturariam
    //   o commonPool, afetando parallelStream() em toda a JVM.
    //   Pool dedicado = isolamento de responsabilidade.
    private final ExecutorService ioExecutor =
            Executors.newFixedThreadPool(
                    Runtime.getRuntime().availableProcessors(),
                    r -> Thread.ofVirtual().name("analytics-io-", 0).unstarted(r)
                    // WHY Virtual Threads (Dia 4)?
                    //   Queries ao banco são I/O-bound: a thread bloqueia esperando resposta.
                    //   Virtual Thread suspende durante o bloqueio — carrier fica livre.
                    //   Mais eficiente do que platform threads para I/O.
            );

    public AccountAnalyticsService(
            AccountRepository accountRepository,
            TransactionRepository transactionRepository) {
        this.accountRepository     = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // COMPLETABLEFUTURE — Dia 4
    //
    // Carrega dados em paralelo e combina quando ambos chegarem.
    // Sem CompletableFuture: busca sequencial — conta (50ms) DEPOIS
    // transações (80ms) = 130ms total.
    // Com CompletableFuture: ambas simultâneas = ~80ms total.
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Gera relatório mensal carregando dados em paralelo.
     *
     * Dia 4 — supplyAsync: submete cada query ao ioExecutor em sua própria thread.
     * Dia 4 — thenCombine: combina quando AMBAS as queries terminarem.
     * Dia 6 — getMonthlyReport: usa teeing() internamente para um único loop.
     */
    public CompletableFuture<MonthlyAccountSummary> getMonthlyReportAsync(
            UUID accountId, YearMonth month) {

        // Dispara as duas queries SIMULTANEAMENTE (Dia 4)
        CompletableFuture<Account> cfAccount = CompletableFuture
                .supplyAsync(
                        () -> accountRepository.findById(accountId)
                                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId)),
                        ioExecutor
                );

        CompletableFuture<List<Transaction>> cfTransactions = CompletableFuture
                .supplyAsync(
                        () -> transactionRepository.findByAccountIdAndMonth(accountId, month),
                        ioExecutor
                );

        // Combina quando AMBAS terminarem (Dia 4 — thenCombine)
        return cfAccount
                .thenCombine(cfTransactions, (account, transactions) -> {
                    // Aqui: Account.getMonthlyReport usa teeing() — Dia 6
                    // A lista de transactions já está filtrada pelo mês no repo
                    return account.getMonthlyReport(month);
                })
                .exceptionally(ex -> {
                    // Dia 4 — exceptionally: fallback em caso de erro
                    // Em produção: logar o erro e lançar exceção de negócio
                    throw new CompletionException(
                            "Failed to generate monthly report for account " + accountId, ex
                    );
                });
    }

    /**
     * Carrega dashboard completo: conta + últimas transações + top categorias.
     *
     * Dia 4 — allOf: aguarda TRÊS operações paralelas antes de combinar.
     */
    public CompletableFuture<AccountDashboard> getDashboardAsync(UUID accountId) {
        YearMonth currentMonth = YearMonth.now();

        CompletableFuture<Account> cfAccount = CompletableFuture
                .supplyAsync(() -> accountRepository.findById(accountId).orElseThrow(), ioExecutor);

        CompletableFuture<List<Transaction>> cfRecentTxs = CompletableFuture
                .supplyAsync(
                        () -> transactionRepository.findRecentByAccountId(accountId, 10),
                        ioExecutor
                );

        CompletableFuture<MonthlyAccountSummary> cfMonthly = CompletableFuture
                .supplyAsync(
                        () -> accountRepository.findById(accountId)
                                .map(acc -> acc.getMonthlyReport(currentMonth))
                                .orElseThrow(),
                        ioExecutor
                );

        // allOf: aguarda as três simultaneamente (Dia 4)
        return CompletableFuture.allOf(cfAccount, cfRecentTxs, cfMonthly)
                .thenApply(v -> new AccountDashboard(
                        cfAccount.join(),      // .join() não bloqueia — allOf já garantiu que terminou
                        cfRecentTxs.join(),
                        cfMonthly.join()
                ));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PARALLEL STREAMS — Dia 6 (apenas onde faz sentido)
    //
    // Regra: CPU-bound + ArrayList grande + sem I/O + sem sorted() complexo
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Detecta transações suspeitas em um lote grande de contas.
     *
     * WHY parallelStream() aqui e NÃO em outros lugares?
     *   1. Fonte: ArrayList de Account — divisão O(1) por índice (Dia 6 — Spliterator)
     *   2. Operação: isHighRiskTransaction é CPU-bound (comparação de BigDecimal)
     *   3. Sem I/O: os dados já estão em memória
     *   4. Sem sorted(): não há custo de merge sort paralelo
     *   5. Volume: lote grande justifica o overhead de divisão do ForkJoinPool
     *
     * WHY NÃO usar parallelStream() no getMonthlyReportAsync()?
     *   Aquele é I/O-bound (queries ao banco). Virtual Threads + CompletableFuture
     *   é a abstração correta para I/O. parallelStream() satura o ForkJoinPool.
     */
    public List<SuspiciousTransactionAlert> detectSuspiciousTransactions(
            List<Account> accounts,        // ArrayList grande — bom para parallel
            BigDecimal    riskThreshold) {

        return accounts.parallelStream()          // Dia 6 — parallelStream() justificado
                .filter(Account::isActive)            // stateless — paraleliza bem (Dia 5)
                .filter(acc -> acc.hasHighRiskTransaction(riskThreshold)) // short-circuit internamente (Dia 5)
                .flatMap(acc ->                       // flatMap para achatar (Dia 5)
                        acc.getTransactions().stream()
                                .filter(Transaction::isCompleted)
                                .filter(tx -> tx.isHighValue(riskThreshold))
                                .map(tx -> new SuspiciousTransactionAlert(
                                        acc.getId(), acc.getName(),
                                        tx.id(), tx.amount(), tx.category()
                                ))
                )
                .collect(Collectors.toList());        // thread-safe via combiner (Dia 6)
        // WHY toList() e não toUnmodifiableList()?
        //   Collector.toList() em parallelStream usa combiner para juntar listas parciais.
        //   O resultado pode ser modificado pelo chamador se necessário.
    }

    /**
     * Ranking de gastos por categoria entre todas as contas de um usuário.
     *
     * Combina flatMap (Dia 5) + groupingBy com downstream (Dia 6).
     * Stream sequencial — dados já em memória, lista não necessariamente grande.
     */
    public Map<String, BigDecimal> getSpendingRankingAcrossAccounts(
            List<Account> accounts, YearMonth month) {

        return accounts.stream()                  // sequencial — lista de contas é pequena
                .filter(Account::isActive)
                .flatMap(acc ->                       // achata List<Account> → Stream<Transaction>
                        acc.getTransactionsByMonth(month).stream()
                )
                .filter(Transaction::isDebit)         // lazy (Dia 5)
                .filter(Transaction::isCompleted)     // lazy (Dia 5)
                .collect(Collectors.groupingBy(       // Dia 6 — groupingBy + downstream
                        Transaction::category,
                        Collectors.reducing(
                                BigDecimal.ZERO,
                                Transaction::amount,
                                BigDecimal::add
                        )
                ));
        // Resultado: {"Alimentação": 1250.00, "Transporte": 430.00, ...}
        // Um único loop — sem loops aninhados manuais
    }

    /**
     * Verifica saúde financeira de todas as contas: todas processadas no mês?
     *
     * allMatch (Dia 5 — Short-Circuit):
     *   Para na PRIMEIRA conta que não está completamente processada.
     *   Com 1000 contas onde a 3ª tem pendências → processa 3 contas, para.
     */
    public boolean isAllAccountsFullyProcessed(List<Account> accounts, YearMonth month) {
        return accounts.stream()
                .filter(Account::isActive)
                .allMatch(acc -> acc.isMonthFullyProcessed(month)); // short-circuit (Dia 5)
    }

    // Value objects de resultado — Records para dados imutáveis de saída
    public record AccountDashboard(
            Account                 account,
            List<Transaction>       recentTransactions,
            MonthlyAccountSummary   monthlySummary
    ) {}

    public record SuspiciousTransactionAlert(
            UUID       accountId,
            String     accountName,
            UUID       transactionId,
            BigDecimal amount,
            String     category
    ) {}
}
