package com.example.cooperativa.recepcao.service;

import com.example.cooperativa.common.contract.CooperativeEvent;
import com.example.cooperativa.common.contract.EventPayload;
import com.example.cooperativa.common.messaging.ProcessedEventRepository;
import com.example.cooperativa.recepcao.domain.TicketPesagemEntity;
import com.example.cooperativa.recepcao.domain.TicketPesagemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class RecepcaoService {

    private static final Logger log = LoggerFactory.getLogger(RecepcaoService.class);
    private static final String CONSUMER_NAME = "recepcao";

    private final ProcessedEventRepository processedEventRepository;
    private final TicketPesagemRepository ticketRepository;

    public RecepcaoService(
            ProcessedEventRepository processedEventRepository,
            TicketPesagemRepository ticketRepository
    ) {
        this.processedEventRepository = processedEventRepository;
        this.ticketRepository = ticketRepository;
    }

    public void processar(CooperativeEvent event) {
        if (!processedEventRepository.tryClaimEvent(CONSUMER_NAME, event.eventId(), "recepcao-1")) {
            log.info("[Recepção] Evento duplicado ignorado: eventId={}", event.eventId());
            return;
        }

        EventPayload payload = event.payload();
        if (payload == null || payload.lote() == null || payload.pesoLiquidoKg() == null) {
            throw new IllegalArgumentException("Evento de carga recebida sem lote ou pesoLiquidoKg: " + event.eventId());
        }

        String produto = payload.produto() != null ? payload.produto() : "NAO_INFORMADO";
        BigDecimal peso = payload.pesoLiquidoKg();

        TicketPesagemEntity ticket = new TicketPesagemEntity(
                event.eventId(),
                payload.lote(),
                produto,
                payload.cooperado(),
                peso,
                event.occurredAt() != null ? event.occurredAt() : Instant.now(),
                Instant.now()
        );
        ticketRepository.save(ticket);
        log.info("[Recepção] Ticket de pesagem registrado: lote={}, produto={}, pesoLiquidoKg={}, eventId={}",
                ticket.getLote(), ticket.getProduto(), ticket.getPesoLiquidoKg(), event.eventId());
    }
}
