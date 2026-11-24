package com.example.cooperativa.qualidade.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "qualidade_resultados")
public class ResultadoAnaliseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", length = 100, nullable = false, unique = true)
    private String eventId;

    @Column(name = "event_type", length = 100, nullable = false)
    private String eventType;

    @Column(name = "routing_key", length = 120, nullable = false)
    private String routingKey;

    @Column(name = "lote", length = 80, nullable = false)
    private String lote;

    @Column(name = "produto", length = 80)
    private String produto;

    @Column(name = "resultado", length = 80, nullable = false)
    private String resultado;

    @Column(name = "motivo", columnDefinition = "TEXT")
    private String motivo;

    @Column(name = "detalhes_json", columnDefinition = "TEXT")
    private String detalhesJson;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ResultadoAnaliseEntity() {
    }

    public ResultadoAnaliseEntity(
            String eventId,
            String eventType,
            String routingKey,
            String lote,
            String produto,
            String resultado,
            String motivo,
            String detalhesJson,
            Instant occurredAt,
            Instant createdAt
    ) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.routingKey = routingKey;
        this.lote = lote;
        this.produto = produto;
        this.resultado = resultado;
        this.motivo = motivo;
        this.detalhesJson = detalhesJson;
        this.occurredAt = occurredAt;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getRoutingKey() {
        return routingKey;
    }

    public String getLote() {
        return lote;
    }

    public String getProduto() {
        return produto;
    }

    public String getResultado() {
        return resultado;
    }

    public String getMotivo() {
        return motivo;
    }

    public String getDetalhesJson() {
        return detalhesJson;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
