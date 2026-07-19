package com.fintrack.infrastructure.persistence.repository;

import com.fintrack.infrastructure.persistence.entity.TransactionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TransactionJpaRepository extends JpaRepository<TransactionJpaEntity, UUID> {

    List<TransactionJpaEntity> findByAccountIdOrderByOccurredAtDesc(UUID accountId);

    List<TransactionJpaEntity> findByAccountIdAndCategoryOrderByOccurredAtDesc(
        UUID accountId, String category
    );
}
