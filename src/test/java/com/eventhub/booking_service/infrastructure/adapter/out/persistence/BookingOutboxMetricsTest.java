package com.eventhub.booking_service.infrastructure.adapter.out.persistence;

import com.eventhub.booking_service.domain.model.BookingOutboxStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookingOutboxMetricsTest {

    @Test
    void exportsPersistedFailedEventCount() {
        SpringDataJpaBookingOutboxRepository repository = mock(SpringDataJpaBookingOutboxRepository.class);
        when(repository.countByStatus(BookingOutboxStatus.FAILED)).thenReturn(2L);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        BookingOutboxMetrics metrics = new BookingOutboxMetrics(repository, meterRegistry);

        metrics.refreshFailedEventCount();

        assertThat(meterRegistry.get("eventhub.outbox.failed.events")
                .tag("service", "booking")
                .gauge()
                .value()).isEqualTo(2.0);
        verify(repository).countByStatus(BookingOutboxStatus.FAILED);
    }
}
