package com.example.cooperativa.producer.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PublishedEventRepository extends JpaRepository<PublishedEventEntity, String> {

    List<PublishedEventEntity> findAllByOrderByCreatedAtDesc();
}
