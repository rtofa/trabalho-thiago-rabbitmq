package com.example.cooperativa.qualidade.service;

import com.example.cooperativa.common.contract.CooperativeEvent;
import com.example.cooperativa.common.contract.EventPayload;
import com.example.cooperativa.common.contract.EventTypes;
import com.example.cooperativa.common.messaging.ProcessedEventRepository;
import com.example.cooperativa.qualidade.domain.ResultadoAnaliseEntity;
import com.example.cooperativa.qualidade.domain.ResultadoAnaliseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Map;

@Service
public class QualidadeService {

    private static final Logger log = LoggerFactory.getLogger(QualidadeService.class);
    private static final String CONSUMER_NAME = "qualidade";

    private final ProcessedEventRepository processedEventRepository;
    private final ResultadoAnaliseRepository resultadoRepository;
    private final JsonMapper jsonMapper;

    public QualidadeService(
            ProcessedEventRepository processedEventRepository,
            ResultadoAnaliseRepository resultadoRepository,
            JsonMapper jsonMapper
    ) {
        this.processedEventRepository = processedEventRepository;
        this.resultadoRepository = resultadoRepository;
        this.jsonMapper = jsonMapper;
    }

    public void processar(CooperativeEvent event) {
        if (!processedEventRepository.tryClaimEvent(CONSUMER_NAME, event.eventId(), "qualidade-1")) {
            log.info("[Qualidade] Evento duplicado ignorado: eventId={}", event.eventId());
            return;
        }

        EventPayload payload = event.payload();
        String lote = payload != null && payload.lote() != null ? payload.lote() : "SEM_LOTE";
        String produto = payload != null ? payload.produto() : null;
        String motivo = payload != null ? payload.motivo() : null;
        Map<String, Object> detalhes = payload != null && payload.detalhes() != null ? payload.detalhes() : Map.of();
        String detalhesJson = jsonMapper.writeValueAsString(detalhes);

        String resultado = derivarResultado(event.eventType());

        ResultadoAnaliseEntity entity = new ResultadoAnaliseEntity(
                event.eventId(),
                event.eventType(),
                event.routingKey(),
                lote,
                produto,
                resultado,
                motivo,
                detalhesJson,
                event.occurredAt() != null ? event.occurredAt() : Instant.now(),
                Instant.now()
        );
        resultadoRepository.save(entity);
        log.info("[Qualidade] Resultado {} registrado para lote={}, routingKey={}, motivo={}, eventId={}",
                resultado, lote, event.routingKey(), motivo, event.eventId());
    }

    private static String derivarResultado(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return "DESCONHECIDO";
        }
        if (EventTypes.ANALISE_APROVADA.equals(eventType)) {
            return "APROVADA";
        }
        if (EventTypes.ANALISE_REPROVADA.equals(eventType)) {
            return "REPROVADA";
        }
        if (eventType.startsWith("ANALISE_") && eventType.length() > "ANALISE_".length()) {
            return eventType.substring("ANALISE_".length());
        }
        return eventType;
    }
}
