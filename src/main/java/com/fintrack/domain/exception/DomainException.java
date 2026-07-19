package com.fintrack.domain.exception;

/**
 * Base exception for all domain rule violations.
 *
 * WHY unchecked (extends RuntimeException)?
 * Checked exceptions (throws na assinatura) forçam todos os chamadores
 * a lidar com o erro, poluindo a API com try/catch desnecessários.
 * Domain exceptions representam violações de regra de negócio — o chamador
 * não pode "recuperar" delas programaticamente, apenas reportar ao usuário.
 * Por isso, unchecked é a escolha correta aqui.
 */
public class DomainException extends RuntimeException {

    public DomainException(String message) {
        super(message);
    }

    public DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
