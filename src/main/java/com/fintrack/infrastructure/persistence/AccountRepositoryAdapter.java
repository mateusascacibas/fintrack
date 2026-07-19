package com.fintrack.infrastructure.persistence;

import com.fintrack.domain.model.Account;
import com.fintrack.domain.port.out.AccountRepository;
import com.fintrack.infrastructure.persistence.entity.AccountJpaEntity;
import com.fintrack.infrastructure.persistence.repository.AccountJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * AccountRepositoryAdapter — Adapter de saída (infraestrutura → domínio).
 *
 * PAPEL: Implementa AccountRepository (porta do domínio) usando Spring Data JPA.
 *
 * FLUXO:
 * AccountService → AccountRepository (interface do domínio)
 *                      ↑ implementado por
 *                 AccountRepositoryAdapter → AccountJpaRepository (Spring Data)
 *                                                ↓
 *                                          PostgreSQL
 *
 * @Component (not @Repository): @Repository é semântico para Spring Data interfaces.
 * Para adapters que implementam interfaces do domínio, @Component é mais preciso.
 */
@Component
public class AccountRepositoryAdapter implements AccountRepository {

    private final AccountJpaRepository jpaRepository;

    public AccountRepositoryAdapter(AccountJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Account save(Account account) {
        AccountJpaEntity entity = AccountJpaEntity.fromDomain(account);
        AccountJpaEntity saved = jpaRepository.save(entity);
        return saved.toDomain();
    }

    @Override
    public Optional<Account> findById(UUID id) {
        return jpaRepository.findById(id)
            .map(AccountJpaEntity::toDomain);
    }

    @Override
    public List<Account> findAllActive() {
        return jpaRepository.findByActiveTrue()
            .stream()
            .map(AccountJpaEntity::toDomain)
            .toList(); // Java 16+ — mais conciso que collect(Collectors.toList())
    }

    @Override
    public boolean existsByName(String name) {
        return jpaRepository.existsByName(name);
    }
}
