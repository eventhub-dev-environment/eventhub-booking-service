package com.eventhub.booking_service.infrastructure.adapter.out.messaging;

import com.eventhub.booking_service.domain.event.BookingCreatedEvent;
import com.eventhub.booking_service.domain.model.BookingOutboxEvent;
import com.eventhub.booking_service.domain.model.BookingOutboxStatus;
import com.eventhub.booking_service.domain.port.out.BookingOutboxRepositoryPort;
import com.eventhub.booking_service.infrastructure.config.RabbitMQConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OutboxBookingEventPublisherTest {

    @Test
    void storesBookingCreatedEventForReliablePublication() throws Exception {
        BookingOutboxRepositoryPort outboxRepository = mock(BookingOutboxRepositoryPort.class);
        OutboxBookingEventPublisher publisher = new OutboxBookingEventPublisher(
                outboxRepository, new ObjectMapper().findAndRegisterModules());

        publisher.publishBookingCreated(BookingCreatedEvent.builder()
                .bookingId("booking-123")
                .eventId("event-123")
                .sessionId("session-123")
                .ticketTierId("tier-123")
                .userId("user-123")
                .quantity(2)
                .totalPrice(new BigDecimal("85.00"))
                .createdAt(LocalDateTime.parse("2026-10-17T12:00:00"))
                .build());

        ArgumentCaptor<BookingOutboxEvent> eventCaptor = ArgumentCaptor.forClass(BookingOutboxEvent.class);
        verify(outboxRepository).save(eventCaptor.capture());
        BookingOutboxEvent saved = eventCaptor.getValue();
        assertThat(saved.aggregateType()).isEqualTo("Booking");
        assertThat(saved.aggregateId()).isEqualTo("booking-123");
        assertThat(saved.exchange()).isEqualTo(RabbitMQConfig.BOOKING_EXCHANGE);
        assertThat(saved.routingKey()).isEqualTo(RabbitMQConfig.BOOKING_CREATED_KEY);
        assertThat(saved.status()).isEqualTo(BookingOutboxStatus.PENDING);
        assertThat(new ObjectMapper().readTree(saved.payload()).path("bookingId").asText())
                .isEqualTo("booking-123");
        assertThat(saved.id()).isNull();
    }
}
