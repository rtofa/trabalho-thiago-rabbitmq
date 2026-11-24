package com.example.cooperativa.qualidade.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResultadoAnaliseRepository extends JpaRepository<ResultadoAnaliseEntity, Long> {

    List<ResultadoAnaliseEntity> findAllByOrderByCreatedAtDesc();
}
