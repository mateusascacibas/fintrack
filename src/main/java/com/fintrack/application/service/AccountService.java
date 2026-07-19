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

/**
 * AccountService — Implementa o caso de uso AccountUseCase.
 *
 * CAMADA: application/service
 *
 * RESPONSABILIDADES desta camada:
 * 1. Orquestrar o domínio (Account) e as portas de saída (AccountRepository)
 * 2. Controlar transações (@Transactional)
 * 3. NÃO conter regras de negócio — isso é responsabilidade do domínio
 *
 * REGRA: Se você está escrevendo um if que valida regra de negócio aqui,
 * provavelmente deveria estar no método do domínio (Account).
 *
 * WHY @Transactional here and not in the domain?
 * Transação é um conceito de infraestrutura (banco de dados).
 * O domínio não sabe que existe um banco. A camada de aplicação coordena
 * a unidade de trabalho — se algo falhar, tudo é revertido.
 */
@Service
@Transactional(readOnly = true)
// readOnly = true é o default aqui. Métodos que escrevem sobrescrevem com @Transactional(readOnly = false)
// WHY readOnly = true as default?
// 1. Performance: Hibernate desativa dirty checking (não rastreia mudanças)
// 2. Banco: alguns bancos roteiam reads para réplicas automaticamente com esse hint
// 3. Segurança: protege contra escrita acidental em métodos de leitura
public class AccountService implements AccountUseCase {

    private final AccountRepository accountRepository;

    // WHY constructor injection and not @Autowired on field?
    // 1. Imutabilidade: campo pode ser final
    // 2. Testabilidade: facilita instanciar a classe em testes sem Spring
    // 3. Deixa explícito quais dependências a classe precisa
    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    @Transactional
    public Account createAccount(String name, AccountType type, BigDecimal initialBalance) {
        // Regra de negócio de criação está no factory method Account.create()
        Account account = Account.create(name, type, initialBalance);
        return accountRepository.save(account);
    }

    @Override
    public Account findById(UUID accountId) {
        return accountRepository.findById(accountId)
            .orElseThrow(() -> new AccountNotFoundException(accountId));
        // WHY orElseThrow here and not in the repository?
        // O repositório retorna Optional porque "não encontrar" é um resultado válido.
        // É o caso de uso que decide se "não encontrar" é um erro (aqui é).
        // Em outro contexto, "não encontrar" poderia ser tratado de forma diferente.
    }

    @Override
    public List<Account> findAllActiveAccounts() {
        return accountRepository.findAllActive();
    }

    @Override
    @Transactional
    public Account renameAccount(UUID accountId, String newName) {
        Account account = findById(accountId);
        account.rename(newName); // Regra de validação do nome está no domínio
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
