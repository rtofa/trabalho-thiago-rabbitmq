package com.example.cooperativa.producer.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record TriggerEventRequest(
        String eventId,
        String lote,
        String cooperado,
        String produto,
        BigDecimal pesoLiquidoKg,
        String motivo,
        Instant occurredAt,
        Map<String, Object> detalhes
) {
}
