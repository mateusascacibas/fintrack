package com.fintrack.infrastructure.messaging;

import com.fintrack.domain.port.out.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * NoOpEventPublisher — Implementação placeholder do EventPublisher.
 *
 * OBJETIVO: Permitir que o sistema funcione completamente sem Kafka
 * durante os meses 1–6 do plano de estudos.
 *
 * No Mês 7, você irá:
 * 1. Adicionar spring-kafka ao pom.xml
 * 2. Criar KafkaEventPublisher implements EventPublisher
 * 3. Marcar KafkaEventPublisher com @Primary (ou remover este @Component)
 * 4. O CreateTransactionService NÃO muda uma linha — é a arquitetura hexagonal funcionando.
 *
 * Este é o poder do padrão: trocar infraestrutura sem tocar no domínio/aplicação.
 */
@Component
public class NoOpEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(NoOpEventPublisher.class);

    @Override
    public void publish(String topic, Object event) {
        // Apenas loga — não faz nada de verdade até o Mês 7
        log.debug("NoOp event publisher: topic={}, eventType={}", topic, event.getClass().getSimpleName());
    }
}
