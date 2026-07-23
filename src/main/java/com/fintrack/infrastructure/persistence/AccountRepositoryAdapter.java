package com.fintrack.infrastructure.persistence;

import com.fintrack.domain.model.Account;
import com.fintrack.domain.model.Transaction;
import com.fintrack.domain.port.out.AccountRepository;
import com.fintrack.infrastructure.persistence.entity.AccountJpaEntity;
import com.fintrack.infrastructure.persistence.entity.TransactionJpaEntity;
import com.fintrack.infrastructure.persistence.repository.AccountJpaRepository;
import com.fintrack.infrastructure.persistence.repository.TransactionJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class AccountRepositoryAdapter implements AccountRepository {

    private final AccountJpaRepository     accountJpaRepository;
    private final TransactionJpaRepository transactionJpaRepository;

    public AccountRepositoryAdapter(AccountJpaRepository accountJpaRepository,
                                    TransactionJpaRepository transactionJpaRepository) {
        this.accountJpaRepository     = accountJpaRepository;
        this.transactionJpaRepository = transactionJpaRepository;
    }

    @Override
    public Account save(Account account) {
        AccountJpaEntity saved = accountJpaRepository.save(AccountJpaEntity.fromDomain(account));
        return saved.toDomain(loadTransactions(saved.getId()));
    }

    @Override
    public Optional<Account> findById(UUID id) {
        return accountJpaRepository.findById(id)
                .map(entity -> entity.toDomain(loadTransactions(id)));
    }

    @Override
    public List<Account> findAllActive() {
        return accountJpaRepository.findByActiveTrue().stream()
                .map(entity -> entity.toDomain(loadTransactions(entity.getId())))
                .toList();
    }

    @Override
    public boolean existsByName(String name) {
        return accountJpaRepository.existsByName(name);
    }

    private List<Transaction> loadTransactions(UUID accountId) {
        return transactionJpaRepository
                .findByAccountIdOrderByOccurredAtDesc(accountId)
                .stream()
                .map(TransactionJpaEntity::toDomain)
                .toList();
    }
}