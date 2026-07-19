package com.fintrack.interfaces.rest.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * CreateAccountRequest — DTO de entrada usando Java Record.
 *
 * WHY Java Record for DTOs?
 * Records são imutáveis por design — perfeito para DTOs que são apenas portadores de dados.
 * Geram automaticamente: construtor, getters (sem get prefix), equals, hashCode, toString.
 *
 * CAMADA: interfaces/rest (mais externa da arquitetura)
 * RESPONSABILIDADE: Validar formato e presença de campos.
 * NÃO É responsabilidade deste DTO validar regras de negócio.
 *
 * Distinção importante:
 * - "name não pode ser vazio" → responsabilidade do DTO (@NotBlank)
 * - "nome não pode ser duplicado" → responsabilidade do domínio/serviço
 */
public record CreateAccountRequest(

    @NotBlank(message = "Account name is required")
    @Size(min = 2, max = 100, message = "Account name must be between 2 and 100 characters")
    String name,

    @NotBlank(message = "Account type is required")
    String type, // Será convertido para AccountType enum no controller

    @NotNull(message = "Initial balance is required")
    @DecimalMin(value = "0.00", message = "Initial balance cannot be negative")
    BigDecimal initialBalance
) {}
