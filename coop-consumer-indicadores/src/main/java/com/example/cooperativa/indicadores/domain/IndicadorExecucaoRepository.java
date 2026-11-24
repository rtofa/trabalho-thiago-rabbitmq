package com.example.cooperativa.indicadores.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IndicadorExecucaoRepository extends JpaRepository<IndicadorExecucaoEntity, Long> {

    List<IndicadorExecucaoEntity> findAllByOrderByIdDesc();
}
