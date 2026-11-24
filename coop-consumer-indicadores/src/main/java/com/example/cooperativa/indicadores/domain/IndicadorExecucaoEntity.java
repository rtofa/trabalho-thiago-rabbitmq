package com.example.cooperativa.indicadores.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "indicadores_execucoes")
public class IndicadorExecucaoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", length = 100, nullable = false)
    private String eventId;

    @Column(name = "instance_id", length = 100, nullable = false)
    private String instanceId;

    @Column(name = "lote", length = 80, nullable = false)
    private String lote;

    @Column(name = "produto", length = 80, nullable = false)
    private String produto;

    @Column(name = "peso_liquido_kg", precision = 14, scale = 3, nullable = false)
    private BigDecimal pesoLiquidoKg;

    @Column(name = "status", length = 40, nullable = false)
    private String status;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected IndicadorExecucaoEntity() {
    }

    public IndicadorExecucaoEntity(
            String eventId,
            String instanceId,
            String lote,
            String produto,
            BigDecimal pesoLiquidoKg,
            String status,
            Instant processedAt
    ) {
        this.eventId = eventId;
        this.instanceId = instanceId;
        this.lote = lote;
        this.produto = produto;
        this.pesoLiquidoKg = pesoLiquidoKg;
        this.status = status;
        this.processedAt = processedAt;
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public String getLote() {
        return lote;
    }

    public String getProduto() {
        return produto;
    }

    public BigDecimal getPesoLiquidoKg() {
        return pesoLiquidoKg;
    }

    public String getStatus() {
        return status;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
