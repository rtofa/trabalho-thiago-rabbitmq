package com.example.cooperativa.indicadores.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "indicadores_totais")
public class IndicadorProdutoEntity {

    @Id
    @Column(name = "produto", length = 80, nullable = false)
    private String produto;

    @Column(name = "peso_total_kg", precision = 16, scale = 3, nullable = false)
    private BigDecimal pesoTotalKg;

    @Column(name = "total_cargas", nullable = false)
    private long totalCargas;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected IndicadorProdutoEntity() {
    }

    public String getProduto() {
        return produto;
    }

    public BigDecimal getPesoTotalKg() {
        return pesoTotalKg;
    }

    public long getTotalCargas() {
        return totalCargas;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }
}
