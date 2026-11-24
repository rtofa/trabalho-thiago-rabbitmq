package com.example.cooperativa.comunicacao.service;

import com.example.cooperativa.common.contract.CooperativeEvent;
import com.example.cooperativa.common.contract.EventPayload;
import com.example.cooperativa.common.contract.EventTypes;
import com.example.cooperativa.common.messaging.ProcessedEventRepository;
import com.example.cooperativa.comunicacao.domain.AvisoCooperadoEntity;
import com.example.cooperativa.comunicacao.domain.AvisoCooperadoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class ComunicacaoService {

    private static final Logger log = LoggerFactory.getLogger(ComunicacaoService.class);
    private static final String CONSUMER_NAME = "comunicacao";

    private final ProcessedEventRepository processedEventRepository;
    private final AvisoCooperadoRepository avisoRepository;

    public ComunicacaoService(
            ProcessedEventRepository processedEventRepository,
            AvisoCooperadoRepository avisoRepository
    ) {
        this.processedEventRepository = processedEventRepository;
        this.avisoRepository = avisoRepository;
    }

    public void processar(CooperativeEvent event) {
        if (!processedEventRepository.tryClaimEvent(CONSUMER_NAME, event.eventId(), "comunicacao-1")) {
            log.info("[Comunicação] Evento duplicado ignorado: eventId={}", event.eventId());
            return;
        }

        EventPayload payload = event.payload();
        String lote = payload != null && payload.lote() != null ? payload.lote() : "SEM_LOTE";
        String cooperado = payload != null && payload.cooperado() != null ? payload.cooperado() : "Cooperado não informado";
        String produto = payload != null ? payload.produto() : null;
        String motivo = payload != null ? payload.motivo() : null;

        String tipoAviso;
        String mensagem;
        if (EventTypes.CARGA_RECEBIDA.equals(event.eventType()) || EventTypes.RK_CARGA_RECEBIDA.equals(event.routingKey())) {
            tipoAviso = "RECEBIMENTO";
            mensagem = String.format("Prezado(a) %s, sua carga (%s, lote %s) foi recebida com peso líquido de %s kg.",
                    cooperado, produto != null ? produto : "produto", lote,
                    payload != null && payload.pesoLiquidoKg() != null ? payload.pesoLiquidoKg() : "0");
        } else if (EventTypes.CARGA_RECUSADA.equals(event.eventType()) || EventTypes.RK_CARGA_RECUSADA.equals(event.routingKey())) {
            tipoAviso = "RECUSA_CARGA";
            mensagem = String.format("Prezado(a) %s, a carga do lote %s foi recusada na recepção. Motivo: %s.",
                    cooperado, lote, motivo != null ? motivo : "não especificado");
        } else if (EventTypes.ANALISE_REPROVADA.equals(event.eventType()) || EventTypes.RK_ANALISE_REPROVADA.equals(event.routingKey())) {
            tipoAviso = "REPROVACAO_ANALISE";
            mensagem = String.format("Prezado(a) %s, a análise de qualidade do lote %s foi reprovada. Motivo: %s.",
                    cooperado, lote, motivo != null ? motivo : "não especificado");
        } else {
            tipoAviso = event.eventType() != null ? event.eventType() : "AVISO_GERAL";
            mensagem = String.format("Notificação referente ao evento %s para o lote %s.", tipoAviso, lote);
        }

        AvisoCooperadoEntity aviso = new AvisoCooperadoEntity(
                event.eventId(),
                tipoAviso,
                lote,
                cooperado,
                produto,
                payload != null ? payload.pesoLiquidoKg() : null,
                motivo,
                mensagem,
                event.occurredAt() != null ? event.occurredAt() : Instant.now(),
                Instant.now()
        );
        avisoRepository.save(aviso);
        log.info("[Comunicação] Aviso {} registrado para {}: lote={}, eventId={}",
                tipoAviso, cooperado, lote, event.eventId());
    }
}
