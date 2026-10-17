package com.eventhub.booking_service.infrastructure.adapter.out.messaging;

import com.eventhub.booking_service.domain.event.BookingConfirmedEvent;
import com.eventhub.booking_service.domain.event.BookingCreatedEvent;
import com.eventhub.booking_service.domain.model.BookingOutboxEvent;
import com.eventhub.booking_service.domain.model.BookingOutboxStatus;
import com.eventhub.booking_service.domain.port.out.BookingEventPublisherPort;
import com.eventhub.booking_service.domain.port.out.BookingOutboxRepositoryPort;
import com.eventhub.booking_service.infrastructure.config.RabbitMQConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class OutboxBookingEventPublisher implements BookingEventPublisherPort {

    private final BookingOutboxRepositoryPort outboxRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void publishBookingCreated(BookingCreatedEvent event) {
        save(event.getBookingId(), event.getClass().getSimpleName(),
                RabbitMQConfig.BOOKING_CREATED_KEY, event);
    }

    @Override
    public void publishBookingConfirmed(BookingConfirmedEvent event) {
        save(event.getBookingId(), event.getClass().getSimpleName(),
                RabbitMQConfig.BOOKING_CONFIRMED_KEY, event);
    }

    private void save(String bookingId, String eventType, String routingKey, Object payload) {
        try {
            outboxRepository.save(new BookingOutboxEvent(
                    null,
                    "Booking",
                    bookingId,
                    eventType,
                    RabbitMQConfig.BOOKING_EXCHANGE,
                    routingKey,
                    objectMapper.writeValueAsString(payload),
                    BookingOutboxStatus.PENDING,
                    0,
                    LocalDateTime.now(),
                    null));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("No se pudo serializar el evento de reserva", exception);
        }
    }
}
