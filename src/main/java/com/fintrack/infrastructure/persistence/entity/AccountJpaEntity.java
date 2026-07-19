package com.fintrack.infrastructure.persistence.entity;

import com.fintrack.domain.model.Account;
import com.fintrack.domain.model.AccountType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * AccountJpaEntity — Entidade JPA. Vive na camada de INFRAESTRUTURA.
 *
 * POR QUE separar AccountJpaEntity de Account (domínio)?
 *
 * 1. EVOLUÇÃO INDEPENDENTE: O schema do banco pode evoluir diferente do modelo de domínio.
 *    Ex: o banco tem uma coluna `legacy_code` que o domínio não precisa saber.
 *
 * 2. SEM CONTAMINAÇÃO: O domínio não carrega anotações JPA, Hibernate, etc.
 *    Se trocar JPA por JDBC puro amanhã, o domínio não muda uma linha.
 *
 * 3. MAPEAMENTO EXPLÍCITO: A conversão entity ↔ domain está no Adapter (explícita),
 *    não espalhada pelos dois modelos.
 *
 * CUSTO: Mais código (duas classes por entidade + mapeamento).
 * BENEFÍCIO: Separação de responsabilidades real, não apenas no discurso.
 *
 * WHY @Getter/@Setter com Lombok aqui (mas não no domínio)?
 * Entidades JPA precisam de getters/setters para o Hibernate funcionar.
 * Como é só infraestrutura (sem lógica de negócio), Lombok é aceitável.
 */
@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
public class AccountJpaEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "balance", nullable = false, precision = 19, scale = 4)
    // WHY precision 19, scale 4?
    // BigDecimal para dinheiro. Scale 4 (4 casas decimais) é padrão financeiro.
    // Nunca use double/float para valores monetários — problemas de precisão de ponto flutuante.
    private BigDecimal balance;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    // WHY EnumType.STRING and not ORDINAL?
    // ORDINAL salva 0, 1, 2... Se você reordenar o enum, todos os dados corrompem.
    // STRING salva "CHECKING", "SAVINGS"... Robusto a reordenações.
    private AccountType type;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ──────────────────────────────────────────────
    // MAPEAMENTO: JpaEntity ↔ Domain Model
    // Poderia estar no Adapter, mas centralizar no entity é pragmático.
    // ──────────────────────────────────────────────

    public static AccountJpaEntity fromDomain(Account account) {
        AccountJpaEntity entity = new AccountJpaEntity();
        entity.id = account.getId();
        entity.name = account.getName();
        entity.balance = account.getBalance();
        entity.type = account.getType();
        entity.active = account.isActive();
        entity.createdAt = account.getCreatedAt();
        entity.updatedAt = account.getUpdatedAt();
        return entity;
    }

    public Account toDomain() {
        return Account.reconstitute(id, name, balance, type, active, createdAt, updatedAt);
    }
}
