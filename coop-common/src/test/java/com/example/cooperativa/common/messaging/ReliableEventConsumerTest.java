package com.example.cooperativa.common.messaging;

import com.example.cooperativa.common.contract.CooperativeEvent;
import com.example.cooperativa.common.contract.EventPayload;
import com.example.cooperativa.common.contract.EventTypes;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ReliableEventConsumerTest {

    @Test
    void deveConfirmarAckSomenteAposCommitDaTransacao() throws Exception {
        TransactionTemplate txTemplate = mock(TransactionTemplate.class);
        Channel channel = mock(Channel.class);
        AtomicBoolean workExecuted = new AtomicBoolean(false);

        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(new SimpleTransactionStatus());
            return null;
        }).when(txTemplate).executeWithoutResult(any());

        ReliableEventConsumer consumer = new ReliableEventConsumer(txTemplate);
        CooperativeEvent event = sampleEvent("evt-001");

        consumer.handle("Recepção", event, channel, 42L, () -> workExecuted.set(true));

        assertThat(workExecuted).isTrue();
        InOrder order = inOrder(txTemplate, channel);
        order.verify(txTemplate).executeWithoutResult(any());
        order.verify(channel).basicAck(42L, false);
        verify(channel, never()).basicNack(42L, false, true);
        verify(channel, never()).basicNack(42L, false, false);
    }

    @Test
    void deveReenfileirarQuandoOcorrerFalhaTransitoriaDeBanco() throws Exception {
        TransactionTemplate txTemplate = mock(TransactionTemplate.class);
        Channel channel = mock(Channel.class);

        doAnswer(invocation -> {
            throw new RecoverableDataAccessException("Banco temporariamente indisponível");
        }).when(txTemplate).executeWithoutResult(any());

        ReliableEventConsumer consumer = new ReliableEventConsumer(txTemplate);
        consumer.handle("Comunicação", sampleEvent("evt-002"), channel, 7L, () -> {});

        verify(channel, never()).basicAck(7L, false);
        verify(channel).basicNack(7L, false, true);
    }

    @Test
    void deveEnviarParaDlqQuandoFalhaNaoForRecuperavel() throws Exception {
        TransactionTemplate txTemplate = mock(TransactionTemplate.class);
        Channel channel = mock(Channel.class);

        doAnswer(invocation -> {
            throw new IllegalArgumentException("Payload inválido");
        }).when(txTemplate).executeWithoutResult(any());

        ReliableEventConsumer consumer = new ReliableEventConsumer(txTemplate);
        consumer.handle("Recepção", sampleEvent("evt-003"), channel, 9L, () -> {});

        verify(channel, never()).basicAck(9L, false);
        verify(channel).basicNack(9L, false, false);
    }

    @Test
    void deveEnviarParaDlqQuandoEventIdEstiverAusente() throws Exception {
        TransactionTemplate txTemplate = mock(TransactionTemplate.class);
        Channel channel = mock(Channel.class);
        ReliableEventConsumer consumer = new ReliableEventConsumer(txTemplate);

        CooperativeEvent invalid = new CooperativeEvent(" ", EventTypes.CARGA_RECEBIDA,
                EventTypes.RK_CARGA_RECEBIDA, Instant.now(), null);

        consumer.handle("Recepção", invalid, channel, 11L, () -> {});

        verify(txTemplate, never()).executeWithoutResult(any());
        verify(channel).basicNack(11L, false, false);
    }

    private static CooperativeEvent sampleEvent(String eventId) {
        return new CooperativeEvent(
                eventId,
                EventTypes.CARGA_RECEBIDA,
                EventTypes.RK_CARGA_RECEBIDA,
                Instant.parse("2026-11-24T13:30:00Z"),
                new EventPayload("LOTE-1", "Fazenda A", "SOJA", new BigDecimal("15000.00"), null, Map.of())
        );
    }
}
