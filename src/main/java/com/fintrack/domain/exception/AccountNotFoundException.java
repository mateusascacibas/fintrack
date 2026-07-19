package com.fintrack.domain.exception;

import java.util.UUID;

public class AccountNotFoundException extends DomainException {
    public AccountNotFoundException(UUID accountId) {
        super("Account not found with id: " + accountId);
    }
}
