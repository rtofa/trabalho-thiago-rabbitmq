package com.example.cooperativa.auditoria.service;

import com.example.cooperativa.auditoria.domain.AuditoriaEventoEntity;
import com.example.cooperativa.auditoria.domain.AuditoriaEventoRepository;
import com.example.cooperativa.common.contract.CooperativeEvent;
import com.example.cooperativa.common.contract.EventPayload;
import com.example.cooperativa.common.contract.EventTypes;
import com.example.cooperativa.common.messaging.ProcessedEventRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditoriaServiceTest {

    @Test
    void deveRegistrarEventoAuditoriaEIncrementarDeliveryCountEmDuplicata() {
        ProcessedEventRepository processedRepo = mock(ProcessedEventRepository.class);
        AuditoriaEventoRepository auditRepo = mock(AuditoriaEventoRepository.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        JsonMapper jsonMapper = JsonMapper.builder().findAndAddModules().build();

        AuditoriaService service = new AuditoriaService(processedRepo, auditRepo, jdbcTemplate, jsonMapper);

        CooperativeEvent event = new CooperativeEvent(
                "evt-aud-1", EventTypes.CARGA_RECEBIDA, EventTypes.RK_CARGA_RECEBIDA,
                Instant.parse("2026-11-24T13:00:00Z"),
                new EventPayload("LOTE-1", "Fazenda A", "SOJA", new BigDecimal("1000"), null, Map.of())
        );

        when(processedRepo.tryClaimEvent("auditoria", "evt-aud-1", "auditoria-1"))
                .thenReturn(true)
                .thenReturn(false);

        service.processar(event);
        service.processar(event);

        ArgumentCaptor<AuditoriaEventoEntity> captor = ArgumentCaptor.forClass(AuditoriaEventoEntity.class);
        verify(auditRepo, times(1)).save(captor.capture());
        assertThat(captor.getValue().getEventId()).isEqualTo("evt-aud-1");
        assertThat(captor.getValue().getDeliveryCount()).isEqualTo(1);

        verify(jdbcTemplate, times(1)).update(anyString(), any(), eq("evt-aud-1"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void deveConsultarHistoricoComFiltros() {
        ProcessedEventRepository processedRepo = mock(ProcessedEventRepository.class);
        AuditoriaEventoRepository auditRepo = mock(AuditoriaEventoRepository.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        JsonMapper jsonMapper = JsonMapper.builder().findAndAddModules().build();
        AuditoriaService service = new AuditoriaService(processedRepo, auditRepo, jdbcTemplate, jsonMapper);

        when(auditRepo.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());

        List<AuditoriaEventoEntity> result = service.consultar(
                "evt-1", "CARGA_RECEBIDA",
                Instant.parse("2026-11-24T00:00:00Z"),
                Instant.parse("2026-11-24T23:59:59Z")
        );

        assertThat(result).isEmpty();
        verify(auditRepo).findAll(any(Specification.class), any(Sort.class));
    }
}
