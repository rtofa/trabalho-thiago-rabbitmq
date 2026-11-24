package com.example.cooperativa.auditoria.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "auditoria_eventos")
public class AuditoriaEventoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", length = 100, nullable = false, unique = true)
    private String eventId;

    @Column(name = "event_type", length = 100, nullable = false)
    private String eventType;

    @Column(name = "routing_key", length = 120, nullable = false)
    private String routingKey;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "payload_json", columnDefinition = "TEXT", nullable = false)
    private String payloadJson;

    @Column(name = "delivery_count", nullable = false)
    private int deliveryCount;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    protected AuditoriaEventoEntity() {
    }

    public AuditoriaEventoEntity(
            String eventId,
            String eventType,
            String routingKey,
            Instant occurredAt,
            String payloadJson,
            int deliveryCount,
            Instant firstSeenAt,
            Instant lastSeenAt
    ) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.routingKey = routingKey;
        this.occurredAt = occurredAt;
        this.payloadJson = payloadJson;
        this.deliveryCount = deliveryCount;
        this.firstSeenAt = firstSeenAt;
        this.lastSeenAt = lastSeenAt;
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getRoutingKey() {
        return routingKey;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public int getDeliveryCount() {
        return deliveryCount;
    }

    public Instant getFirstSeenAt() {
        return firstSeenAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }
}
