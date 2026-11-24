package com.example.cooperativa.producer.api;

import com.example.cooperativa.common.contract.EventPayload;

import java.time.Instant;

public record CustomEventRequest(
        String eventId,
        String eventType,
        String routingKey,
        Instant occurredAt,
        EventPayload payload
) {
}
