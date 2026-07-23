package com.fintrack.infrastructure.persistence.entity;

import com.fintrack.domain.model.Account;
import com.fintrack.domain.model.AccountType;
import com.fintrack.domain.model.Transaction;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Entidade JPA para persistência de Account.
 *
 * MUDANÇA em toDomain():
 *   Account.reconstitute() foi corrigido na nova versão de Account.java
 *   para usar um segundo constructor privado que aceita createdAt explícito.
 *   A assinatura do reconstitute() permanece a mesma — apenas a implementação
 *   interna mudou para corrigir o bug de createdAt.
 */
@Entity
@Table(name = "accounts")
public class AccountJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private AccountType type;

    @Column(name = "balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected AccountJpaEntity() {}

    // ─────────────────────────────────────────────────────────────────────────
    // toDomain() — JPA Entity → Domain Model
    //
    // Account.reconstitute() foi corrigido para aceitar createdAt explícito.
    // Transações são carregadas separadamente pelo adapter e passadas aqui.
    // ─────────────────────────────────────────────────────────────────────────
    public Account toDomain(List<Transaction> transactions) {
        return Account.reconstitute(
                this.id,
                this.name,
                this.type,
                this.balance,
                this.active,
                this.createdAt,   // agora preservado corretamente
                this.updatedAt,
                transactions
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // fromDomain() — Domain Model → JPA Entity
    //
    // Account usa getters convencionais (não é Record): .getId(), .getName()...
    // ─────────────────────────────────────────────────────────────────────────
    public static AccountJpaEntity fromDomain(Account account) {
        AccountJpaEntity entity = new AccountJpaEntity();
        entity.id        = account.getId();
        entity.name      = account.getName();
        entity.type      = account.getType();
        entity.balance   = account.getBalance();
        entity.active    = account.isActive();
        entity.createdAt = account.getCreatedAt();
        entity.updatedAt = account.getUpdatedAt();
        return entity;
    }

    public UUID          getId()        { return id; }
    public String        getName()      { return name; }
    public AccountType   getType()      { return type; }
    public BigDecimal    getBalance()   { return balance; }
    public boolean       isActive()     { return active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}