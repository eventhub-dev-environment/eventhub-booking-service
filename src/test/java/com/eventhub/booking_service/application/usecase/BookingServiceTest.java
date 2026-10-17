package com.eventhub.booking_service.application.usecase;

import com.eventhub.booking_service.domain.event.BookingCreatedEvent;
import com.eventhub.booking_service.domain.event.BookingCancellationRequestedEvent;
import com.eventhub.booking_service.domain.model.Booking;
import com.eventhub.booking_service.domain.model.BookingInventoryDefinition;
import com.eventhub.booking_service.domain.model.BookingStatus;
import com.eventhub.booking_service.domain.model.InventoryMode;
import com.eventhub.booking_service.domain.port.out.BookingEventPublisherPort;
import com.eventhub.booking_service.domain.port.out.BookingRepositoryPort;
import com.eventhub.booking_service.domain.port.out.InventoryRepositoryPort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class BookingServiceTest {

    @Test
    void createsBookingOnlyAfterInventoryHasBeenHeldAtCatalogPrice() {
        BookingRepositoryPort bookingRepository = mock(BookingRepositoryPort.class);
        BookingEventPublisherPort eventPublisher = mock(BookingEventPublisherPort.class);
        InventoryRepositoryPort inventoryRepository = mock(InventoryRepositoryPort.class);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId("booking-generated");
            }
            return saved;
        });
        BookingService service = new BookingService(bookingRepository, eventPublisher, inventoryRepository);
        Booking booking = Booking.builder()
                .eventId("event-123")
                .sessionId("session-123")
                .ticketTierId("tier-123")
                .userId("user-123")
                .quantity(2)
                .totalPrice(new BigDecimal("1.00"))
                .build();
        BookingInventoryDefinition definition = new BookingInventoryDefinition(
                "event-123",
                "session-123",
                "tier-123",
                new BigDecimal("12.345"),
                InventoryMode.GENERAL_ADMISSION,
                10,
                List.of(),
                java.time.LocalDateTime.now().plusDays(30));

        Booking created = service.createBooking(booking, definition);

        assertThat(created.getId()).isNotBlank();
        assertThat(created.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(created.getInventoryHeld()).isTrue();
        assertThat(created.getTotalPrice()).isEqualByComparingTo("24.69");
        assertThat(created.getExpiresAt()).isAfter(created.getCreatedAt());
        assertThat(created.getRefundCutoffAt()).isBefore(definition.eventStartsAt());
        verify(inventoryRepository).initialize(definition);
        verify(inventoryRepository).hold(created, definition);
        verify(eventPublisher).publishBookingCreated(any(BookingCreatedEvent.class));
    }

    @Test
    void expirationReleasesInventoryOnceAndChangesPendingStatus() {
        BookingRepositoryPort bookingRepository = mock(BookingRepositoryPort.class);
        BookingEventPublisherPort eventPublisher = mock(BookingEventPublisherPort.class);
        InventoryRepositoryPort inventoryRepository = mock(InventoryRepositoryPort.class);
        Booking expired = Booking.builder()
                .id("booking-123")
                .eventId("event-123")
                .sessionId("session-123")
                .ticketTierId("tier-123")
                .quantity(2)
                .status(BookingStatus.PENDING_PAYMENT)
                .inventoryHeld(true)
                .expiresAt(java.time.LocalDateTime.now().minusSeconds(1))
                .build();
        when(bookingRepository.findExpiredPendingBookingIds(any(), org.mockito.ArgumentMatchers.eq(100)))
                .thenReturn(List.of("booking-123"));
        when(bookingRepository.findByIdForUpdate("booking-123")).thenReturn(Optional.of(expired));
        when(bookingRepository.save(expired)).thenReturn(expired);
        BookingService service = new BookingService(bookingRepository, eventPublisher, inventoryRepository);

        service.expirePendingBookings();

        assertThat(expired.getStatus()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(expired.getInventoryHeld()).isFalse();
        verify(inventoryRepository).release(expired);
        verify(bookingRepository).save(expired);
    }

    @Test
    void requestsOneFullRefundForEligibleConfirmedBooking() {
        BookingRepositoryPort bookingRepository = mock(BookingRepositoryPort.class);
        BookingEventPublisherPort eventPublisher = mock(BookingEventPublisherPort.class);
        InventoryRepositoryPort inventoryRepository = mock(InventoryRepositoryPort.class);
        Booking booking = Booking.builder()
                .id("booking-123")
                .userId("user-123")
                .status(BookingStatus.CONFIRMED)
                .quantity(1)
                .paymentTransactionId("pi-123")
                .totalPrice(new BigDecimal("25.00"))
                .refundCutoffAt(java.time.LocalDateTime.now().plusHours(1))
                .build();
        when(bookingRepository.findByIdForUpdate("booking-123")).thenReturn(Optional.of(booking));
        when(bookingRepository.countTicketsByBookingIdAndStatus(
                "booking-123", com.eventhub.booking_service.domain.model.TicketStatus.ISSUED)).thenReturn(1L);
        when(bookingRepository.save(booking)).thenReturn(booking);
        BookingService service = new BookingService(bookingRepository, eventPublisher, inventoryRepository);

        Booking result = service.requestCancellation("booking-123", "user-123");
        Booking repeated = service.requestCancellation("booking-123", "user-123");

        assertThat(result.getStatus()).isEqualTo(BookingStatus.CANCELLATION_REQUESTED);
        assertThat(repeated).isSameAs(result);
        verify(eventPublisher).publishBookingCancellationRequested(any(BookingCancellationRequestedEvent.class));
        verify(bookingRepository).save(booking);
    }

    @Test
    void rejectsCancellationAfterRefundCutoff() {
        BookingRepositoryPort bookingRepository = mock(BookingRepositoryPort.class);
        BookingEventPublisherPort eventPublisher = mock(BookingEventPublisherPort.class);
        Booking booking = Booking.builder()
                .id("booking-123")
                .userId("user-123")
                .status(BookingStatus.CONFIRMED)
                .quantity(1)
                .paymentTransactionId("pi-123")
                .totalPrice(new BigDecimal("25.00"))
                .refundCutoffAt(java.time.LocalDateTime.now().minusSeconds(1))
                .build();
        when(bookingRepository.findByIdForUpdate("booking-123")).thenReturn(Optional.of(booking));
        BookingService service = new BookingService(
                bookingRepository, eventPublisher, mock(InventoryRepositoryPort.class));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> service.requestCancellation("booking-123", "user-123"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);

        verify(eventPublisher, never()).publishBookingCancellationRequested(any());
    }

    @Test
    void hidesCancellationForAnotherUser() {
        BookingRepositoryPort bookingRepository = mock(BookingRepositoryPort.class);
        Booking booking = Booking.builder()
                .id("booking-123")
                .userId("owner-user")
                .status(BookingStatus.CONFIRMED)
                .build();
        when(bookingRepository.findByIdForUpdate("booking-123")).thenReturn(Optional.of(booking));
        BookingService service = new BookingService(
                bookingRepository,
                mock(BookingEventPublisherPort.class),
                mock(InventoryRepositoryPort.class));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> service.requestCancellation("booking-123", "different-user"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .extracting(exception -> ((org.springframework.web.server.ResponseStatusException) exception)
                        .getStatusCode())
                .isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
    }
}
