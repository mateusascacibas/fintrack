package com.fintrack.domain.port.in;

import com.fintrack.domain.model.Account;
import com.fintrack.domain.model.AccountType;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * AccountUseCase — Porta de ENTRADA do domínio para operações de conta.
 *
 * CONCEITO HEXAGONAL: "Ports and Adapters"
 *
 * PORT (interface aqui): Define O QUE pode ser feito. É o contrato.
 * ADAPTER (implementação em application/service/): Define COMO é feito.
 *
 * A camada REST (AccountController) depende desta interface, nunca da implementação.
 * Isso permite:
 * 1. Testar o controller sem subir o Spring (mock do use case)
 * 2. Trocar a implementação sem mudar o controller
 * 3. Ter múltiplas implementações (ex: uma para produção, uma para testes A/B)
 */
public interface AccountUseCase {

    Account createAccount(String name, AccountType type, BigDecimal initialBalance);

    Account findById(UUID accountId);

    List<Account> findAllActiveAccounts();

    Account renameAccount(UUID accountId, String newName);

    void deactivateAccount(UUID accountId);
}
