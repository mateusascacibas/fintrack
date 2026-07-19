package com.fintrack.domain.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * TransactionCreatedEvent — Evento de domínio.
 *
 * WHY Java Record?
 * Records (Java 16+) são perfeitos para Value Objects e eventos:
 * - Imutáveis por padrão (campos final)
 * - equals/hashCode/toString gerados automaticamente
 * - Sintaxe concisa
 * - Deixam claro que o objeto é apenas portador de dados (sem comportamento)
 *
 * Eventos de domínio são o mecanismo pelo qual o domínio comunica que
 * algo importante aconteceu. Outros sistemas (Kafka consumers, listeners)
 * reagem a esses eventos sem que o domínio precise conhecê-los.
 */
public record TransactionCreatedEvent(
    UUID transactionId,
    UUID accountId,
    BigDecimal amount,
    String type,
    String category,
    LocalDateTime occurredAt
) {
    // O Record valida que os campos não são null no construtor canônico
    public TransactionCreatedEvent {
        if (transactionId == null) throw new IllegalArgumentException("transactionId is required");
        if (accountId == null) throw new IllegalArgumentException("accountId is required");
        if (amount == null) throw new IllegalArgumentException("amount is required");
    }
}
