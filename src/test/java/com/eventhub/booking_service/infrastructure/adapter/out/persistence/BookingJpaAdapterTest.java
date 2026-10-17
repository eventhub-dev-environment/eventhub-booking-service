package com.eventhub.booking_service.infrastructure.adapter.out.persistence;

import com.eventhub.booking_service.domain.model.Booking;
import com.eventhub.booking_service.domain.model.BookingStatus;
import com.eventhub.booking_service.domain.model.Ticket;
import com.eventhub.booking_service.infrastructure.adapter.out.persistence.entity.BookingEntity;
import com.eventhub.booking_service.infrastructure.adapter.out.persistence.entity.TicketEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BookingJpaAdapterTest {

    @Test
    void preservesUserEmailWhenSavingAndLoadingBooking() {
        SpringDataJpaBookingRepository bookingRepository = mock(SpringDataJpaBookingRepository.class);
        SpringDataJpaTicketRepository ticketRepository = mock(SpringDataJpaTicketRepository.class);
        BookingJpaAdapter adapter = new BookingJpaAdapter(bookingRepository, ticketRepository);
        Booking booking = Booking.builder()
                .id("booking-123")
                .eventId("event-123")
                .sessionId("session-123")
                .ticketTierId("tier-123")
                .seatIds(List.of("seat-1"))
                .attendeeData(List.of(Map.of("shirtSize", "M")))
                .userId("user-123")
                .userEmail("buyer@example.com")
                .quantity(1)
                .totalPrice(new BigDecimal("42.50"))
                .status(BookingStatus.PENDING_PAYMENT)
                .paymentTransactionId("transaction-123")
                .inventoryHeld(true)
                .build();
        when(bookingRepository.save(any(BookingEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Booking savedBooking = adapter.save(booking);

        assertThat(savedBooking.getUserEmail()).isEqualTo("buyer@example.com");
        assertThat(savedBooking.getSessionId()).isEqualTo("session-123");
        assertThat(savedBooking.getPaymentTransactionId()).isEqualTo("transaction-123");
        assertThat(savedBooking.getInventoryHeld()).isTrue();
        assertThat(savedBooking.getSeatIds()).containsExactly("seat-1");
        assertThat(savedBooking.getAttendeeData()).containsExactly(Map.of("shirtSize", "M"));

        when(bookingRepository.findById("booking-123")).thenReturn(Optional.of(
                BookingEntity.builder()
                        .id("booking-123")
                        .eventId("event-123")
                        .sessionId("session-123")
                        .ticketTierId("tier-123")
                        .seatIds(List.of("seat-1"))
                        .attendeeData(List.of(Map.of("shirtSize", "M")))
                        .userId("user-123")
                        .userEmail("buyer@example.com")
                        .quantity(1)
                        .totalPrice(new BigDecimal("42.50"))
                        .status(BookingStatus.PENDING_PAYMENT)
                        .build()));

        assertThat(adapter.findById("booking-123"))
                .get()
                .extracting(Booking::getUserEmail)
                .isEqualTo("buyer@example.com");

        assertThat(adapter.findById("booking-123"))
                .get()
                .extracting(Booking::getSeatIds)
                .asList()
                .containsExactly("seat-1");
        assertThat(adapter.findById("booking-123"))
                .get()
                .extracting(Booking::getAttendeeData)
                .asList()
                .containsExactly(Map.of("shirtSize", "M"));

        when(ticketRepository.save(any(TicketEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        Ticket ticket = adapter.saveTicket(Ticket.builder()
                .id("ticket-123")
                .bookingId("booking-123")
                .eventId("event-123")
                .sessionId("session-123")
                .ticketTierId("tier-123")
                .seatId("seat-1")
                .build());
        assertThat(ticket.getSessionId()).isEqualTo("session-123");
        assertThat(ticket.getSeatId()).isEqualTo("seat-1");
    }
}
