package com.fintrack.infrastructure.persistence.entity;

import com.fintrack.domain.model.Transaction;
import com.fintrack.domain.model.TransactionStatus;
import com.fintrack.domain.model.TransactionType;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entidade JPA para persistência de Transaction.
 *
 * WHY separado do domain model?
 *   Transaction (domínio) é um Record imutável — sem @Entity, sem @Column.
 *   TransactionJpaEntity (infraestrutura) carrega as anotações JPA.
 *   A conversão entre os dois acontece nos métodos toDomain() e fromDomain().
 *
 * MUDANÇA: toDomain() foi corrigido para o Record.
 *   Record não tem construtor vazio nem setters — a construção usa o
 *   construtor canônico com todos os campos de uma vez.
 */
@Entity
@Table(
        name = "transactions",
        indexes = {
                @Index(name = "idx_transactions_account_id", columnList = "account_id"),
                @Index(name = "idx_transactions_occurred_at", columnList = "occurred_at")
        }
)
public class TransactionJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private TransactionStatus status;

    @Column(name = "description", nullable = false, length = 255)
    private String description;

    @Column(name = "category", nullable = false, length = 100)
    private String category;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt; // null para PENDING

    protected TransactionJpaEntity() {
        // JPA exige constructor vazio — domain model não precisa
    }

    // ─────────────────────────────────────────────────────────────────────────
    // toDomain() — JPA Entity → Domain Record
    //
    // MUDANÇA: Transaction agora é um Record.
    //   Antes: new Transaction() + setters
    //   Agora: construtor canônico com todos os campos
    //
    // WHY não usar os factory methods (createDebit, createCredit)?
    //   Os factory methods geram novo UUID e timestamps.
    //   Aqui estamos RECONSTITUINDO a partir do banco — precisamos dos
    //   valores originais salvos, não de novos valores gerados.
    // ─────────────────────────────────────────────────────────────────────────
    public Transaction toDomain() {
        return new Transaction(
                this.id,
                this.accountId,
                this.amount,
                this.type,
                this.status,
                this.description,
                this.category,
                this.occurredAt,
                this.processedAt
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // fromDomain() — Domain Record → JPA Entity
    //
    // Record usa acessores sem "get": .id(), .amount(), .type()...
    // ─────────────────────────────────────────────────────────────────────────
    public static TransactionJpaEntity fromDomain(Transaction transaction) {
        TransactionJpaEntity entity = new TransactionJpaEntity();
        entity.id          = transaction.id();           // Record: .id() não .getId()
        entity.accountId   = transaction.accountId();
        entity.amount      = transaction.amount();
        entity.type        = transaction.type();
        entity.status      = transaction.status();
        entity.description = transaction.description();
        entity.category    = transaction.category();
        entity.occurredAt  = transaction.occurredAt();
        entity.processedAt = transaction.processedAt();
        return entity;
    }

    // Getters para o JPA (sem setters — imutável após fromDomain)
    public UUID            getId()          { return id; }
    public UUID            getAccountId()   { return accountId; }
    public BigDecimal      getAmount()      { return amount; }
    public TransactionType getType()        { return type; }
    public TransactionStatus getStatus()   { return status; }
    public String          getDescription() { return description; }
    public String          getCategory()    { return category; }
    public LocalDateTime   getOccurredAt()  { return occurredAt; }
    public LocalDateTime   getProcessedAt() { return processedAt; }
}