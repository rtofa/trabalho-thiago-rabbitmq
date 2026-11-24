package com.example.cooperativa.indicadores.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IndicadorProdutoRepository extends JpaRepository<IndicadorProdutoEntity, String> {

    List<IndicadorProdutoEntity> findAllByOrderByProdutoAsc();
}
