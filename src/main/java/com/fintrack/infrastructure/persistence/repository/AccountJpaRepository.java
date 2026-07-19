package com.fintrack.infrastructure.persistence.repository;

import com.fintrack.infrastructure.persistence.entity.AccountJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

/**
 * AccountJpaRepository — Spring Data JPA interface.
 *
 * Esta é uma interface do Spring Data, não é o AccountRepository do domínio.
 * O AccountRepositoryAdapter usa esta interface para implementar o contrato do domínio.
 *
 * Naming convention do Spring Data:
 * findBy[Campo][Condição] → Spring gera a query automaticamente.
 * Para queries complexas, use @Query com JPQL ou native SQL.
 */
public interface AccountJpaRepository extends JpaRepository<AccountJpaEntity, UUID> {

    List<AccountJpaEntity> findByActiveTrue();

    boolean existsByName(String name);

    // WHY @Query here?
    // Para queries que o naming convention não consegue expressar claramente,
    // ou quando você precisa de otimizações específicas (FETCH JOIN, etc.).
    @Query("SELECT a FROM AccountJpaEntity a WHERE a.active = true ORDER BY a.createdAt DESC")
    List<AccountJpaEntity> findAllActiveOrderByCreatedAtDesc();
}
