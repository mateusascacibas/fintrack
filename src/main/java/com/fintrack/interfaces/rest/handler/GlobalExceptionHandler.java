package com.fintrack.interfaces.rest.handler;

import com.fintrack.domain.exception.AccountNotFoundException;
import com.fintrack.domain.exception.DomainException;
import com.fintrack.domain.exception.InsufficientBalanceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * GlobalExceptionHandler — Centraliza o tratamento de erros da API.
 *
 * WHY @RestControllerAdvice?
 * Intercepta exceções lançadas em qualquer @RestController.
 * Sem isso, cada controller precisaria de try/catch — código duplicado e inconsistente.
 *
 * WHY ProblemDetail (RFC 7807)?
 * É o padrão HTTP para respostas de erro. Define um formato JSON consistente:
 * {
 *   "type": "https://fintrack.com/errors/insufficient-balance",
 *   "title": "Insufficient Balance",
 *   "status": 422,
 *   "detail": "Insufficient balance. Current: 100.00, Required: 500.00",
 *   "instance": "/api/v1/accounts/123/transactions"
 * }
 *
 * Spring Boot 3 suporta ProblemDetail nativamente.
 * Antes do Spring Boot 3, você precisava criar sua própria classe de resposta de erro.
 * Esse é um dos motivos pelos quais Java 21 + Spring Boot 3 foi a escolha certa.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String ERROR_BASE_URI = "https://fintrack.com/errors";

    @ExceptionHandler(AccountNotFoundException.class)
    public ProblemDetail handleAccountNotFound(AccountNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setType(URI.create(ERROR_BASE_URI + "/account-not-found"));
        problem.setTitle("Account Not Found");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    public ProblemDetail handleInsufficientBalance(InsufficientBalanceException ex) {
        // WHY 422 Unprocessable Entity (not 400 Bad Request)?
        // 400 = o request está malformado (JSON inválido, campo faltando)
        // 422 = o request está bem formado, mas a regra de negócio impede a operação
        // Saldo insuficiente é uma violação de regra de negócio, não de formato.
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setType(URI.create(ERROR_BASE_URI + "/insufficient-balance"));
        problem.setTitle("Insufficient Balance");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(DomainException.class)
    public ProblemDetail handleDomainException(DomainException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setType(URI.create(ERROR_BASE_URI + "/domain-rule-violation"));
        problem.setTitle("Domain Rule Violation");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, "Request validation failed");
        problem.setType(URI.create(ERROR_BASE_URI + "/validation-error"));
        problem.setTitle("Validation Error");
        problem.setProperty("fieldErrors", fieldErrors);
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        // Captura erros como AccountType.valueOf("INVALID_TYPE")
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setType(URI.create(ERROR_BASE_URI + "/invalid-argument"));
        problem.setTitle("Invalid Argument");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex) {
        // Último recurso — nunca expõe detalhes internos para o cliente
        log.error("Unexpected error", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred. Please try again later.");
        problem.setType(URI.create(ERROR_BASE_URI + "/internal-error"));
        problem.setTitle("Internal Server Error");
        problem.setProperty("timestamp", Instant.now());
        return problem;
        // NOTE: O log.error acima com a stack trace completa é para debugging.
        // O response não expõe nada — por segurança e por UX.
    }
}
