package com.example.cooperativa.auditoria.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuditoriaEventoRepository
        extends JpaRepository<AuditoriaEventoEntity, Long>, JpaSpecificationExecutor<AuditoriaEventoEntity> {
}
