package com.fintrack.interfaces.rest.dto.response;

import com.fintrack.domain.model.Account;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * AccountResponse — DTO de saída.
 *
 * WHY a separate response DTO and not return the domain model directly?
 *
 * 1. CONTROLE DA API: O domínio pode ter campos que não devem ser expostos.
 *    Ex: campos de auditoria interna, flags de controle, etc.
 *
 * 2. VERSIONAMENTO: Você pode mudar o modelo de domínio sem quebrar a API.
 *    V1 retorna { "name": "..." }, V2 retorna { "accountName": "..." }.
 *
 * 3. FORMATO CUSTOMIZADO: Campos de dinheiro podem precisar de formatação
 *    diferente do que o BigDecimal.toString() retorna.
 *
 * factory method from(Account) centraliza o mapeamento domain → response.
 */
public record AccountResponse(
    UUID id,
    String name,
    BigDecimal balance,
    String type,
    boolean active,
    LocalDateTime createdAt
) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(
            account.getId(),
            account.getName(),
            account.getBalance(),
            account.getType().name(),
            account.isActive(),
            account.getCreatedAt()
        );
    }
}
