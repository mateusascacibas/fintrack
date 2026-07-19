package com.fintrack.domain.port.in;

import com.fintrack.domain.model.Transaction;
import com.fintrack.domain.model.TransactionType;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface CreateTransactionUseCase {

    /**
     * Creates and processes a financial transaction.
     *
     * This use case:
     * 1. Validates the account exists and is active
     * 2. Applies the debit/credit to the account balance
     * 3. Persists the transaction with COMPLETED status
     * 4. Publishes a TransactionCreatedEvent (will be used for Kafka in Month 7)
     *
     * @throws com.fintrack.domain.exception.AccountNotFoundException if account not found
     * @throws com.fintrack.domain.exception.InsufficientBalanceException if debit > balance
     */
    Transaction execute(UUID accountId, BigDecimal amount, TransactionType type,
                        String description, String category);

    List<Transaction> findByAccount(UUID accountId);

    List<Transaction> findByAccountAndCategory(UUID accountId, String category);
}
