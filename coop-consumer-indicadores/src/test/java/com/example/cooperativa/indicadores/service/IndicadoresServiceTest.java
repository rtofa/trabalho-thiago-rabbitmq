package com.example.cooperativa.indicadores.service;

import com.example.cooperativa.common.contract.CooperativeEvent;
import com.example.cooperativa.common.contract.EventPayload;
import com.example.cooperativa.common.contract.EventTypes;
import com.example.cooperativa.common.messaging.ProcessedEventRepository;
import com.example.cooperativa.indicadores.domain.IndicadorExecucaoEntity;
import com.example.cooperativa.indicadores.domain.IndicadorExecucaoRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IndicadoresServiceTest {

    @Test
    void deveSomarPesoUmaUnicaVezQuandoEventoForRepublicadoEntreInstancias() {
        ProcessedEventRepository processedRepo = mock(ProcessedEventRepository.class);
        IndicadorExecucaoRepository execucaoRepo = mock(IndicadorExecucaoRepository.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        IndicadoresService instancia1 = new IndicadoresService(processedRepo, execucaoRepo, jdbcTemplate, "indicadores-1");
        IndicadoresService instancia2 = new IndicadoresService(processedRepo, execucaoRepo, jdbcTemplate, "indicadores-2");

        CooperativeEvent event = new CooperativeEvent(
                "evt-idemp-1",
                EventTypes.CARGA_RECEBIDA,
                EventTypes.RK_CARGA_RECEBIDA,
                Instant.now(),
                new EventPayload("LOTE-100", "Fazenda Azul", "SOJA", new BigDecimal("18500.00"), null, Map.of())
        );

        when(processedRepo.tryClaimEvent("indicadores", "evt-idemp-1", "indicadores-1")).thenReturn(true);
        when(processedRepo.tryClaimEvent("indicadores", "evt-idemp-1", "indicadores-2")).thenReturn(false);

        instancia1.processar(event);
        instancia2.processar(event);

        // O upsert em indicadores_totais ocorre estritamente 1 vez!
        verify(jdbcTemplate, times(1)).update(anyString(), eq("SOJA"), eq(new BigDecimal("18500.00")), any());

        ArgumentCaptor<IndicadorExecucaoEntity> captor = ArgumentCaptor.forClass(IndicadorExecucaoEntity.class);
        verify(execucaoRepo, times(2)).save(captor.capture());

        assertThat(captor.getAllValues().get(0).getInstanceId()).isEqualTo("indicadores-1");
        assertThat(captor.getAllValues().get(0).getStatus()).isEqualTo("PROCESSADO");

        assertThat(captor.getAllValues().get(1).getInstanceId()).isEqualTo("indicadores-2");
        assertThat(captor.getAllValues().get(1).getStatus()).isEqualTo("DUPLICADO_IGNORADO");
    }

    @Test
    void deveRejeitarEventoSemPesoLiquido() {
        ProcessedEventRepository processedRepo = mock(ProcessedEventRepository.class);
        IndicadorExecucaoRepository execucaoRepo = mock(IndicadorExecucaoRepository.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        IndicadoresService service = new IndicadoresService(processedRepo, execucaoRepo, jdbcTemplate, "indicadores-1");

        CooperativeEvent invalid = new CooperativeEvent(
                "evt-bad", EventTypes.CARGA_RECEBIDA, EventTypes.RK_CARGA_RECEBIDA, Instant.now(),
                new EventPayload("LOTE-1", "Coop", "SOJA", null, null, Map.of())
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.processar(invalid))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(jdbcTemplate);
    }
}
