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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CooperativeEventPublisherServiceTest {

    private RabbitTemplate rabbitTemplate;
    private PublishedEventRepository repository;
    private JdbcTemplate jdbcTemplate;
    private JsonMapper jsonMapper;
    private CooperativeEventPublisherService service;

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
        repository = mock(PublishedEventRepository.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        jsonMapper = JsonMapper.builder().findAndAddModules().build();
        service = new CooperativeEventPublisherService(rabbitTemplate, repository, jdbcTemplate, jsonMapper);

        doAnswer(invocation -> {
            CorrelationData cd = invocation.getArgument(4);
            cd.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).convertAndSend(
                anyString(), anyString(), any(Object.class), any(MessagePostProcessor.class), any(CorrelationData.class));
    }

    @Test
    void devePublicarQuatroEventosObrigatoriosComRoutingKeysCorretas() {
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any())).thenReturn(1);

        PublishResponse r1 = service.publishCargaRecebida(null);
        PublishResponse r2 = service.publishCargaRecusada(null);
        PublishResponse r3 = service.publishAnaliseAprovada(null);
        PublishResponse r4 = service.publishAnaliseReprovada(null);

        assertThat(r1.event().eventType()).isEqualTo(EventTypes.CARGA_RECEBIDA);
        assertThat(r1.event().routingKey()).isEqualTo(EventTypes.RK_CARGA_RECEBIDA);
        assertThat(r1.event().payload().pesoLiquidoKg()).isNotNull();
        assertThat(r1.brokerConfirmed()).isTrue();

        assertThat(r2.event().eventType()).isEqualTo(EventTypes.CARGA_RECUSADA);
        assertThat(r2.event().routingKey()).isEqualTo(EventTypes.RK_CARGA_RECUSADA);
        assertThat(r2.event().payload().motivo()).isNotBlank();

        assertThat(r3.event().eventType()).isEqualTo(EventTypes.ANALISE_APROVADA);
        assertThat(r3.event().routingKey()).isEqualTo(EventTypes.RK_ANALISE_APROVADA);

        assertThat(r4.event().eventType()).isEqualTo(EventTypes.ANALISE_REPROVADA);
        assertThat(r4.event().routingKey()).isEqualTo(EventTypes.RK_ANALISE_REPROVADA);
        assertThat(r4.event().payload().motivo()).isNotBlank();
    }

    @Test
    void deveRepublicarMesmoEventIdPreservandoContratoOriginal() {
        EventPayload originalPayload = new EventPayload(
                "LOTE-DUP-01", "Fazenda Duplicada", "MILHO", new BigDecimal("10000.00"), null, Map.of());
        Instant occurredAt = Instant.parse("2026-11-24T12:00:00Z");
        String payloadJson = jsonMapper.writeValueAsString(originalPayload);

        when(repository.findById("evt-dup-123")).thenReturn(Optional.of(
                new PublishedEventEntity("evt-dup-123", EventTypes.CARGA_RECEBIDA,
                        EventTypes.RK_CARGA_RECEBIDA, occurredAt, payloadJson, Instant.now())
        ));
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any())).thenReturn(0);

        PublishResponse replay = service.republishByEventId("evt-dup-123");

        assertThat(replay.republished()).isTrue();
        assertThat(replay.event().eventId()).isEqualTo("evt-dup-123");
        assertThat(replay.event().occurredAt()).isEqualTo(occurredAt);
        assertThat(replay.event().payload().pesoLiquidoKg()).isEqualByComparingTo("10000.00");

        verify(rabbitTemplate).convertAndSend(
                eq(CooperativeTopologyConstants.EXCHANGE_EVENTS),
                eq(EventTypes.RK_CARGA_RECEBIDA),
                any(CooperativeEvent.class),
                any(MessagePostProcessor.class),
                any(CorrelationData.class)
        );
    }

    @Test
    void devePublicarEventoCustomizadoParaFamiliaOuSemRegra() {
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any())).thenReturn(1);

        PublishResponse custom = service.publishCustom(new CustomEventRequest(
                "evt-new-1",
                "ANALISE_REAVALIADA",
                "analise.reavaliada",
                null,
                new EventPayload("LOTE-9", "Sítio Sol", "SOJA", null, "Contraprova", Map.of())
        ));

        assertThat(custom.event().eventId()).isEqualTo("evt-new-1");
        assertThat(custom.event().routingKey()).isEqualTo("analise.reavaliada");
        assertThat(custom.event().eventType()).isEqualTo("ANALISE_REAVALIADA");
    }

    @Test
    void deveNormalizarProdutoEmMaiusculasNoPayload() {
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any())).thenReturn(1);

        PublishResponse resp = service.publishCargaRecebida(new TriggerEventRequest(
                "evt-norm-1", "LOTE-N", "Coop", " soja ", new BigDecimal("5000"), null, null, null
        ));

        assertThat(resp.event().payload().produto()).isEqualTo("SOJA");
    }
}
