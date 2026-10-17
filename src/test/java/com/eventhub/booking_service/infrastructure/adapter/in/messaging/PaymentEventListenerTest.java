package com.eventhub.booking_service.infrastructure.adapter.in.messaging;

import com.eventhub.booking_service.domain.event.BookingConfirmedEvent;
import com.eventhub.booking_service.domain.model.Booking;
import com.eventhub.booking_service.domain.model.BookingStatus;
import com.eventhub.booking_service.domain.model.Ticket;
import com.eventhub.booking_service.domain.port.out.BookingEventPublisherPort;
import com.eventhub.booking_service.domain.port.out.BookingRepositoryPort;
import com.eventhub.booking_service.domain.port.out.InventoryRepositoryPort;
import com.eventhub.booking_service.infrastructure.adapter.out.persistence.SpringDataJpaPaymentInboxRepository;
import com.eventhub.booking_service.infrastructure.adapter.out.persistence.entity.PaymentInboxEventEntity;
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
import static org.mockito.Mockito.never;

class PaymentEventListenerTest {
    @Test
    void completesCancellationOnlyAfterConfirmedPaymentRefund() {
        BookingRepositoryPort bookingRepository = mock(BookingRepositoryPort.class);
        BookingEventPublisherPort eventPublisher = mock(BookingEventPublisherPort.class);
        InventoryRepositoryPort inventoryRepository = mock(InventoryRepositoryPort.class);
        SpringDataJpaPaymentInboxRepository inboxRepository = mock(SpringDataJpaPaymentInboxRepository.class);
        Booking booking = Booking.builder()
                .id("booking-123")
                .eventId("event-123")
                .sessionId("session-123")
                .ticketTierId("tier-123")
                .quantity(2)
                .seatIds(List.of())
                .paymentTransactionId("pi-123")
                .status(BookingStatus.CANCELLATION_REQUESTED)
                .build();
        when(bookingRepository.findByIdForUpdate("booking-123")).thenReturn(Optional.of(booking));
        when(bookingRepository.countTicketsByBookingIdAndStatus(
                "booking-123", com.eventhub.booking_service.domain.model.TicketStatus.ISSUED)).thenReturn(2L);
        when(bookingRepository.cancelIssuedTickets("booking-123")).thenReturn(2);
        when(bookingRepository.save(booking)).thenReturn(booking);
        PaymentEventListener listener = new PaymentEventListener(
                bookingRepository, eventPublisher, inventoryRepository, inboxRepository);

        listener.handlePaymentRefunded(Map.of(
                "bookingId", "booking-123",
                "transactionId", "pi-123",
                "refundId", "re-123"), "evt-refund-123");

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        verify(inventoryRepository).releaseConfirmed(booking);
        verify(bookingRepository).cancelIssuedTickets("booking-123");
        verify(bookingRepository).save(booking);
        verify(inboxRepository).save(any(PaymentInboxEventEntity.class));
    }

