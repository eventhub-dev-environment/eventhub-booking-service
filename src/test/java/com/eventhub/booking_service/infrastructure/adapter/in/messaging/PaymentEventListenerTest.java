package com.eventhub.booking_service.infrastructure.adapter.in.messaging;

import com.eventhub.booking_service.domain.event.BookingConfirmedEvent;
import com.eventhub.booking_service.domain.model.Booking;
import com.eventhub.booking_service.domain.model.BookingStatus;
import com.eventhub.booking_service.domain.model.Ticket;
import com.eventhub.booking_service.domain.port.out.BookingEventPublisherPort;
import com.eventhub.booking_service.domain.port.out.BookingRepositoryPort;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentEventListenerTest {

    @Test
    void publishesConfirmationWithBookingEmailAndPaymentDetails() {
        BookingRepositoryPort bookingRepository = mock(BookingRepositoryPort.class);
        BookingEventPublisherPort eventPublisher = mock(BookingEventPublisherPort.class);
        Booking booking = Booking.builder()
                .id("booking-123")
                .eventId("event-123")
                .sessionId("session-123")
                .seatIds(List.of("seat-1"))
                .userId("user-123")
                .userEmail("buyer@example.com")
                .quantity(1)
                .totalPrice(new BigDecimal("42.50"))
                .status(BookingStatus.PENDING_PAYMENT)
                .build();
        when(bookingRepository.findById("booking-123")).thenReturn(Optional.of(booking));
        when(bookingRepository.saveTicket(any(Ticket.class))).thenAnswer(invocation -> {
            Ticket ticket = invocation.getArgument(0);
            ticket.setId("ticket-123");
            return ticket;
        });

        PaymentEventListener listener = new PaymentEventListener(bookingRepository, eventPublisher);
        listener.handlePaymentCompleted(Map.of(
                "bookingId", "booking-123",
                "transactionId", "transaction-123"));

        ArgumentCaptor<BookingConfirmedEvent> eventCaptor = ArgumentCaptor.forClass(BookingConfirmedEvent.class);
        verify(eventPublisher).publishBookingConfirmed(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getUserEmail()).isEqualTo("buyer@example.com");
        assertThat(eventCaptor.getValue().getTransactionId()).isEqualTo("transaction-123");
        assertThat(eventCaptor.getValue().getTotalPrice()).isEqualByComparingTo("42.50");
        assertThat(eventCaptor.getValue().getSessionId()).isEqualTo("session-123");
        assertThat(eventCaptor.getValue().getTickets().getFirst().getSeatId()).isEqualTo("seat-1");
    }
}
