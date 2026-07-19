package com.fintrack.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Account — Entidade de domínio.
 *
 * DECISÃO ARQUITETURAL: Por que esta classe não tem anotações JPA (@Entity, @Table)?
 *
 * Na arquitetura hexagonal, o DOMÍNIO é o núcleo da aplicação e não deve
 * depender de nenhum framework externo. JPA é um detalhe de infraestrutura.
 *
 * Se amanhã você trocar PostgreSQL por MongoDB, esta classe não muda.
 * A camada de infraestrutura (AccountJpaEntity) é que carrega as anotações JPA
 * e faz o mapeamento de/para esta entidade de domínio.
 *
 * DECISÃO: Por que não usar Lombok (@Data) aqui?
 * Entidades de domínio devem ter comportamento, não só dados.
 * @Data gera getters/setters para tudo, incentivando anemia de domínio.
 * Preferimos métodos com intenção clara (debit, credit, isActive).
 */
public class Account {

    private final UUID id;
    private String name;
    private BigDecimal balance;
    private final AccountType type;
    private boolean active;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Constructor privado — use o factory method create() para consistência
    private Account(UUID id, String name, BigDecimal balance, AccountType type,
                    boolean active, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.name = name;
        this.balance = balance;
        this.type = type;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Factory method para criar uma nova conta.
     *
     * WHY factory method instead of public constructor?
     * 1. Nome expressivo: Account.create() vs new Account(...)
     * 2. Centraliza regras de criação (UUID gerado aqui, não pelo chamador)
     * 3. Permite validações antes de construir o objeto
     */
    public static Account create(String name, AccountType type, BigDecimal initialBalance) {
        if (initialBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new com.fintrack.domain.exception.DomainException(
                "Initial balance cannot be negative"
            );
        }
        LocalDateTime now = LocalDateTime.now();
        return new Account(UUID.randomUUID(), name, initialBalance, type, true, now, now);
    }

    /**
     * Reconstitui uma Account a partir de dados persistidos.
     * Usado pelos adapters de persistência ao carregar do banco.
     */
    public static Account reconstitute(UUID id, String name, BigDecimal balance,
                                       AccountType type, boolean active,
                                       LocalDateTime createdAt, LocalDateTime updatedAt) {
        return new Account(id, name, balance, type, active, createdAt, updatedAt);
    }

    // ──────────────────────────────────────────────
    // BEHAVIOR — Regras de negócio vivem aqui, no domínio.
    // ──────────────────────────────────────────────

    public void debit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new com.fintrack.domain.exception.DomainException("Debit amount must be positive");
        }
        if (this.balance.compareTo(amount) < 0) {
            throw new com.fintrack.domain.exception.InsufficientBalanceException(
                "Insufficient balance. Current: " + this.balance + ", Required: " + amount
            );
        }
        this.balance = this.balance.subtract(amount);
        this.updatedAt = LocalDateTime.now();
    }

    public void credit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new com.fintrack.domain.exception.DomainException("Credit amount must be positive");
        }
        this.balance = this.balance.add(amount);
        this.updatedAt = LocalDateTime.now();
    }

    public void rename(String newName) {
        if (newName == null || newName.isBlank()) {
            throw new com.fintrack.domain.exception.DomainException("Account name cannot be blank");
        }
        this.name = newName;
        this.updatedAt = LocalDateTime.now();
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = LocalDateTime.now();
    }

    // ──────────────────────────────────────────────
    // GETTERS — Sem setters públicos: estado muda via métodos de negócio
    // ──────────────────────────────────────────────

    public UUID getId() { return id; }
    public String getName() { return name; }
    public BigDecimal getBalance() { return balance; }
    public AccountType getType() { return type; }
    public boolean isActive() { return active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
