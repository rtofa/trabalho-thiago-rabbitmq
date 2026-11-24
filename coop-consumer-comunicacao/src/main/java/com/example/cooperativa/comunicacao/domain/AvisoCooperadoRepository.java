package com.example.cooperativa.comunicacao.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AvisoCooperadoRepository extends JpaRepository<AvisoCooperadoEntity, Long> {

    List<AvisoCooperadoEntity> findAllByOrderByCreatedAtDesc();
}
