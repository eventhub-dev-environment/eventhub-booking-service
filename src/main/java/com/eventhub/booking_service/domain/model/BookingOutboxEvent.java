package com.eventhub.booking_service.domain.model;

import java.time.LocalDateTime;

public record BookingOutboxEvent(
        String id,
        String aggregateType,
        String aggregateId,
        String eventType,
        String exchange,
        String routingKey,
        String payload,
        BookingOutboxStatus status,
        int publishAttempts,
        LocalDateTime createdAt,
        LocalDateTime publishedAt) {
}
