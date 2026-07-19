package com.fintrack.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Transaction — Entidade de domínio.
 *
 * Representa uma movimentação financeira. Uma transação é IMUTÁVEL após criada —
 * não existe "editar transação". Se houve erro, cria-se uma transação de estorno.
 * Este é um invariante de negócio importante no domínio financeiro.
 */
public class Transaction {

    private final UUID id;
    private final UUID accountId;
    private final BigDecimal amount;
    private final TransactionType type;
    private TransactionStatus status;
    private final String description;
    private final String category;
    private final LocalDateTime occurredAt;
    private LocalDateTime updatedAt;

    private Transaction(UUID id, UUID accountId, BigDecimal amount, TransactionType type,
                        TransactionStatus status, String description, String category,
                        LocalDateTime occurredAt, LocalDateTime updatedAt) {
        this.id = id;
        this.accountId = accountId;
        this.amount = amount;
        this.type = type;
        this.status = status;
        this.description = description;
        this.category = category;
        this.occurredAt = occurredAt;
        this.updatedAt = updatedAt;
    }

    public static Transaction create(UUID accountId, BigDecimal amount, TransactionType type,
                                     String description, String category) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new com.fintrack.domain.exception.DomainException("Transaction amount must be positive");
        }
        LocalDateTime now = LocalDateTime.now();
        return new Transaction(
            UUID.randomUUID(), accountId, amount, type,
            TransactionStatus.PENDING, description, category, now, now
        );
    }

    public static Transaction reconstitute(UUID id, UUID accountId, BigDecimal amount,
                                           TransactionType type, TransactionStatus status,
                                           String description, String category,
                                           LocalDateTime occurredAt, LocalDateTime updatedAt) {
        return new Transaction(id, accountId, amount, type, status, description, category, occurredAt, updatedAt);
    }

    // ──────────────────────────────────────────────
    // BEHAVIOR
    // ──────────────────────────────────────────────

    public void complete() {
        if (this.status != TransactionStatus.PENDING) {
            throw new com.fintrack.domain.exception.DomainException(
                "Cannot complete transaction with status: " + this.status
            );
        }
        this.status = TransactionStatus.COMPLETED;
        this.updatedAt = LocalDateTime.now();
    }

    public void fail(String reason) {
        this.status = TransactionStatus.FAILED;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isDebit() {
        return this.type == TransactionType.DEBIT;
    }

    public boolean isCredit() {
        return this.type == TransactionType.CREDIT;
    }

    // ──────────────────────────────────────────────
    // GETTERS
    // ──────────────────────────────────────────────

    public UUID getId() { return id; }
    public UUID getAccountId() { return accountId; }
    public BigDecimal getAmount() { return amount; }
    public TransactionType getType() { return type; }
    public TransactionStatus getStatus() { return status; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
