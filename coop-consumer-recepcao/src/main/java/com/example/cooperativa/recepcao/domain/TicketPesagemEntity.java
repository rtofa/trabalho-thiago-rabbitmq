package com.example.cooperativa.recepcao.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "recepcao_tickets")
public class TicketPesagemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", length = 100, nullable = false, unique = true)
    private String eventId;

    @Column(name = "lote", length = 80, nullable = false)
    private String lote;

    @Column(name = "produto", length = 80, nullable = false)
    private String produto;

    @Column(name = "cooperado", length = 150)
    private String cooperado;

    @Column(name = "peso_liquido_kg", precision = 14, scale = 3, nullable = false)
    private BigDecimal pesoLiquidoKg;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TicketPesagemEntity() {
    }

    public TicketPesagemEntity(
            String eventId,
            String lote,
            String produto,
            String cooperado,
            BigDecimal pesoLiquidoKg,
            Instant occurredAt,
            Instant createdAt
    ) {
        this.eventId = eventId;
        this.lote = lote;
        this.produto = produto;
        this.cooperado = cooperado;
        this.pesoLiquidoKg = pesoLiquidoKg;
        this.occurredAt = occurredAt;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getLote() {
        return lote;
    }

    public String getProduto() {
        return produto;
    }

    public String getCooperado() {
        return cooperado;
    }

    public BigDecimal getPesoLiquidoKg() {
        return pesoLiquidoKg;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
