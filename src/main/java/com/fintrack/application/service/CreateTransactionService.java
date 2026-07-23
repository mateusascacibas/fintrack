package com.fintrack.application.service;

import com.fintrack.domain.event.TransactionCreatedEvent;
import com.fintrack.domain.exception.AccountNotFoundException;
import com.fintrack.domain.exception.DomainException;
import com.fintrack.domain.model.Account;
import com.fintrack.domain.model.Transaction;
import com.fintrack.domain.model.TransactionType;
import com.fintrack.domain.port.in.CreateTransactionUseCase;
import com.fintrack.domain.port.out.AccountRepository;
import com.fintrack.domain.port.out.EventPublisher;
import com.fintrack.domain.port.out.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * CreateTransactionService — Orquestra a criação de uma transação financeira.
 *
 * MUDANÇAS em relação à versão anterior (compatibilidade com novo domínio):
 *
 *   1. account.debit/credit agora retornam a Transaction criada internamente.
 *      O Account é o Aggregate Root — ele cria suas próprias transações.
 *      Removemos a criação manual de Transaction antes do debit/credit.
 *
 *   2. Transaction agora é um Java Record — imutável.
 *      transaction.complete() e transaction.fail() retornam NOVA instância.
 *      Era necessário capturar o retorno — o código anterior ignorava isso.
 *
 *   3. Acessores do Record: .id() em vez de .getId(), .amount() em vez de .getAmount(), etc.
 *
 *   4. O bloco catch não tenta mais criar um registro de "transaction failed".
 *      Com o domínio imutável, a transação só existe após debit/credit bem-sucedido.
 *      Falhas de regra de negócio são registradas apenas em log — não no banco.
 */
@Service
public class CreateTransactionService implements CreateTransactionUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateTransactionService.class);
    private static final String TRANSACTION_EVENTS_TOPIC = "transaction.created";

    private final AccountRepository     accountRepository;
    private final TransactionRepository transactionRepository;
    private final EventPublisher        eventPublisher;

    public CreateTransactionService(AccountRepository accountRepository,
                                    TransactionRepository transactionRepository,
                                    EventPublisher eventPublisher) {
        this.accountRepository     = accountRepository;
        this.transactionRepository = transactionRepository;
        this.eventPublisher        = eventPublisher;
    }

    @Override
    @Transactional
    public Transaction execute(UUID accountId, BigDecimal amount, TransactionType type,
                               String description, String category) {

        log.info("Processing transaction: accountId={}, amount={}, type={}", accountId, amount, type);

        // 1. Busca conta — lança AccountNotFoundException se não existir
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        try {
            // 2. Aplica o efeito financeiro na conta E recebe a Transaction criada.
            //
            // MUDANÇA: antes criávamos Transaction manualmente e depois chamávamos
            // account.debit(amount). Agora account.debit/credit criam a Transaction
            // internamente e a retornam — o Account é responsável por seus eventos.
            //
            // account.debit() agora exige (amount, description, category)
            // account.credit() agora exige (amount, description, category)
            // Ambos lançam DomainException se a regra de negócio for violada.
            Transaction transaction = (type == TransactionType.DEBIT)
                    ? account.debit(amount, description, category)   // retorna Transaction COMPLETED
                    : account.credit(amount, description, category);  // retorna Transaction COMPLETED

            // 3. Persiste conta atualizada e transação na mesma transação JPA.
            //    Se qualquer persist falhar → rollback automático via @Transactional.
            accountRepository.save(account);
            Transaction saved = transactionRepository.save(transaction);

            // 4. Publica evento APÓS commit bem-sucedido (fora do rollback de banco).
            publishEvent(saved);

            log.info("Transaction completed: transactionId={}", saved.id()); // Record: .id() não .getId()
            return saved;

        } catch (DomainException e) {
            // A transação de banco ainda não commitou — rollback automático.
            // Com o domínio imutável, não há Transaction para marcar como FAILED:
            // account.debit() lançou exceção antes de criar qualquer Transaction.
            // Registramos o evento em log apenas.
            log.warn("Transaction rejected by domain rule: accountId={}, reason={}",
                    accountId, e.getMessage());
            throw e; // Re-lança → GlobalExceptionHandler mapeia para HTTP 422
        }
    }

    private void publishEvent(Transaction transaction) {
        try {
            // MUDANÇA: Record usa acessores sem "get":
            //   transaction.id()         em vez de transaction.getId()
            //   transaction.accountId()  em vez de transaction.getAccountId()
            //   transaction.amount()     em vez de transaction.getAmount()
            //   transaction.type()       em vez de transaction.getType()
            //   transaction.category()   em vez de transaction.getCategory()
            //   transaction.occurredAt() em vez de transaction.getOccurredAt()
            var event = new TransactionCreatedEvent(
                    transaction.id(),
                    transaction.accountId(),
                    transaction.amount(),
                    transaction.type().name(),
                    transaction.category(),
                    transaction.occurredAt()
            );
            eventPublisher.publish(TRANSACTION_EVENTS_TOPIC, event);

        } catch (Exception e) {
            // A transação financeira já foi persistida com sucesso.
            // Não falhar o request por problema de mensageria.
            // Padrão correto para produção: Transactional Outbox (Mês 7).
            log.error("Failed to publish TransactionCreatedEvent for transactionId={}: {}",
                    transaction.id(), e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Transaction> findByAccount(UUID accountId) {
        return transactionRepository.findByAccountId(accountId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Transaction> findByAccountAndCategory(UUID accountId, String category) {
        return transactionRepository.findByAccountIdAndCategory(accountId, category);
    }
}