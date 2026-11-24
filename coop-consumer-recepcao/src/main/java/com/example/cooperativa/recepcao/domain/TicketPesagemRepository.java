package com.example.cooperativa.recepcao.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketPesagemRepository extends JpaRepository<TicketPesagemEntity, Long> {

    List<TicketPesagemEntity> findAllByOrderByCreatedAtDesc();
}
