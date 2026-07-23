package com.fintrack.domain.exception;

/**
 * Lançada quando um valor inválido (negativo ou zero) é passado para debit/credit.
 * Mapeada para HTTP 400 Bad Request no GlobalExceptionHandler.
 */
public class InvalidAmountException extends DomainException {
    public InvalidAmountException(String message) {
        super(message);
    }
}
