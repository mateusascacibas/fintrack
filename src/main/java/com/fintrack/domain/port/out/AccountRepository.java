package com.fintrack.domain.port.out;

import com.fintrack.domain.model.Account;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * AccountRepository — Porta de SAÍDA do domínio.
 *
 * CONCEITO: Dependency Inversion Principle (o 'D' do SOLID)
 *
 * O domínio DEFINE esta interface. A infraestrutura IMPLEMENTA.
 * Isso inverte a dependência: infraestrutura depende do domínio, não o contrário.
 *
 * Repare que esta interface retorna objetos de domínio (Account), não JPA entities.
 * A conversão JpaEntity ↔ Domain Object é responsabilidade do adapter de persistência.
 *
 * WHY Optional<Account> instead of Account (nullable)?
 * Optional força o chamador a lidar explicitamente com o caso "não encontrado".
 * Reduz NullPointerExceptions e torna a API mais expressiva.
 */
public interface AccountRepository {

    Account save(Account account);

    Optional<Account> findById(UUID id);

    List<Account> findAllActive();

    boolean existsByName(String name);
}