    @Test
    void movesCancellationToManualReviewWhenStripeRefundFails() {
        BookingRepositoryPort bookingRepository = mock(BookingRepositoryPort.class);
        Booking booking = Booking.builder()
                .id("booking-123")
                .paymentTransactionId("pi-123")
                .status(BookingStatus.CANCELLATION_REQUESTED)
                .build();
        when(bookingRepository.findByIdForUpdate("booking-123")).thenReturn(Optional.of(booking));
        when(bookingRepository.save(booking)).thenReturn(booking);
        SpringDataJpaPaymentInboxRepository inboxRepository = mock(SpringDataJpaPaymentInboxRepository.class);
        PaymentEventListener listener = new PaymentEventListener(
                bookingRepository,
                mock(BookingEventPublisherPort.class),
                mock(InventoryRepositoryPort.class),
                inboxRepository);

        listener.handlePaymentRefundFailed(Map.of(
                "bookingId", "booking-123",
                "transactionId", "pi-123",
                "reason", "provider rejected refund"), "evt-refund-failed");

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLATION_REVIEW_REQUIRED);
        verify(bookingRepository).save(booking);
        verify(inboxRepository).save(any(PaymentInboxEventEntity.class));
    }


    @Test
    void publishesConfirmationWithBookingEmailAndPaymentDetails() {
        BookingRepositoryPort bookingRepository = mock(BookingRepositoryPort.class);
        BookingEventPublisherPort eventPublisher = mock(BookingEventPublisherPort.class);
        InventoryRepositoryPort inventoryRepository = mock(InventoryRepositoryPort.class);
        SpringDataJpaPaymentInboxRepository inboxRepository = mock(SpringDataJpaPaymentInboxRepository.class);
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
                .inventoryHeld(true)
                .expiresAt(java.time.LocalDateTime.now().plusMinutes(10))
                .build();
        when(bookingRepository.findByIdForUpdate("booking-123")).thenReturn(Optional.of(booking));
        when(bookingRepository.saveTicket(any(Ticket.class))).thenAnswer(invocation -> {
            Ticket ticket = invocation.getArgument(0);
            ticket.setId("ticket-123");
            return ticket;
        });

        PaymentEventListener listener = new PaymentEventListener(
                bookingRepository, eventPublisher, inventoryRepository, inboxRepository);
        listener.handlePaymentCompleted(Map.of(
                "bookingId", "booking-123",
                "transactionId", "transaction-123",
                "currency", "COP",
                "amount", new BigDecimal("42.50")), "evt-payment-123");

        verify(inventoryRepository).confirm(booking);
        ArgumentCaptor<BookingConfirmedEvent> eventCaptor = ArgumentCaptor.forClass(BookingConfirmedEvent.class);
        verify(eventPublisher).publishBookingConfirmed(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getUserEmail()).isEqualTo("buyer@example.com");
        assertThat(eventCaptor.getValue().getTransactionId()).isEqualTo("transaction-123");
        assertThat(eventCaptor.getValue().getTotalPrice()).isEqualByComparingTo("42.50");
        assertThat(eventCaptor.getValue().getSessionId()).isEqualTo("session-123");
        assertThat(eventCaptor.getValue().getTickets().getFirst().getSeatId()).isEqualTo("seat-1");
        verify(inboxRepository).save(any(PaymentInboxEventEntity.class));
    }

    @Test
    void sendsExpiredPaidBookingToManualReviewWithoutIssuingTickets() {
        BookingRepositoryPort bookingRepository = mock(BookingRepositoryPort.class);
        BookingEventPublisherPort eventPublisher = mock(BookingEventPublisherPort.class);
        InventoryRepositoryPort inventoryRepository = mock(InventoryRepositoryPort.class);
        SpringDataJpaPaymentInboxRepository inboxRepository = mock(SpringDataJpaPaymentInboxRepository.class);
        Booking booking = Booking.builder()
                .id("booking-123")
                .eventId("event-123")
                .sessionId("session-123")
                .ticketTierId("tier-123")
                .userId("user-123")
                .quantity(1)
                .totalPrice(new BigDecimal("42.50"))
                .status(BookingStatus.EXPIRED)
                .build();
        when(bookingRepository.findByIdForUpdate("booking-123")).thenReturn(Optional.of(booking));
        when(bookingRepository.save(booking)).thenReturn(booking);

        PaymentEventListener listener = new PaymentEventListener(
                bookingRepository, eventPublisher, inventoryRepository, inboxRepository);
        listener.handlePaymentCompleted(Map.of(
                "bookingId", "booking-123",
                "transactionId", "transaction-late",
                "currency", "COP",
                "amount", new BigDecimal("42.50")), "evt-late");

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PAYMENT_REVIEW_REQUIRED);
        assertThat(booking.getPaymentTransactionId()).isEqualTo("transaction-late");
        verify(bookingRepository).save(booking);
        verify(inventoryRepository, org.mockito.Mockito.never()).confirm(booking);
        verify(inventoryRepository, org.mockito.Mockito.never()).release(booking);
        verify(bookingRepository, org.mockito.Mockito.never()).saveTicket(any(Ticket.class));
        verify(eventPublisher, org.mockito.Mockito.never()).publishBookingConfirmed(any());
    }

    @Test
    void releasesInventoryAndSendsUnexpectedPaymentAmountToManualReview() {
        BookingRepositoryPort bookingRepository = mock(BookingRepositoryPort.class);
        BookingEventPublisherPort eventPublisher = mock(BookingEventPublisherPort.class);
        InventoryRepositoryPort inventoryRepository = mock(InventoryRepositoryPort.class);
        SpringDataJpaPaymentInboxRepository inboxRepository = mock(SpringDataJpaPaymentInboxRepository.class);
        Booking booking = Booking.builder()
                .id("booking-123")
                .eventId("event-123")
                .sessionId("session-123")
                .ticketTierId("tier-123")
                .userId("user-123")
                .quantity(1)
                .totalPrice(new BigDecimal("42.50"))
                .status(BookingStatus.PENDING_PAYMENT)
                .inventoryHeld(true)
                .expiresAt(java.time.LocalDateTime.now().plusMinutes(10))
                .build();
        when(bookingRepository.findByIdForUpdate("booking-123")).thenReturn(Optional.of(booking));
        when(bookingRepository.save(booking)).thenReturn(booking);

        PaymentEventListener listener = new PaymentEventListener(
                bookingRepository, eventPublisher, inventoryRepository, inboxRepository);
        listener.handlePaymentCompleted(Map.of(
                "bookingId", "booking-123",
                "transactionId", "transaction-wrong-amount",
                "currency", "COP",
                "amount", new BigDecimal("1.00")), "evt-wrong-amount");

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PAYMENT_REVIEW_REQUIRED);
        assertThat(booking.getInventoryHeld()).isFalse();
        verify(inventoryRepository).release(booking);
        verify(bookingRepository, org.mockito.Mockito.never()).saveTicket(any(Ticket.class));
        verify(eventPublisher, org.mockito.Mockito.never()).publishBookingConfirmed(any());
    }

    @Test
    void sendsUnexpectedCurrencyPaymentToManualReview() {
        BookingRepositoryPort bookingRepository = mock(BookingRepositoryPort.class);
        BookingEventPublisherPort eventPublisher = mock(BookingEventPublisherPort.class);
        InventoryRepositoryPort inventoryRepository = mock(InventoryRepositoryPort.class);
        SpringDataJpaPaymentInboxRepository inboxRepository = mock(SpringDataJpaPaymentInboxRepository.class);
        Booking booking = Booking.builder()
                .id("booking-123")
                .eventId("event-123")
                .sessionId("session-123")
                .ticketTierId("tier-123")
                .quantity(1)
                .totalPrice(new BigDecimal("42.50"))
                .status(BookingStatus.PENDING_PAYMENT)
                .inventoryHeld(true)
                .expiresAt(java.time.LocalDateTime.now().plusMinutes(10))
                .build();
        when(bookingRepository.findByIdForUpdate("booking-123")).thenReturn(Optional.of(booking));
        when(bookingRepository.save(booking)).thenReturn(booking);

        PaymentEventListener listener = new PaymentEventListener(
                bookingRepository, eventPublisher, inventoryRepository, inboxRepository);
        listener.handlePaymentCompleted(Map.of(
                "bookingId", "booking-123",
                "transactionId", "transaction-usd",
                "currency", "USD",
                "amount", new BigDecimal("42.50")), "evt-usd");

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PAYMENT_REVIEW_REQUIRED);
        verify(inventoryRepository).release(booking);
        verify(bookingRepository, org.mockito.Mockito.never()).saveTicket(any(Ticket.class));
    }

    @Test
    void ignoresPaymentEventAlreadyRecordedInInbox() {
        BookingRepositoryPort bookingRepository = mock(BookingRepositoryPort.class);
        BookingEventPublisherPort eventPublisher = mock(BookingEventPublisherPort.class);
        InventoryRepositoryPort inventoryRepository = mock(InventoryRepositoryPort.class);
        SpringDataJpaPaymentInboxRepository inboxRepository = mock(SpringDataJpaPaymentInboxRepository.class);
        when(inboxRepository.existsById("evt-duplicate")).thenReturn(true);

        PaymentEventListener listener = new PaymentEventListener(
                bookingRepository, eventPublisher, inventoryRepository, inboxRepository);
        listener.handlePaymentCompleted(Map.of(
                "bookingId", "booking-123",
                "transactionId", "transaction-123",
                "currency", "COP",
                "amount", new BigDecimal("42.50")), "evt-duplicate");

        verify(bookingRepository, never()).findByIdForUpdate(any());
        verify(inboxRepository, never()).save(any(PaymentInboxEventEntity.class));
    }
}
