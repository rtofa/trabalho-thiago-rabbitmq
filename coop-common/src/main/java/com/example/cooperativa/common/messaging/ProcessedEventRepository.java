package com.example.cooperativa.common.messaging;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Registro atômico de idempotência compartilhado entre instâncias de um mesmo consumidor.
 * Usa INSERT ... ON CONFLICT DO NOTHING no PostgreSQL para garantir semântica de
 * processamento único (efeito único) mesmo sob entregas duplicadas ou concorrentes.
 */
@Repository
public class ProcessedEventRepository {

    private static final String TRY_CLAIM_SQL = """
            INSERT INTO processed_events (consumer_name, event_id, instance_id)
            VALUES (?, ?, ?)
            ON CONFLICT (consumer_name, event_id) DO NOTHING
            """;

    private final JdbcTemplate jdbcTemplate;

    public ProcessedEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Tenta reivindicar o processamento inédito de {@code eventId} para {@code consumerName}.
     *
     * @return {@code true} se este evento ainda não havia sido processado por essa responsabilidade;
     *         {@code false} se já foi processado anteriormente (duplicata).
     */
    public boolean tryClaimEvent(String consumerName, String eventId, String instanceId) {
        int rows = jdbcTemplate.update(TRY_CLAIM_SQL, consumerName, eventId, instanceId);
        return rows == 1;
    }
}
