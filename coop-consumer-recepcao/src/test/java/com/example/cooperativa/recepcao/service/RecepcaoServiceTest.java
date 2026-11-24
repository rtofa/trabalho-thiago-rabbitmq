package com.example.cooperativa.recepcao.service;

import com.example.cooperativa.common.contract.CooperativeEvent;
import com.example.cooperativa.common.contract.EventPayload;
import com.example.cooperativa.common.contract.EventTypes;
import com.example.cooperativa.common.messaging.ProcessedEventRepository;
import com.example.cooperativa.recepcao.domain.TicketPesagemEntity;
import com.example.cooperativa.recepcao.domain.TicketPesagemRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecepcaoServiceTest {

    @Test
    void deveRegistrarTicketDePesagemEDescartarDuplicata() {
        ProcessedEventRepository processedRepo = mock(ProcessedEventRepository.class);
        TicketPesagemRepository ticketRepo = mock(TicketPesagemRepository.class);
        RecepcaoService service = new RecepcaoService(processedRepo, ticketRepo);

        CooperativeEvent event = new CooperativeEvent(
                "evt-rec-1", EventTypes.CARGA_RECEBIDA, EventTypes.RK_CARGA_RECEBIDA, Instant.now(),
                new EventPayload("LOTE-REC-1", "Cooperado Silva", "TRIGO", new BigDecimal("12000.50"), null, Map.of())
        );

        when(processedRepo.tryClaimEvent("recepcao", "evt-rec-1", "recepcao-1"))
                .thenReturn(true)
                .thenReturn(false);

        service.processar(event);
        service.processar(event);

        ArgumentCaptor<TicketPesagemEntity> captor = ArgumentCaptor.forClass(TicketPesagemEntity.class);
        verify(ticketRepo, org.mockito.Mockito.times(1)).save(captor.capture());
        assertThat(captor.getValue().getLote()).isEqualTo("LOTE-REC-1");
        assertThat(captor.getValue().getPesoLiquidoKg()).isEqualByComparingTo("12000.50");
        verify(ticketRepo, never()).deleteAll();
    }
}
