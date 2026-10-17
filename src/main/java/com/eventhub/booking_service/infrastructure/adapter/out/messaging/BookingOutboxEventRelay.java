package com.eventhub.booking_service.infrastructure.adapter.out.messaging;

import com.eventhub.booking_service.domain.model.BookingOutboxEvent;
import com.eventhub.booking_service.domain.model.BookingOutboxStatus;
import com.eventhub.booking_service.domain.port.out.BookingOutboxRepositoryPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingOutboxEventRelay {

    private final BookingOutboxRepositoryPort outboxRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Value("${booking.outbox.batch-size:50}")
    private int batchSize = 50;

    @Value("${booking.outbox.max-attempts:10}")
    private int maxAttempts = 10;

    @Value("${booking.outbox.confirm-timeout-ms:5000}")
    private long confirmTimeoutMs = 5000;

    @Scheduled(fixedDelayString = "${booking.outbox.scan-interval-ms:5000}")
    @Transactional
    public void publishPendingEvents() {
        for (BookingOutboxEvent event : outboxRepository.findPendingForUpdate(batchSize)) {
            int attempts = event.publishAttempts() + 1;
            try {
                Map<String, Object> payload = objectMapper.readValue(
                        event.payload(), new TypeReference<>() {
                        });
                CorrelationData correlationData = new CorrelationData(event.id());
                rabbitTemplate.convertAndSend(
                        event.exchange(),
                        event.routingKey(),
                        payload,
                        message -> {
                            message.getMessageProperties().setMessageId(event.id());
                            return message;
                        },
                        correlationData);
                CorrelationData.Confirm confirm = correlationData.getFuture()
                        .get(confirmTimeoutMs, TimeUnit.MILLISECONDS);
                if (!confirm.ack() || correlationData.getReturned() != null) {
                    throw new AmqpException("RabbitMQ no confirmó el enrutamiento del evento");
                }
                outboxRepository.save(new BookingOutboxEvent(
                        event.id(),
                        event.aggregateType(),
                        event.aggregateId(),
                        event.eventType(),
                        event.exchange(),
                        event.routingKey(),
                        event.payload(),
                        BookingOutboxStatus.PUBLISHED,
                        attempts,
                        event.createdAt(),
                        LocalDateTime.now()));
            } catch (JsonProcessingException | AmqpException | ExecutionException | TimeoutException exception) {
                log.error("No se pudo publicar el evento de outbox de reserva {}", event.id(), exception);
                recordFailure(event, attempts);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                log.error("Se interrumpió la publicación del evento de reserva {}", event.id(), exception);
                recordFailure(event, attempts);
                return;
            }
        }
    }

    private void recordFailure(BookingOutboxEvent event, int attempts) {
        BookingOutboxStatus status = attempts >= maxAttempts
                ? BookingOutboxStatus.FAILED
                : BookingOutboxStatus.PENDING;
        outboxRepository.save(new BookingOutboxEvent(
                event.id(),
                event.aggregateType(),
                event.aggregateId(),
                event.eventType(),
                event.exchange(),
                event.routingKey(),
                event.payload(),
                status,
                attempts,
                event.createdAt(),
                event.publishedAt()));
    }
}
