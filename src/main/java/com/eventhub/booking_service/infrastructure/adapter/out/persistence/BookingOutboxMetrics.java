package com.eventhub.booking_service.infrastructure.adapter.out.persistence;

import com.eventhub.booking_service.domain.model.BookingOutboxStatus;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class BookingOutboxMetrics {

    private final SpringDataJpaBookingOutboxRepository repository;
    private final AtomicLong failedEvents = new AtomicLong();

    public BookingOutboxMetrics(
            SpringDataJpaBookingOutboxRepository repository,
            MeterRegistry meterRegistry) {
        this.repository = repository;
        Gauge.builder("eventhub.outbox.failed.events", failedEvents, AtomicLong::get)
                .description("Number of booking outbox events that exhausted publishing retries")
                .tag("service", "booking")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelay = 5_000)
    public void refreshFailedEventCount() {
        failedEvents.set(repository.countByStatus(BookingOutboxStatus.FAILED));
    }
}
