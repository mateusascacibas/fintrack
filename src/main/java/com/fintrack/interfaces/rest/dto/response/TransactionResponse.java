package com.fintrack.interfaces.rest.dto.response;

import com.fintrack.domain.model.Transaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TransactionResponse(
    UUID id,
    UUID accountId,
    BigDecimal amount,
    String type,
    String status,
    String description,
    String category,
    LocalDateTime occurredAt
) {
    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
            transaction.id(),
            transaction.accountId(),
            transaction.amount(),
            transaction.type().name(),
            transaction.status().name(),
            transaction.description(),
            transaction.category(),
            transaction.occurredAt()
        );
    }
}
