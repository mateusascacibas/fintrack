package com.fintrack.domain.model;

import com.fintrack.domain.exception.InsufficientBalanceException;
import com.fintrack.domain.exception.InvalidAmountException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Domain model — Account.
 *
 * Este é o Aggregate Root do domínio financeiro: toda operação que envolve
 * dinheiro passa por aqui. As regras de negócio vivem nesta classe — não
 * nos services, não nos controllers.
 *
 * DECISÕES DE DESIGN (com referências aos dias de estudo):
 *
 *   Dia 1 — JVM/Heap: Account vive no Heap enquanto o ApplicationContext
 *            Spring segurar referência. As Transactions são alcançáveis via
 *            Account → não são coletadas enquanto Account existir.
 *
 *   Dia 2 — GC: Em produção com ZGC Generational (-XX:+UseZGC -XX:+ZGenerational),
 *            pausas de GC não impactam as operações de debit/credit.
 *
 *   Dia 3 — Reference Types: Account no cache → SoftReference (caro de recriar
 *            via banco). Em WeakHashMap de metadata → VALUE não pode referenciar
 *            a KEY (armadilha que vimos no quiz).
 *
 *   Dia 4 — Thread Safety: debit() e credit() são synchronized para garantir
 *            atomicidade da operação de saldo. Em produção com Virtual Threads
 *            (Java 21), usar ReentrantLock em vez de synchronized para evitar
 *            pinning da virtual thread ao carrier.
 *
 *   Dia 5 — Streams: todos os métodos de analytics usam Streams com lazy
 *            evaluation. Filtros mais seletivos (status == COMPLETED) vêm
 *            antes dos menos seletivos (type == DEBIT).
 *
 *   Dia 6 — Collectors: teeing() para dois resultados em um loop,
 *            groupingBy + downstream collectors para breakdowns por categoria.
 */
public final class Account {
    // WHY final?
    //   Effective Java Item 17: minimize herança. Account não foi projetado
    //   para ser estendido. final evita subclasses que possam violar invariantes
    //   (ex: uma subclasse que sobrescreve debit() sem validar saldo).

    // ─────────────────────────────────────────────────────────────────────────
    // Campos imutáveis — identidade e características da conta
    // WHY final? Identidade não muda. Nome e tipo são imutáveis por negócio.
    // ─────────────────────────────────────────────────────────────────────────
    private final UUID          id;
    private String        name;
    private final AccountType   type;
    private final LocalDateTime createdAt;

    // ─────────────────────────────────────────────────────────────────────────
    // Estado mutável — protegido por sincronização (Dia 4)
    //
    // WHY não usar AtomicReference<BigDecimal> para balance?
    //   debit() precisa de DUAS operações atômicas: verificar saldo E subtrair.
    //   AtomicReference garante atomicidade por operação, não por sequência.
    //   synchronized garante que ninguém interfere entre a verificação e a subtração.
    // ─────────────────────────────────────────────────────────────────────────
    private BigDecimal   balance;
    private boolean      active;
    private LocalDateTime updatedAt;

    // Lista de transações — acesso controlado
    // WHY ArrayList e não CopyOnWriteArrayList?
    //   Escrita em transações é protegida por synchronized nos métodos debit/credit.
    //   CopyOnWriteArrayList seria overhead desnecessário — a sincronização já existe.
    private final List<Transaction> transactions;

    // ─────────────────────────────────────────────────────────────────────────
    // Constructor PRIVADO — Effective Java Item 1
    //
    // WHY privado?
    //   Forçar uso dos factory methods. Constructor não tem nome — o caller
    //   não sabe o que está criando. Factory method tem nome descritivo.
    //   Também permite validação centralizada antes de criar o objeto.
    // ─────────────────────────────────────────────────────────────────────────
    private Account(UUID id, String name, AccountType type, BigDecimal initialBalance) {
        this.id           = Objects.requireNonNull(id, "Account id cannot be null");
        this.name         = validateName(name);
        this.type         = Objects.requireNonNull(type, "Account type cannot be null");
        this.balance      = validateBalance(initialBalance);
        this.active       = true;
        this.createdAt    = LocalDateTime.now();
        this.updatedAt    = LocalDateTime.now();
        this.transactions = new ArrayList<>();
    }

