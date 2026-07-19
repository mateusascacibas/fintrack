package com.fintrack.domain.port.out;

/**
 * EventPublisher — Porta de saída para publicação de eventos.
 *
 * MÊS 7 DO PLANO: Esta interface será implementada pelo KafkaEventPublisher.
 * Por ora, o adapter NoOpEventPublisher (não faz nada) está ativo.
 *
 * WHY define this now if Kafka comes in month 7?
 * Porque o domínio e a lógica de negócio já sabem que precisam publicar eventos.
 * O COMO publicar (Kafka, RabbitMQ, in-memory, noop) é detalhe de infraestrutura.
 * Trocar a implementação não exigirá nenhuma mudança no domínio ou serviço.
 */
public interface EventPublisher {
    void publish(String topic, Object event);
}
