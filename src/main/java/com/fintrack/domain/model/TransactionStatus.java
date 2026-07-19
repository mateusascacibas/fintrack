package com.fintrack.domain.model;

public enum TransactionStatus {
    PENDING,    // Criada, aguardando processamento
    COMPLETED,  // Processada com sucesso
    FAILED      // Falhou (saldo insuficiente, erro externo)
}
