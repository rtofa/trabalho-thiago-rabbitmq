package com.example.cooperativa.qualidade.service;

import com.example.cooperativa.common.contract.CooperativeEvent;
import com.example.cooperativa.common.contract.EventPayload;
import com.example.cooperativa.common.contract.EventTypes;
import com.example.cooperativa.common.messaging.ProcessedEventRepository;
import com.example.cooperativa.qualidade.domain.ResultadoAnaliseEntity;
import com.example.cooperativa.qualidade.domain.ResultadoAnaliseRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QualidadeServiceTest {

    @Test
    void deveRegistrarAnaliseAprovadaEReprovadaENovoEventoDaFamilia() {
        ProcessedEventRepository processedRepo = mock(ProcessedEventRepository.class);
        ResultadoAnaliseRepository resultadoRepo = mock(ResultadoAnaliseRepository.class);
        JsonMapper jsonMapper = JsonMapper.builder().findAndAddModules().build();

        QualidadeService service = new QualidadeService(processedRepo, resultadoRepo, jsonMapper);

        when(processedRepo.tryClaimEvent("qualidade", "evt-q1", "qualidade-1")).thenReturn(true);
        service.processar(new CooperativeEvent(
                "evt-q1", EventTypes.ANALISE_REPROVADA, EventTypes.RK_ANALISE_REPROVADA, Instant.now(),
                new EventPayload("LOTE-50", "Coop A", "SOJA", null, "Umidade excessiva", Map.of())
        ));

        when(processedRepo.tryClaimEvent("qualidade", "evt-q2", "qualidade-1")).thenReturn(true);
        service.processar(new CooperativeEvent(
                "evt-q2", "ANALISE_REAVALIADA", "analise.reavaliada", Instant.now(),
                new EventPayload("LOTE-50", "Coop A", "SOJA", null, "Contraprova em laboratório",
                        Map.of("analista", "Maria"))
        ));

        ArgumentCaptor<ResultadoAnaliseEntity> captor = ArgumentCaptor.forClass(ResultadoAnaliseEntity.class);
        verify(resultadoRepo, org.mockito.Mockito.times(2)).save(captor.capture());

        assertThat(captor.getAllValues().get(0).getResultado()).isEqualTo("REPROVADA");
        assertThat(captor.getAllValues().get(0).getMotivo()).isEqualTo("Umidade excessiva");
        assertThat(captor.getAllValues().get(1).getResultado()).isEqualTo("REAVALIADA");
        assertThat(captor.getAllValues().get(1).getRoutingKey()).isEqualTo("analise.reavaliada");
    }

    @Test
    void deveIgnorarEventoDuplicadoNaQualidade() {
        ProcessedEventRepository processedRepo = mock(ProcessedEventRepository.class);
        ResultadoAnaliseRepository resultadoRepo = mock(ResultadoAnaliseRepository.class);
        JsonMapper jsonMapper = JsonMapper.builder().findAndAddModules().build();
        QualidadeService service = new QualidadeService(processedRepo, resultadoRepo, jsonMapper);

        when(processedRepo.tryClaimEvent("qualidade", "evt-dup", "qualidade-1")).thenReturn(false);

        service.processar(new CooperativeEvent(
                "evt-dup", EventTypes.ANALISE_APROVADA, EventTypes.RK_ANALISE_APROVADA, Instant.now(),
                new EventPayload("LOTE-1", "Coop", "MILHO", null, null, Map.of())
        ));

        verify(resultadoRepo, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
