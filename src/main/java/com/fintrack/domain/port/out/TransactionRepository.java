package com.fintrack.domain.port.out;

import com.fintrack.domain.model.Transaction;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

public interface TransactionRepository {

    Transaction save(Transaction transaction);

    List<Transaction> findByAccountId(UUID accountId);

    List<Transaction> findByAccountIdAndCategory(UUID accountId, String category);

    List<Transaction> findByAccountIdAndMonth(UUID accountId, YearMonth month);

    List<Transaction> findRecentByAccountId(UUID accountId, int i);
}
