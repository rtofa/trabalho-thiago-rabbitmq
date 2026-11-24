package com.example.cooperativa.common.contract;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Payload de domínio compartilhado pelos eventos da cooperativa agrícola.
 * Contém os campos utilizados pelos diferentes consumidores (pesagem,
 * avisos ao cooperado, análise de qualidade e indicadores de recepção)
 * além de um mapa extensível para novos fatos do domínio.
 */
public record EventPayload(
        String lote,
        String cooperado,
        String produto,
        BigDecimal pesoLiquidoKg,
        String motivo,
        Map<String, Object> detalhes
) {
    public EventPayload {
        detalhes = detalhes == null ? Map.of() : Map.copyOf(detalhes);
    }
}