    private Account(UUID id, String name, AccountType type, BigDecimal balance,
                    boolean active, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id           = Objects.requireNonNull(id,        "Account id cannot be null");
        this.name         = validateName(name);
        this.type         = Objects.requireNonNull(type,      "Account type cannot be null");
        this.balance      = Objects.requireNonNull(balance,   "Balance cannot be null");
        this.active       = active;
        this.createdAt    = Objects.requireNonNull(createdAt, "CreatedAt cannot be null");
        this.updatedAt    = Objects.requireNonNull(updatedAt, "UpdatedAt cannot be null");
        this.transactions = new ArrayList<>();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Factory Methods — Effective Java Item 1
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Cria uma nova conta com saldo inicial zero.
     * Mais comum para contas correntes e poupança.
     */
    public static Account create(String name, AccountType type) {
        return new Account(UUID.randomUUID(), name, type, BigDecimal.ZERO);
    }

    /**
     * Cria uma nova conta com saldo inicial informado.
     * Útil para importação de contas existentes.
     */
    public static Account createWithBalance(String name, AccountType type, BigDecimal initialBalance) {
        return new Account(UUID.randomUUID(), name, type, initialBalance);
    }

    /**
     * Reconstitui uma Account a partir dos dados persistidos.
     * Usado pelo AccountRepositoryAdapter ao carregar do banco.
     *
     * WHY método separado para reconstituição?
     *   create() gera novo UUID e timestamps. reconstitute() usa os valores
     *   existentes do banco — são semânticas completamente diferentes.
     */
    public static Account reconstitute(
            UUID id, String name, AccountType type, BigDecimal balance,
            boolean active, LocalDateTime createdAt, LocalDateTime updatedAt,
            List<Transaction> transactions) {

        // Usa o segundo constructor — preserva createdAt e updatedAt do banco
        Account account = new Account(id, name, type, balance, active, createdAt, updatedAt);
        account.transactions.addAll(
                Objects.requireNonNullElse(transactions, List.of())
        );
        return account;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Operações de negócio — o coração do domínio
    //
    // WHY synchronized e não ReentrantLock?
    //   Em Java 21 com Virtual Threads, synchronized pina a VT ao carrier
    //   (Dia 4 — pinning). Para produção com VTs habilitadas, trocar por:
    //
    //   private final ReentrantLock lock = new ReentrantLock();
    //
    //   public Transaction debit(...) {
    //       lock.lock();
    //       try { ... } finally { lock.unlock(); }
    //   }
    //
    //   Mantemos synchronized aqui como ponto de evolução intencional.
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Debita um valor da conta.
     *
     * Regras de domínio:
     *   1. Conta deve estar ativa
     *   2. Valor deve ser positivo
     *   3. Saldo deve ser suficiente
     *
     * Thread safety (Dia 4): synchronized garante que duas threads não
     * debitam simultaneamente e ultrapassam o saldo.
     *
     * @throws InvalidAmountException       se amount <= 0
     * @throws InsufficientBalanceException se balance < amount
     * @throws IllegalStateException        se conta inativa
     */
    public synchronized Transaction debit(
            BigDecimal amount, String description, String category) {

        validateActive();
        validateAmount(amount);

        // Regra de negócio: saldo suficiente
        // WHY não usar .subtract() diretamente e ver se fica negativo?
        //   Clareza semântica. A exceção carrega o contexto (saldo atual, valor pedido).
        if (balance.compareTo(amount) < 0) {
            throw new InsufficientBalanceException(
                    "Insufficient balance. Current: %s, Required: %s"
                            .formatted(balance.toPlainString(), amount.toPlainString())
            );
        }

        // Operação atômica: subtrair saldo E registrar transação juntos
        this.balance   = balance.subtract(amount).setScale(2, RoundingMode.HALF_UP);
        this.updatedAt = LocalDateTime.now();

        Transaction tx = Transaction.createDebit(id, amount, description, category);
        transactions.add(tx);
        return tx;
    }

    /**
     * Credita um valor na conta.
     *
     * Thread safety (Dia 4): synchronized — mesma razão do debit().
     * Crédito não precisa verificar saldo, mas precisa ser atômico com
     * o registro da transação.
     */
    public synchronized Transaction credit(
            BigDecimal amount, String description, String category) {

        validateActive();
        validateAmount(amount);

        this.balance   = balance.add(amount).setScale(2, RoundingMode.HALF_UP);
        this.updatedAt = LocalDateTime.now();

        Transaction tx = Transaction.createCredit(id, amount, description, category);
        transactions.add(tx);
        return tx;
    }

    /** Desativa a conta. Operações de debit/credit falharão após isso. */
    public synchronized void deactivate() {
        this.active    = false;
        this.updatedAt = LocalDateTime.now();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Analytics com Streams (Dia 5) e Collectors (Dia 6)
    //
    // WHY métodos de analytics no domínio e não no service?
    //   "Qual é o maior débito da conta?" é uma pergunta de NEGÓCIO.
    //   O domínio deve saber responder. O service orquestra, o domínio responde.
    //
    //   Tudo aqui é puro: sem Spring, sem JPA, sem rede.
    //   Testável com JUnit simples — sem @SpringBootTest.
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Retorna transações completadas por tipo.
     *
     * Lazy evaluation (Dia 5): .filter() não executa até .collect().
     * Ordem dos filters: status primeiro (mais seletivo para débitos
     * em contas com muitas transações completadas) → type depois.
     */
    public List<Transaction> getCompletedTransactionsByType(TransactionType type) {
        return transactions.stream()
                .filter(Transaction::isCompleted)     // stateless — lazy (Dia 5)
                .filter(tx -> tx.type() == type)       // stateless — lazy
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Retorna transações de um mês específico.
     *
     * takeWhile (Dia 5) não se aplica aqui pois a lista não é garantidamente
     * ordenada por data. filter varre tudo — trade-off aceitável para
     * lista em memória.
     */
    public List<Transaction> getTransactionsByMonth(YearMonth month) {
        return transactions.stream()
                .filter(tx -> tx.belongsTo(month))    // método do domínio Transaction
                .filter(Transaction::isCompleted)
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Calcula total de débitos ou créditos completados.
     *
     * Short-circuit via reduce com identity: se não há transações, retorna ZERO
     * sem lançar NoSuchElementException (diferente de findFirst().get()).
     */
    public BigDecimal getTotalByType(TransactionType type) {
        return transactions.stream()
                .filter(Transaction::isCompleted)
                .filter(tx -> tx.type() == type)
                .map(Transaction::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // reduce com identity nunca lança exceção — retorna ZERO se vazio
    }

    /**
     * Verifica se existe transação acima do limiar de risco.
     *
     * Short-Circuit (Dia 5): anyMatch para imediatamente ao encontrar
     * o primeiro match. Para 100.000 transações onde a 3ª já supera o
     * limiar → processa apenas 3 elementos.
     */
    public boolean hasHighRiskTransaction(BigDecimal threshold) {
        return transactions.stream()
                .filter(Transaction::isCompleted)
                .anyMatch(tx -> tx.isHighValue(threshold)); // short-circuit (Dia 5)
    }

    /**
     * Retorna a primeira transação suspeita — short-circuit (Dia 5).
     *
     * findFirst() + filter = processa apenas até encontrar o primeiro match.
     * Optional como retorno: explícito sobre a possibilidade de ausência.
     */
    public Optional<Transaction> findFirstSuspiciousTransaction(BigDecimal threshold) {
        return transactions.stream()
                .filter(Transaction::isCompleted)
                .filter(Transaction::isDebit)
                .filter(tx -> tx.isHighValue(threshold))
                .findFirst(); // short-circuit — para no primeiro (Dia 5)
    }

    /**
     * Breakdown de gastos por categoria.
     *
     * Collectors.groupingBy + downstream (Dia 6):
     *   Um único loop sobre a lista de transações produz o mapa completo.
     *   Sem groupingBy: você precisaria de um loop por categoria.
     */
    public Map<String, BigDecimal> getSpendingByCategory() {
        return transactions.stream()
                .filter(Transaction::isCompleted)
                .filter(Transaction::isDebit)
                .collect(Collectors.groupingBy(
                        Transaction::category,
                        Collectors.reducing(BigDecimal.ZERO, Transaction::amount, BigDecimal::add)
                        // reducing como downstream: soma os valores de cada grupo
                ));
    }

    /**
     * Retorna as N categorias com maior gasto.
     *
     * Composição de Streams (Dias 5 + 6):
     *   Stream 1: groupingBy para agrupar por categoria (um loop)
     *   Stream 2: sobre o entrySet do Map para ordenar e limitar
     *   limit() = short-circuit (Dia 5)
     */
    public List<Map.Entry<String, BigDecimal>> getTopSpendingCategories(int limit) {
        return getSpendingByCategory().entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(limit)           // short-circuit — não processa além do necessário
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Resumo financeiro do mês com dois resultados em um único loop.
     *
     * Collectors.teeing() (Dia 6 — Java 12+):
     *   Calcula total de débitos E total de créditos em um único loop.
     *   Sem teeing: dois streams separados = dois loops sobre os mesmos dados.
     *
     * @param month mês a analisar (ex: YearMonth.of(2026, 7))
     */
    public MonthlyAccountSummary getMonthlyReport(YearMonth month) {
        return transactions.stream()
                .filter(tx -> tx.belongsTo(month))   // lazy (Dia 5)
                .filter(Transaction::isCompleted)     // lazy (Dia 5)
                .collect(Collectors.teeing(           // dois resultados, um loop (Dia 6)

                        // Downstream 1: soma todos os débitos do mês
                        Collectors.filtering(
                                Transaction::isDebit,
                                Collectors.reducing(BigDecimal.ZERO, Transaction::amount, BigDecimal::add)
                        ),

                        // Downstream 2: soma todos os créditos do mês
                        Collectors.filtering(
                                Transaction::isCredit,
                                Collectors.reducing(BigDecimal.ZERO, Transaction::amount, BigDecimal::add)
                        ),

                        // Merger: combina os dois resultados num record
                        (totalDebits, totalCredits) -> new MonthlyAccountSummary(
                                month,
                                balance,
                                totalDebits,
                                totalCredits,
                                totalCredits.subtract(totalDebits), // net: créditos - débitos
                                getSpendingByCategory()
                        )
                ));
    }

    /**
     * Verifica se todas as transações do mês estão completadas.
     *
     * allMatch (Dia 5 — Short-Circuit):
     *   Para no PRIMEIRO elemento que não satisfaz a condição.
     *   Se há uma transação PENDING, para imediatamente → retorna false.
     */
    public boolean isMonthFullyProcessed(YearMonth month) {
        return transactions.stream()
                .filter(tx -> tx.belongsTo(month))
                .allMatch(Transaction::isCompleted); // short-circuit (Dia 5)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Getters — acesso controlado ao estado
    // WHY não expor a lista diretamente?
    //   Encapsulamento. Se o chamador tiver a lista, pode modificá-la
    //   sem passar pelas regras de negócio (debit/credit).
    // ─────────────────────────────────────────────────────────────────────────

    public UUID          getId()           { return id; }
    public String        getName()         { return name; }
    public AccountType   getType()         { return type; }
    public LocalDateTime getCreatedAt()    { return createdAt; }
    public LocalDateTime getUpdatedAt()    { return updatedAt; }
    public boolean       isActive()        { return active; }

    /** Retorna uma view imutável das transações — sem expor a lista interna. */
    public List<Transaction> getTransactions() {
        return Collections.unmodifiableList(transactions);
    }

    /**
     * Retorna o saldo atual.
     * WHY synchronized no getter de saldo?
     *   Garante visibilidade do valor mais recente entre threads (Dia 4).
     *   Sem synchronized, uma thread poderia ler valor antigo do cache de CPU.
     */
    public synchronized BigDecimal getBalance() { return balance; }

    public synchronized void rename(String newName) {
        this.name      = validateName(newName);
        this.updatedAt = LocalDateTime.now();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // equals / hashCode — identidade baseada em ID
    //
    // WHY apenas id?
    //   Effective Java Item 10: dois Accounts são o mesmo se têm o mesmo UUID.
    //   O saldo pode mudar, o nome pode mudar — a identidade não.
    //   Usar balance no equals causaria inconsistência em Sets e Maps após debit().
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Account other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
        // WHY apenas id? Mesmo motivo do equals. Se usarmos balance,
        // o hashCode mudaria após debit() → objeto perdido em HashSet.
    }

    @Override
    public String toString() {
        return "Account{id=%s, name='%s', type=%s, balance=%s, active=%b}"
                .formatted(id, name, type, balance.toPlainString(), active);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Validações privadas — falha rápida com mensagem clara
    // ─────────────────────────────────────────────────────────────────────────

    private static String validateName(String name) {
        Objects.requireNonNull(name, "Account name cannot be null");
        if (name.isBlank()) throw new IllegalArgumentException("Account name cannot be blank");
        if (name.length() > 100) throw new IllegalArgumentException("Account name too long (max 100 chars)");
        return name.trim();
    }

    private static BigDecimal validateBalance(BigDecimal balance) {
        Objects.requireNonNull(balance, "Balance cannot be null");
        if (balance.compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("Initial balance cannot be negative: " + balance);
        return balance.setScale(2, RoundingMode.HALF_UP);
    }

    private void validateActive() {
        if (!active) throw new IllegalStateException(
                "Account '%s' is not active. Debit/credit operations are not allowed.".formatted(name)
        );
    }

    private static void validateAmount(BigDecimal amount) {
        Objects.requireNonNull(amount, "Amount cannot be null");
        if (amount.compareTo(BigDecimal.ZERO) <= 0)
            throw new InvalidAmountException("Amount must be positive, got: " + amount);
    }

}
