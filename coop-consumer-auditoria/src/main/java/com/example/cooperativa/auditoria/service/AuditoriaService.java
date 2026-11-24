package com.example.cooperativa.auditoria.service;

import com.example.cooperativa.auditoria.domain.AuditoriaEventoEntity;
import com.example.cooperativa.auditoria.domain.AuditoriaEventoRepository;
import com.example.cooperativa.common.contract.CooperativeEvent;
import com.example.cooperativa.common.messaging.ProcessedEventRepository;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class AuditoriaService {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaService.class);
    private static final String CONSUMER_NAME = "auditoria";

    private final ProcessedEventRepository processedEventRepository;
    private final AuditoriaEventoRepository auditoriaRepository;
    private final JdbcTemplate jdbcTemplate;
    private final JsonMapper jsonMapper;

    public AuditoriaService(
            ProcessedEventRepository processedEventRepository,
            AuditoriaEventoRepository auditoriaRepository,
            JdbcTemplate jdbcTemplate,
            JsonMapper jsonMapper
    ) {
        this.processedEventRepository = processedEventRepository;
        this.auditoriaRepository = auditoriaRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.jsonMapper = jsonMapper;
    }

    public void processar(CooperativeEvent event) {
        Instant now = Instant.now();
        boolean firstTime = processedEventRepository.tryClaimEvent(CONSUMER_NAME, event.eventId(), "auditoria-1");
        if (!firstTime) {
            jdbcTemplate.update(
                    "UPDATE auditoria_eventos SET delivery_count = delivery_count + 1, last_seen_at = ? WHERE event_id = ?",
                    Timestamp.from(now),
                    event.eventId()
            );
            log.info("[Auditoria] Entrega duplicada contabilizada no histórico sem duplicar registro: eventId={}",
                    event.eventId());
            return;
        }

        String payloadJson = jsonMapper.writeValueAsString(event.payload());
        String eventType = event.eventType() != null ? event.eventType() : "DESCONHECIDO";
        String routingKey = event.routingKey() != null ? event.routingKey() : "sem.routing.key";
        Instant occurredAt = event.occurredAt() != null ? event.occurredAt() : now;

        AuditoriaEventoEntity entity = new AuditoriaEventoEntity(
                event.eventId(),
                eventType,
                routingKey,
                occurredAt,
                payloadJson,
                1,
                now,
                now
        );
        auditoriaRepository.save(entity);
        log.info("[Auditoria] Evento auditado: eventId={}, tipo={}, routingKey={}, occurredAt={}",
                event.eventId(), eventType, routingKey, occurredAt);
    }

    public List<AuditoriaEventoEntity> consultar(String eventId, String eventType, Instant inicio, Instant fim) {
        Specification<AuditoriaEventoEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (eventId != null && !eventId.isBlank()) {
                predicates.add(cb.equal(root.get("eventId"), eventId.trim()));
            }
            if (eventType != null && !eventType.isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("eventType")), eventType.trim().toUpperCase(Locale.ROOT)));
            }
            if (inicio != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), inicio));
            }
            if (fim != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("occurredAt"), fim));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return auditoriaRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "occurredAt", "id"));
    }
}
