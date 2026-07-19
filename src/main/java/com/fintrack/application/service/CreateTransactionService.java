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
 * Este serviço demonstra a interação entre domínio e múltiplas portas de saída:
 * AccountRepository, TransactionRepository e EventPublisher.
 *
 * FLUXO:
 * 1. Busca a conta (lança AccountNotFoundException se não existe)
 * 2. Aplica débito/crédito no domínio (regra de negócio no domain model)
 * 3. Cria a transação no domínio
 * 4. Persiste a conta atualizada
 * 5. Persiste a transação
 * 6. Publica evento (NoOp agora, Kafka no Mês 7)
 *
 * NOTA SOBRE @Transactional:
 * Os passos 4 e 5 estão dentro da mesma transação de banco.
 * Se o passo 5 falhar, o passo 4 é revertido (rollback).
 * O evento (passo 6) é publicado FORA da transação intencionalmente —
 * eventos não devem participar do rollback do banco.
 * Padrão avançado: Transactional Outbox (estudar no Mês 7).
 */
@Service
public class CreateTransactionService implements CreateTransactionUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateTransactionService.class);

    private static final String TRANSACTION_EVENTS_TOPIC = "transaction.created";

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final EventPublisher eventPublisher;

    public CreateTransactionService(AccountRepository accountRepository,
                                    TransactionRepository transactionRepository,
                                    EventPublisher eventPublisher) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public Transaction execute(UUID accountId, BigDecimal amount, TransactionType type,
                               String description, String category) {

        log.info("Processing transaction: accountId={}, amount={}, type={}", accountId, amount, type);

        // 1. Busca conta — lança AccountNotFoundException se não existir
        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new AccountNotFoundException(accountId));

        // 2. Cria transação no domínio (validações de negócio acontecem aqui)
        Transaction transaction = Transaction.create(accountId, amount, type, description, category);

        try {
            // 3. Aplica o efeito financeiro na conta (regras de negócio no domínio)
            if (type == TransactionType.DEBIT) {
                account.debit(amount);  // Lança InsufficientBalanceException se saldo insuficiente
            } else {
                account.credit(amount);
            }

            // 4. Marca a transação como concluída
            transaction.complete();

            // 5. Persiste (ambos na mesma transação JPA)
            accountRepository.save(account);
            Transaction saved = transactionRepository.save(transaction);

            // 6. Publica evento APÓS commit bem-sucedido
            publishEvent(saved);

            log.info("Transaction completed: transactionId={}", saved.getId());
            return saved;

        } catch (DomainException e) {
            // A transação de banco ainda não commitou — o rollback é automático
            // pois a exceção propaga para fora do @Transactional
            log.warn("Transaction failed due to domain rule: {}", e.getMessage());
            transaction.fail(e.getMessage());
            throw e; // Re-lança para o controller tratar e retornar 422
        }
    }

    private void publishEvent(Transaction transaction) {
        try {
            var event = new TransactionCreatedEvent(
                transaction.getId(),
                transaction.getAccountId(),
                transaction.getAmount(),
                transaction.getType().name(),
                transaction.getCategory(),
                transaction.getOccurredAt()
            );
            eventPublisher.publish(TRANSACTION_EVENTS_TOPIC, event);
        } catch (Exception e) {
            // WHY swallow this exception?
            // A transação financeira já foi persistida com sucesso.
            // Falhar o request HTTP por causa de um problema de mensageria
            // seria errado do ponto de vista de UX.
            // Padrão correto para produção: Transactional Outbox Pattern (Mês 7).
            log.error("Failed to publish TransactionCreatedEvent for transactionId={}: {}",
                transaction.getId(), e.getMessage());
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
