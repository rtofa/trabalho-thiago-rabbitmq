package com.example.cooperativa.producer.service;

import com.example.cooperativa.common.contract.CooperativeEvent;
import com.example.cooperativa.common.contract.EventPayload;
import com.example.cooperativa.common.contract.EventTypes;
import com.example.cooperativa.common.messaging.CooperativeTopologyConstants;
import com.example.cooperativa.producer.api.CustomEventRequest;
import com.example.cooperativa.producer.api.PublishResponse;
import com.example.cooperativa.producer.api.TriggerEventRequest;
import com.example.cooperativa.producer.domain.PublishedEventEntity;
import com.example.cooperativa.producer.domain.PublishedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class CooperativeEventPublisherService {

    private static final Logger log = LoggerFactory.getLogger(CooperativeEventPublisherService.class);

    private static final List<String> SAMPLE_PRODUCTS = List.of("SOJA", "MILHO", "TRIGO", "CAFE");
    private static final List<String> SAMPLE_COOPERADOS = List.of(
            "Fazenda Boa Vista",
            "Sítio Esperança",
            "Estância Santa Helena",
            "Chácara São Pedro"
    );
    private static final List<BigDecimal> SAMPLE_WEIGHTS = List.of(
            new BigDecimal("18500.00"),
            new BigDecimal("14200.50"),
            new BigDecimal("21300.00"),
            new BigDecimal("9850.75")
    );

    private final RabbitTemplate rabbitTemplate;
    private final PublishedEventRepository repository;
    private final JdbcTemplate jdbcTemplate;
    private final JsonMapper jsonMapper;
    private final AtomicInteger sequence = new AtomicInteger(1);

    public CooperativeEventPublisherService(
            RabbitTemplate rabbitTemplate,
            PublishedEventRepository repository,
            JdbcTemplate jdbcTemplate,
            JsonMapper jsonMapper
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
        this.jsonMapper = jsonMapper;
    }

    public PublishResponse publishCargaRecebida(TriggerEventRequest req) {
        CooperativeEvent event = buildDomainEvent(
                EventTypes.CARGA_RECEBIDA,
                EventTypes.RK_CARGA_RECEBIDA,
                req,
                true,
                null
        );
        return publish(event, false);
    }

    public PublishResponse publishCargaRecusada(TriggerEventRequest req) {
        CooperativeEvent event = buildDomainEvent(
                EventTypes.CARGA_RECUSADA,
                EventTypes.RK_CARGA_RECUSADA,
                req,
                false,
                "Umidade acima do limite aceito pela unidade (21,5%)"
        );
        return publish(event, false);
    }

    public PublishResponse publishAnaliseAprovada(TriggerEventRequest req) {
        CooperativeEvent event = buildDomainEvent(
                EventTypes.ANALISE_APROVADA,
                EventTypes.RK_ANALISE_APROVADA,
                req,
                false,
                null
        );
        return publish(event, false);
    }

    public PublishResponse publishAnaliseReprovada(TriggerEventRequest req) {
        CooperativeEvent event = buildDomainEvent(
                EventTypes.ANALISE_REPROVADA,
                EventTypes.RK_ANALISE_REPROVADA,
                req,
                false,
                "Percentual de grãos avariados acima da tolerância normativa"
        );
        return publish(event, false);
    }

    public PublishResponse publishCustom(CustomEventRequest req) {
        if (req == null || req.routingKey() == null || req.routingKey().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "routingKey é obrigatória");
        }
        String routingKey = req.routingKey().trim().toLowerCase(Locale.ROOT);
        String eventType = (req.eventType() == null || req.eventType().isBlank())
                ? routingKey.replace('.', '_').toUpperCase(Locale.ROOT)
                : req.eventType().trim().toUpperCase(Locale.ROOT);
        String eventId = resolveEventId(req.eventId());
        Instant occurredAt = req.occurredAt() != null ? req.occurredAt() : Instant.now();

        EventPayload payload = req.payload() != null
                ? normalizePayload(req.payload())
                : new EventPayload(
                        nextSampleLote(),
                        SAMPLE_COOPERADOS.get(0),
                        "SOJA",
                        null,
                        "Evento personalizado do domínio",
                        Map.of()
                );

        CooperativeEvent event = new CooperativeEvent(eventId, eventType, routingKey, occurredAt, payload);
        return publish(event, false);
    }

    public PublishResponse republishByEventId(String eventId) {
        PublishedEventEntity stored = repository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Evento não encontrado para republicação: " + eventId));

        EventPayload payload = jsonMapper.readValue(stored.getPayloadJson(), EventPayload.class);
        CooperativeEvent event = new CooperativeEvent(
                stored.getEventId(),
                stored.getEventType(),
                stored.getRoutingKey(),
                stored.getOccurredAt(),
                payload
        );
        return publish(event, true);
    }

    public List<CooperativeEvent> listPublishedEvents() {
        return repository.findAllByOrderByCreatedAtDesc().stream()
                .map(entity -> new CooperativeEvent(
                        entity.getEventId(),
                        entity.getEventType(),
                        entity.getRoutingKey(),
                        entity.getOccurredAt(),
                        jsonMapper.readValue(entity.getPayloadJson(), EventPayload.class)
                ))
                .toList();
    }

    private PublishResponse publish(CooperativeEvent event, boolean explicitReplay) {
        boolean alreadyExisted = !insertPublishedEventIfAbsent(event);
        boolean isDuplicatePublish = explicitReplay || alreadyExisted;

        CorrelationData correlationData = new CorrelationData(event.eventId());
        rabbitTemplate.convertAndSend(
                CooperativeTopologyConstants.EXCHANGE_EVENTS,
                event.routingKey(),
                event,
                message -> {
                    message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    message.getMessageProperties().setMessageId(event.eventId());
                    message.getMessageProperties().setType(event.eventType());
                    return message;
                },
                correlationData
        );

        boolean confirmed = awaitConfirm(correlationData);
        log.info("[Producer] Evento publicado no RabbitMQ: eventId={}, tipo={}, routingKey={}, replay={}, brokerConfirmed={}",
                event.eventId(), event.eventType(), event.routingKey(), isDuplicatePublish, confirmed);

        String statusMsg = isDuplicatePublish
                ? "Evento republicado com o mesmo eventId (teste de duplicidade)."
                : "Evento publicado com sucesso.";

        return new PublishResponse(event, confirmed, isDuplicatePublish, statusMsg);
    }

    private boolean insertPublishedEventIfAbsent(CooperativeEvent event) {
        String payloadJson = jsonMapper.writeValueAsString(event.payload());
        int rows = jdbcTemplate.update(
                """
                INSERT INTO published_events (event_id, event_type, routing_key, occurred_at, payload_json)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (event_id) DO NOTHING
                """,
                event.eventId(),
                event.eventType(),
                event.routingKey(),
                Timestamp.from(event.occurredAt()),
                payloadJson
        );
        return rows == 1;
    }

    private boolean awaitConfirm(CorrelationData correlationData) {
        try {
            CorrelationData.Confirm confirm = correlationData.getFuture().get(5, TimeUnit.SECONDS);
            return confirm != null && confirm.ack();
        } catch (Exception ex) {
            log.warn("[Producer] Timeout/erro aguardando Publisher Confirm para eventId={}: {}",
                    correlationData.getId(), ex.getMessage());
            return false;
        }
    }

    private CooperativeEvent buildDomainEvent(
            String eventType,
            String routingKey,
            TriggerEventRequest req,
            boolean defaultWeightRequired,
            String defaultMotivo
    ) {
        int idx = Math.floorMod(sequence.getAndIncrement() - 1, 4);
        String eventId = resolveEventId(req != null ? req.eventId() : null);
        Instant occurredAt = req != null && req.occurredAt() != null ? req.occurredAt() : Instant.now();

        String lote = (req != null && isNotBlank(req.lote())) ? req.lote().trim() : nextSampleLote();
        String cooperado = (req != null && isNotBlank(req.cooperado()))
                ? req.cooperado().trim()
                : SAMPLE_COOPERADOS.get(idx);
        String produto = (req != null && isNotBlank(req.produto()))
                ? req.produto().trim().toUpperCase(Locale.ROOT)
                : SAMPLE_PRODUCTS.get(idx);
        BigDecimal peso = (req != null && req.pesoLiquidoKg() != null)
                ? req.pesoLiquidoKg()
                : (defaultWeightRequired ? SAMPLE_WEIGHTS.get(idx) : null);
        String motivo = (req != null && isNotBlank(req.motivo()))
                ? req.motivo().trim()
                : defaultMotivo;
        Map<String, Object> detalhes = (req != null && req.detalhes() != null)
                ? req.detalhes()
                : Map.of("origem", "unidade-central");

        EventPayload payload = new EventPayload(lote, cooperado, produto, peso, motivo, detalhes);
        return new CooperativeEvent(eventId, eventType, routingKey, occurredAt, payload);
    }

    private EventPayload normalizePayload(EventPayload payload) {
        String lote = isNotBlank(payload.lote()) ? payload.lote().trim() : nextSampleLote();
        String produto = isNotBlank(payload.produto()) ? payload.produto().trim().toUpperCase(Locale.ROOT) : "SOJA";
        String cooperado = isNotBlank(payload.cooperado()) ? payload.cooperado().trim() : SAMPLE_COOPERADOS.get(0);
        return new EventPayload(lote, cooperado, produto, payload.pesoLiquidoKg(), payload.motivo(), payload.detalhes());
    }

    private String resolveEventId(String suppliedId) {
        return isNotBlank(suppliedId) ? suppliedId.trim() : UUID.randomUUID().toString();
    }

    private String nextSampleLote() {
        return String.format("LOTE-2026-%03d", sequence.get());
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
