package com.eventhub.booking_service.infrastructure.adapter.out.messaging;

import com.eventhub.booking_service.domain.event.BookingCreatedEvent;
import com.eventhub.booking_service.domain.port.out.BookingEventPublisherPort;
import com.eventhub.booking_service.infrastructure.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitMQBookingEventPublisher implements BookingEventPublisherPort {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void publishBookingCreated(BookingCreatedEvent event) {
        log.info("Publicando evento BookingCreatedEvent para la reserva ID: {}", event.getBookingId());
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.BOOKING_EXCHANGE,
                RabbitMQConfig.BOOKING_CREATED_KEY,
                event
        );
    }
}