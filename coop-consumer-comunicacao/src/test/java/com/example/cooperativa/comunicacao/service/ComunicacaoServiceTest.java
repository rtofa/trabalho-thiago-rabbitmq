package com.example.cooperativa.comunicacao.service;

import com.example.cooperativa.common.contract.CooperativeEvent;
import com.example.cooperativa.common.contract.EventPayload;
import com.example.cooperativa.common.contract.EventTypes;
import com.example.cooperativa.common.messaging.ProcessedEventRepository;
import com.example.cooperativa.comunicacao.domain.AvisoCooperadoEntity;
import com.example.cooperativa.comunicacao.domain.AvisoCooperadoRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ComunicacaoServiceTest {

    @Test
    void deveGerarAvisosCorretosParaRecebimentoRecusaEReprovacao() {
        ProcessedEventRepository processedRepo = mock(ProcessedEventRepository.class);
        AvisoCooperadoRepository avisoRepo = mock(AvisoCooperadoRepository.class);
        ComunicacaoService service = new ComunicacaoService(processedRepo, avisoRepo);

        when(processedRepo.tryClaimEvent("comunicacao", "e1", "comunicacao-1")).thenReturn(true);
        when(processedRepo.tryClaimEvent("comunicacao", "e2", "comunicacao-1")).thenReturn(true);
        when(processedRepo.tryClaimEvent("comunicacao", "e3", "comunicacao-1")).thenReturn(true);

        service.processar(new CooperativeEvent("e1", EventTypes.CARGA_RECEBIDA, EventTypes.RK_CARGA_RECEBIDA,
                Instant.now(), new EventPayload("L1", "João", "SOJA", new BigDecimal("1000"), null, Map.of())));
        service.processar(new CooperativeEvent("e2", EventTypes.CARGA_RECUSADA, EventTypes.RK_CARGA_RECUSADA,
                Instant.now(), new EventPayload("L2", "Maria", "MILHO", null, "Umidade alta", Map.of())));
        service.processar(new CooperativeEvent("e3", EventTypes.ANALISE_REPROVADA, EventTypes.RK_ANALISE_REPROVADA,
                Instant.now(), new EventPayload("L3", "Pedro", "CAFE", null, "Micotoxina", Map.of())));

        ArgumentCaptor<AvisoCooperadoEntity> captor = ArgumentCaptor.forClass(AvisoCooperadoEntity.class);
        verify(avisoRepo, times(3)).save(captor.capture());

        assertThat(captor.getAllValues().get(0).getTipoAviso()).isEqualTo("RECEBIMENTO");
        assertThat(captor.getAllValues().get(1).getTipoAviso()).isEqualTo("RECUSA_CARGA");
        assertThat(captor.getAllValues().get(1).getMotivo()).isEqualTo("Umidade alta");
        assertThat(captor.getAllValues().get(2).getTipoAviso()).isEqualTo("REPROVACAO_ANALISE");
    }
}
