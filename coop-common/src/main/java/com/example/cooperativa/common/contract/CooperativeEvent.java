package com.example.cooperativa.common.contract;

import java.time.Instant;

/**
 * Contrato JSON canônico dos eventos publicados pela cooperativa no RabbitMQ.
 */
public record CooperativeEvent(
        String eventId,
        String eventType,
        String routingKey,
        Instant occurredAt,
        EventPayload payload
) {
}
