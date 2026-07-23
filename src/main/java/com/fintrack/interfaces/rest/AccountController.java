package com.fintrack.interfaces.rest;

import com.fintrack.domain.model.AccountType;
import com.fintrack.domain.model.TransactionType;
import com.fintrack.domain.port.in.AccountUseCase;
import com.fintrack.domain.port.in.CreateTransactionUseCase;
import com.fintrack.interfaces.rest.dto.request.CreateAccountRequest;
import com.fintrack.interfaces.rest.dto.request.CreateTransactionRequest;
import com.fintrack.interfaces.rest.dto.response.AccountResponse;
import com.fintrack.interfaces.rest.dto.response.TransactionResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * AccountController — Adapter de entrada HTTP.
 *
 * RESPONSABILIDADES:
 * 1. Receber requests HTTP
 * 2. Converter DTOs de request para tipos do domínio
 * 3. Chamar o use case correto
 * 4. Converter resultado do domínio para DTO de response
 * 5. Retornar o HTTP status correto
 *
 * NÃO É responsabilidade do controller:
 * - Lógica de negócio (isso é do domínio)
 * - Acesso ao banco (isso é da infraestrutura)
 * - Tratamento de erros de domínio (isso é do GlobalExceptionHandler)
 *
 * WHY @RestController and not @Controller?
 * @RestController = @Controller + @ResponseBody em todos os métodos.
 * @ResponseBody faz o Spring serializar o retorno como JSON automaticamente.
 *
 * BOAS PRÁTICAS DE HTTP STATUS:
 * 200 OK → GET com sucesso
 * 201 Created → POST que cria recurso (com Location header)
 * 204 No Content → DELETE com sucesso (sem body)
 * 400 Bad Request → Validação de DTO falhou
 * 401 Unauthorized → Não autenticado
 * 403 Forbidden → Autenticado mas sem permissão
 * 404 Not Found → Recurso não existe
 * 422 Unprocessable Entity → Regra de negócio violada (saldo insuficiente, etc.)
 */
@RestController
@RequestMapping("/api/v1/accounts")
// WHY /api/v1/?
// Versioning na URL é a estratégia mais simples e explícita.
// Quando você lançar v2 com breaking changes, v1 continua funcionando.
public class AccountController {

    private final AccountUseCase accountUseCase;
    private final CreateTransactionUseCase transactionUseCase;

    public AccountController(AccountUseCase accountUseCase,
                             CreateTransactionUseCase transactionUseCase) {
        this.accountUseCase = accountUseCase;
        this.transactionUseCase = transactionUseCase;
    }

    // ──────────────────────────────────────────────
    // ACCOUNTS
    // ──────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(
        @RequestBody @Valid CreateAccountRequest request) {

        var account = accountUseCase.createAccount(
            request.name(),
            AccountType.valueOf(request.type().toUpperCase()),
            request.initialBalance()
        );

        // WHY return 201 with Location header?
        // RFC 7231: POST que cria um recurso DEVE retornar 201 Created
        // com um header Location apontando para o recurso criado.
        // Clientes (mobile, frontend) podem usar esse header para navegar ao recurso.
        URI location = ServletUriComponentsBuilder
            .fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(account.getId())
            .toUri();

        return ResponseEntity.created(location).body(AccountResponse.from(account));
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable UUID accountId) {
        return ResponseEntity.ok(AccountResponse.from(accountUseCase.findById(accountId)));
    }

    @GetMapping
    public ResponseEntity<List<AccountResponse>> getAllAccounts() {
        List<AccountResponse> accounts = accountUseCase.findAllActiveAccounts()
            .stream()
            .map(AccountResponse::from)
            .toList();
        return ResponseEntity.ok(accounts);
    }

    @DeleteMapping("/{accountId}")
    public ResponseEntity<Void> deactivateAccount(@PathVariable UUID accountId) {
        accountUseCase.deactivateAccount(accountId);
        return ResponseEntity.noContent().build(); // 204 No Content
    }

    // ──────────────────────────────────────────────
    // TRANSACTIONS (nested resource)
    // WHY nested route /accounts/{id}/transactions?
    // Transações pertencem a uma conta. A URL expressa essa hierarquia.
    // REST princípio: recursos relacionados são sub-recursos.
    // ──────────────────────────────────────────────

    @PostMapping("/{accountId}/transactions")
    public ResponseEntity<TransactionResponse> createTransaction(
        @PathVariable UUID accountId,
        @RequestBody @Valid CreateTransactionRequest request) {

        var transaction = transactionUseCase.execute(
            accountId,
            request.amount(),
            TransactionType.valueOf(request.type().toUpperCase()),
            request.description(),
            request.category()
        );

        URI location = ServletUriComponentsBuilder
            .fromCurrentContextPath()
            .path("/api/v1/accounts/{accountId}/transactions/{transactionId}")
            .buildAndExpand(accountId, transaction.id())
            .toUri();

        return ResponseEntity.created(location).body(TransactionResponse.from(transaction));
    }

    @GetMapping("/{accountId}/transactions")
    public ResponseEntity<List<TransactionResponse>> getTransactions(
        @PathVariable UUID accountId,
        @RequestParam(required = false) String category) {

        var transactions = category != null
            ? transactionUseCase.findByAccountAndCategory(accountId, category)
            : transactionUseCase.findByAccount(accountId);

        return ResponseEntity.ok(
            transactions.stream().map(TransactionResponse::from).toList()
        );
    }
}
