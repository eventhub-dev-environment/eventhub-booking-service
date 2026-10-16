package com.eventhub.booking_service.infrastructure.adapter.out.persistence;

import com.eventhub.booking_service.domain.model.Booking;
import com.eventhub.booking_service.domain.model.BookingStatus;
import com.eventhub.booking_service.infrastructure.adapter.out.persistence.entity.BookingEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
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
                .ticketTierId("tier-123")
                .userId("user-123")
                .userEmail("buyer@example.com")
                .quantity(1)
                .totalPrice(new BigDecimal("42.50"))
                .status(BookingStatus.PENDING_PAYMENT)
                .build();
        when(bookingRepository.save(any(BookingEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Booking savedBooking = adapter.save(booking);

        assertThat(savedBooking.getUserEmail()).isEqualTo("buyer@example.com");

        when(bookingRepository.findById("booking-123")).thenReturn(Optional.of(
                BookingEntity.builder()
                        .id("booking-123")
                        .eventId("event-123")
                        .ticketTierId("tier-123")
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
    }
}
