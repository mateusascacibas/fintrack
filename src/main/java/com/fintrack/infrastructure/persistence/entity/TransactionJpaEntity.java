package com.fintrack.infrastructure.persistence.entity;

import com.fintrack.domain.model.Transaction;
import com.fintrack.domain.model.TransactionStatus;
import com.fintrack.domain.model.TransactionType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "transactions",
    indexes = {
        // WHY declare indexes here?
        // Hibernate não cria índices automaticamente.
        // Declarar aqui serve como documentação de intenção.
        // Os índices REAIS são criados pelas migrations Flyway (V2).
        @Index(name = "idx_transactions_account_id", columnList = "account_id"),
        @Index(name = "idx_transactions_account_category", columnList = "account_id, category")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class TransactionJpaEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private TransactionStatus status;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static TransactionJpaEntity fromDomain(Transaction transaction) {
        TransactionJpaEntity entity = new TransactionJpaEntity();
        entity.id = transaction.getId();
        entity.accountId = transaction.getAccountId();
        entity.amount = transaction.getAmount();
        entity.type = transaction.getType();
        entity.status = transaction.getStatus();
        entity.description = transaction.getDescription();
        entity.category = transaction.getCategory();
        entity.occurredAt = transaction.getOccurredAt();
        entity.updatedAt = transaction.getUpdatedAt();
        return entity;
    }

    public Transaction toDomain() {
        return Transaction.reconstitute(id, accountId, amount, type, status,
            description, category, occurredAt, updatedAt);
    }
}
