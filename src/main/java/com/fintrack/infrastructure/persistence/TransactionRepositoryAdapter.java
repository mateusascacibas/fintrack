package com.fintrack.infrastructure.persistence;

import com.fintrack.domain.model.Transaction;
import com.fintrack.domain.port.out.TransactionRepository;
import com.fintrack.infrastructure.persistence.entity.TransactionJpaEntity;
import com.fintrack.infrastructure.persistence.repository.TransactionJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Component
public class TransactionRepositoryAdapter implements TransactionRepository {

    private final TransactionJpaRepository transactionJpaRepository;

    public TransactionRepositoryAdapter(TransactionJpaRepository transactionJpaRepository) {
        this.transactionJpaRepository = transactionJpaRepository;
    }

    @Override
    public Transaction save(Transaction transaction) {
        return transactionJpaRepository
                .save(TransactionJpaEntity.fromDomain(transaction))
                .toDomain();
    }

    @Override
    public List<Transaction> findByAccountId(UUID accountId) {
        return transactionJpaRepository
                .findByAccountIdOrderByOccurredAtDesc(accountId)
                .stream()
                .map(TransactionJpaEntity::toDomain)
                .toList();
    }

    @Override
    public List<Transaction> findByAccountIdAndCategory(UUID accountId, String category) {
        return transactionJpaRepository
                .findByAccountIdAndCategoryOrderByOccurredAtDesc(accountId, category)
                .stream()
                .map(TransactionJpaEntity::toDomain)
                .toList();
    }

    @Override
    public List<Transaction> findRecentByAccountId(UUID accountId, int limit) {
        return transactionJpaRepository
                .findByAccountId(
                        accountId,
                        PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "occurredAt"))
                )
                .stream()
                .map(TransactionJpaEntity::toDomain)
                .toList();
    }

    @Override
    public List<Transaction> findByAccountIdAndMonth(UUID accountId, YearMonth month) {
        LocalDateTime start = month.atDay(1).atStartOfDay();
        LocalDateTime end   = month.atEndOfMonth().atTime(23, 59, 59);
        return transactionJpaRepository
                .findByAccountIdAndOccurredAtBetween(accountId, start, end)
                .stream()
                .map(TransactionJpaEntity::toDomain)
                .toList();
    }
}