package com.fintrack.domain.port.out;

import com.fintrack.domain.model.Transaction;

import java.util.List;
import java.util.UUID;

public interface TransactionRepository {

    Transaction save(Transaction transaction);

    List<Transaction> findByAccountId(UUID accountId);

    List<Transaction> findByAccountIdAndCategory(UUID accountId, String category);
}
