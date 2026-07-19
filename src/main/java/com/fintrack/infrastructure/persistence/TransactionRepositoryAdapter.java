package com.fintrack.infrastructure.persistence;

import com.fintrack.domain.model.Transaction;
import com.fintrack.domain.port.out.TransactionRepository;
import com.fintrack.infrastructure.persistence.entity.TransactionJpaEntity;
import com.fintrack.infrastructure.persistence.repository.TransactionJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class TransactionRepositoryAdapter implements TransactionRepository {

    private final TransactionJpaRepository jpaRepository;

    public TransactionRepositoryAdapter(TransactionJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Transaction save(Transaction transaction) {
        TransactionJpaEntity entity = TransactionJpaEntity.fromDomain(transaction);
        return jpaRepository.save(entity).toDomain();
    }

    @Override
    public List<Transaction> findByAccountId(UUID accountId) {
        return jpaRepository.findByAccountIdOrderByOccurredAtDesc(accountId)
            .stream()
            .map(TransactionJpaEntity::toDomain)
            .toList();
    }

    @Override
    public List<Transaction> findByAccountIdAndCategory(UUID accountId, String category) {
        return jpaRepository.findByAccountIdAndCategoryOrderByOccurredAtDesc(accountId, category)
            .stream()
            .map(TransactionJpaEntity::toDomain)
            .toList();
    }
}
