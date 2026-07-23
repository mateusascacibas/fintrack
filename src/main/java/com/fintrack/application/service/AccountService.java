package com.fintrack.application.service;

import com.fintrack.domain.exception.AccountNotFoundException;
import com.fintrack.domain.model.Account;
import com.fintrack.domain.model.AccountType;
import com.fintrack.domain.port.in.AccountUseCase;
import com.fintrack.domain.port.out.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AccountService implements AccountUseCase {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    @Transactional
    public Account createAccount(String name, AccountType type, BigDecimal initialBalance) {
        // CORRIGIDO: Account.create() só aceita 2 params.
        // Com saldo inicial usa createWithBalance().
        Account account = Account.createWithBalance(name, type, initialBalance);
        return accountRepository.save(account);
    }

    @Override
    public Account findById(UUID accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    @Override
    public List<Account> findAllActiveAccounts() {
        return accountRepository.findAllActive();
    }

    @Override
    @Transactional
    public Account renameAccount(UUID accountId, String newName) {
        Account account = findById(accountId);
        account.rename(newName); // método adicionado ao domínio — ver Account.java
        return accountRepository.save(account);
    }

    @Override
    @Transactional
    public void deactivateAccount(UUID accountId) {
        Account account = findById(accountId);
        account.deactivate();
        accountRepository.save(account);
    }
}