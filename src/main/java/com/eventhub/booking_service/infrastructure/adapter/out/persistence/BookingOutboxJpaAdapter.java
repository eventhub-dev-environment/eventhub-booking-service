package com.eventhub.booking_service.infrastructure.adapter.out.persistence;

import com.eventhub.booking_service.domain.model.BookingOutboxEvent;
import com.eventhub.booking_service.domain.model.BookingOutboxStatus;
import com.eventhub.booking_service.domain.port.out.BookingOutboxRepositoryPort;
import com.eventhub.booking_service.infrastructure.adapter.out.persistence.entity.BookingOutboxEventEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class BookingOutboxJpaAdapter implements BookingOutboxRepositoryPort {

    private final SpringDataJpaBookingOutboxRepository repository;

    @Override
    public BookingOutboxEvent save(BookingOutboxEvent event) {
        BookingOutboxEventEntity entity = BookingOutboxEventEntity.builder()
                .id(event.id())
                .aggregateType(event.aggregateType())
                .aggregateId(event.aggregateId())
                .eventType(event.eventType())
                .exchange(event.exchange())
                .routingKey(event.routingKey())
                .payload(event.payload())
                .status(event.status())
                .publishAttempts(event.publishAttempts())
                .createdAt(event.createdAt())
                .publishedAt(event.publishedAt())
                .build();
        return toDomain(repository.save(entity));
    }

    @Override
    public List<BookingOutboxEvent> findPendingForUpdate(int limit) {
        return repository.findByStatusForUpdate(
                        BookingOutboxStatus.PENDING,
                        PageRequest.of(0, limit))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    private BookingOutboxEvent toDomain(BookingOutboxEventEntity entity) {
        return new BookingOutboxEvent(
                entity.getId(),
                entity.getAggregateType(),
                entity.getAggregateId(),
                entity.getEventType(),
                entity.getExchange(),
                entity.getRoutingKey(),
                entity.getPayload(),
                entity.getStatus(),
                entity.getPublishAttempts(),
                entity.getCreatedAt(),
                entity.getPublishedAt());
    }
}
