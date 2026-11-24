package com.example.cooperativa.comunicacao.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "comunicacao_avisos")
public class AvisoCooperadoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", length = 100, nullable = false, unique = true)
    private String eventId;

    @Column(name = "tipo_aviso", length = 60, nullable = false)
    private String tipoAviso;

    @Column(name = "lote", length = 80, nullable = false)
    private String lote;

    @Column(name = "cooperado", length = 150, nullable = false)
    private String cooperado;

    @Column(name = "produto", length = 80)
    private String produto;

    @Column(name = "peso_liquido_kg", precision = 14, scale = 3)
    private BigDecimal pesoLiquidoKg;

    @Column(name = "motivo", columnDefinition = "TEXT")
    private String motivo;

    @Column(name = "mensagem", columnDefinition = "TEXT", nullable = false)
    private String mensagem;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AvisoCooperadoEntity() {
    }

    public AvisoCooperadoEntity(
            String eventId,
            String tipoAviso,
            String lote,
            String cooperado,
            String produto,
            BigDecimal pesoLiquidoKg,
            String motivo,
            String mensagem,
            Instant occurredAt,
            Instant createdAt
    ) {
        this.eventId = eventId;
        this.tipoAviso = tipoAviso;
        this.lote = lote;
        this.cooperado = cooperado;
        this.produto = produto;
        this.pesoLiquidoKg = pesoLiquidoKg;
        this.motivo = motivo;
        this.mensagem = mensagem;
        this.occurredAt = occurredAt;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getTipoAviso() {
        return tipoAviso;
    }

    public String getLote() {
        return lote;
    }

    public String getCooperado() {
        return cooperado;
    }

    public String getProduto() {
        return produto;
    }

    public BigDecimal getPesoLiquidoKg() {
        return pesoLiquidoKg;
    }

    public String getMotivo() {
        return motivo;
    }

    public String getMensagem() {
        return mensagem;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
