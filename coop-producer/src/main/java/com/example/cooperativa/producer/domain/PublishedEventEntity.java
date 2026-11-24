package com.example.cooperativa.producer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "published_events")
public class PublishedEventEntity {

    @Id
    @Column(name = "event_id", length = 100, nullable = false)
    private String eventId;

    @Column(name = "event_type", length = 100, nullable = false)
    private String eventType;

    @Column(name = "routing_key", length = 120, nullable = false)
    private String routingKey;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "payload_json", columnDefinition = "TEXT", nullable = false)
    private String payloadJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PublishedEventEntity() {
    }

    public PublishedEventEntity(
            String eventId,
            String eventType,
            String routingKey,
            Instant occurredAt,
            String payloadJson,
            Instant createdAt
    ) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.routingKey = routingKey;
        this.occurredAt = occurredAt;
        this.payloadJson = payloadJson;
        this.createdAt = createdAt;
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

    public Instant getCreatedAt() {
        return createdAt;
    }
}
