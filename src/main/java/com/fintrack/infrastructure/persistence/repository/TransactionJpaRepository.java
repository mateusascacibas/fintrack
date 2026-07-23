package com.fintrack.infrastructure.persistence.repository;

import com.fintrack.infrastructure.persistence.entity.TransactionJpaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface TransactionJpaRepository extends JpaRepository<TransactionJpaEntity, UUID> {

    // Todas as transações de uma conta, ordenadas por data decrescente
    List<TransactionJpaEntity> findByAccountIdOrderByOccurredAtDesc(UUID accountId);

    // Filtro por categoria
    List<TransactionJpaEntity> findByAccountIdAndCategoryOrderByOccurredAtDesc(
            UUID accountId, String category
    );

    // Paginação — para findRecentByAccountId
    // CORRIGIDO: era Optional<Object> — deve ser List<TransactionJpaEntity>
    List<TransactionJpaEntity> findByAccountId(UUID accountId, Pageable pageable);

    // Range de datas — para findByAccountIdAndMonth
    // NOVO: não existia, necessário para o TransactionRepositoryAdapter
    List<TransactionJpaEntity> findByAccountIdAndOccurredAtBetween(
            UUID accountId,
            LocalDateTime start,
            LocalDateTime end
    );
}