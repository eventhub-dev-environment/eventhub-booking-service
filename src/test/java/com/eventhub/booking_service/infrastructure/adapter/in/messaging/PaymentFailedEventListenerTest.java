package com.eventhub.booking_service.infrastructure.adapter.in.messaging;

import com.eventhub.booking_service.infrastructure.adapter.out.persistence.SpringDataJpaPaymentInboxRepository;
import com.eventhub.booking_service.infrastructure.adapter.out.persistence.entity.PaymentInboxEventEntity;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentFailedEventListenerTest {

    @Test
    void storesProcessedFailedPaymentMessageId() {
        SpringDataJpaPaymentInboxRepository inboxRepository = mock(SpringDataJpaPaymentInboxRepository.class);
        PaymentFailedEventListener listener = new PaymentFailedEventListener(inboxRepository);

        listener.handlePaymentFailed(Map.of(
                "bookingId", "booking-123",
                "paymentId", "payment-123"), "evt-payment-failed");

        verify(inboxRepository).save(any(PaymentInboxEventEntity.class));
    }

    @Test
    void ignoresPreviouslyProcessedFailedPaymentMessage() {
        SpringDataJpaPaymentInboxRepository inboxRepository = mock(SpringDataJpaPaymentInboxRepository.class);
        when(inboxRepository.existsById("evt-payment-failed")).thenReturn(true);
        PaymentFailedEventListener listener = new PaymentFailedEventListener(inboxRepository);

        listener.handlePaymentFailed(Map.of(
                "bookingId", "booking-123",
                "paymentId", "payment-123"), "evt-payment-failed");

        verify(inboxRepository, never()).save(any(PaymentInboxEventEntity.class));
    }
}
