package com.example.cooperativa.indicadores.service;

import com.example.cooperativa.common.contract.CooperativeEvent;
import com.example.cooperativa.common.contract.EventPayload;
import com.example.cooperativa.common.messaging.ProcessedEventRepository;
import com.example.cooperativa.indicadores.domain.IndicadorExecucaoEntity;
import com.example.cooperativa.indicadores.domain.IndicadorExecucaoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Locale;

@Service
public class IndicadoresService {

    private static final Logger log = LoggerFactory.getLogger(IndicadoresService.class);
    private static final String CONSUMER_NAME = "indicadores";

    private static final String UPSERT_TOTAL_SQL = """
            INSERT INTO indicadores_totais (produto, peso_total_kg, total_cargas, atualizado_em)
            VALUES (?, ?, 1, ?)
            ON CONFLICT (produto) DO UPDATE
            SET peso_total_kg = indicadores_totais.peso_total_kg + EXCLUDED.peso_total_kg,
                total_cargas = indicadores_totais.total_cargas + 1,
                atualizado_em = EXCLUDED.atualizado_em
            """;

    private final ProcessedEventRepository processedEventRepository;
    private final IndicadorExecucaoRepository execucaoRepository;
    private final JdbcTemplate jdbcTemplate;
    private final String instanceId;

    public IndicadoresService(
            ProcessedEventRepository processedEventRepository,
            IndicadorExecucaoRepository execucaoRepository,
            JdbcTemplate jdbcTemplate,
            @Value("${coop.instance-id:indicadores-1}") String instanceId
    ) {
        this.processedEventRepository = processedEventRepository;
        this.execucaoRepository = execucaoRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.instanceId = instanceId;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void processar(CooperativeEvent event) {
        EventPayload payload = event.payload();
        if (payload == null || payload.pesoLiquidoKg() == null) {
            throw new IllegalArgumentException("Evento de carga recebida sem pesoLiquidoKg: " + event.eventId());
        }

        String produto = (payload.produto() != null && !payload.produto().isBlank())
                ? payload.produto().trim().toUpperCase(Locale.ROOT)
                : "NAO_INFORMADO";
        String lote = (payload.lote() != null && !payload.lote().isBlank())
                ? payload.lote().trim()
                : "SEM_LOTE";
        BigDecimal peso = payload.pesoLiquidoKg();
        Instant now = Instant.now();

        boolean firstTime = processedEventRepository.tryClaimEvent(CONSUMER_NAME, event.eventId(), instanceId);
        if (!firstTime) {
            execucaoRepository.save(new IndicadorExecucaoEntity(
                    event.eventId(),
                    instanceId,
                    lote,
                    produto,
                    peso,
                    "DUPLICADO_IGNORADO",
                    now
            ));
            log.warn("[Indicadores][{}] DUPLICIDADE DETECTADA! eventId={} já processado anteriormente. Peso {} kg NÃO somado novamente.",
                    instanceId, event.eventId(), peso);
            return;
        }

        jdbcTemplate.update(UPSERT_TOTAL_SQL, produto, peso, Timestamp.from(now));
        execucaoRepository.save(new IndicadorExecucaoEntity(
                event.eventId(),
                instanceId,
                lote,
                produto,
                peso,
                "PROCESSADO",
                now
        ));

        log.info("[Indicadores][{}] Somado +{} kg ao produto {} (lote={}, eventId={})",
                instanceId, peso, produto, lote, event.eventId());
    }
}
