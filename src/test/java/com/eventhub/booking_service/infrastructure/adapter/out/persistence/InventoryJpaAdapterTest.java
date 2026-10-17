package com.eventhub.booking_service.infrastructure.adapter.out.persistence;

import com.eventhub.booking_service.domain.exception.InventoryUnavailableException;
import com.eventhub.booking_service.domain.model.Booking;
import com.eventhub.booking_service.domain.model.BookingInventoryDefinition;
import com.eventhub.booking_service.domain.model.InventoryMode;
import com.eventhub.booking_service.domain.model.InventorySeatDefinition;
import com.eventhub.booking_service.infrastructure.adapter.out.persistence.entity.InventoryPoolEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryJpaAdapterTest {

    @Test
    void initializesGeneralAdmissionPoolWithoutResettingExistingCounts() {
        SpringDataInventoryPoolRepository pools = mock(SpringDataInventoryPoolRepository.class);
        SpringDataInventorySeatRepository seats = mock(SpringDataInventorySeatRepository.class);
        BookingInventoryDefinition definition = generalAdmissionDefinition();
        when(pools.findByEventIdAndSessionIdAndTicketTierId("event-1", "session-1", "tier-1"))
                .thenReturn(Optional.of(InventoryPoolEntity.builder()
                        .totalCapacity(20)
                        .availableQuantity(7)
                        .heldQuantity(3)
                        .soldQuantity(10)
                        .build()));
        InventoryJpaAdapter adapter = new InventoryJpaAdapter(pools, seats);

        adapter.initialize(definition);

        verify(pools).insertIfAbsent(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq("event-1"),
                org.mockito.ArgumentMatchers.eq("session-1"), org.mockito.ArgumentMatchers.eq("tier-1"),
                org.mockito.ArgumentMatchers.eq(20));
        verify(pools, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void atomicallyRejectsGeneralAdmissionWhenCapacityIsInsufficient() {
        SpringDataInventoryPoolRepository pools = mock(SpringDataInventoryPoolRepository.class);
        InventoryJpaAdapter adapter = new InventoryJpaAdapter(pools, mock(SpringDataInventorySeatRepository.class));
        Booking booking = Booking.builder()
                .eventId("event-1")
                .sessionId("session-1")
                .ticketTierId("tier-1")
                .quantity(2)
                .build();
        when(pools.holdAvailable("event-1", "session-1", "tier-1", 2)).thenReturn(0);

        assertThatThrownBy(() -> adapter.hold(booking, generalAdmissionDefinition()))
                .isInstanceOf(InventoryUnavailableException.class);
    }

    @Test
    void locksNumberedSeatsInStableOrder() {
        SpringDataInventorySeatRepository seats = mock(SpringDataInventorySeatRepository.class);
        InventoryJpaAdapter adapter = new InventoryJpaAdapter(
                mock(SpringDataInventoryPoolRepository.class), seats);
        LocalDateTime now = LocalDateTime.now();
        Booking booking = Booking.builder()
                .id("booking-1")
                .eventId("event-1")
                .sessionId("session-1")
                .ticketTierId("tier-1")
                .quantity(2)
                .seatIds(List.of("seat-b", "seat-a"))
                .createdAt(now)
                .expiresAt(now.plusMinutes(10))
                .build();
        when(seats.holdSeat("event-1", "session-1", "tier-1", "seat-a",
                "booking-1", booking.getExpiresAt(), now)).thenReturn(1);
        when(seats.holdSeat("event-1", "session-1", "tier-1", "seat-b",
                "booking-1", booking.getExpiresAt(), now)).thenReturn(1);
        BookingInventoryDefinition definition = new BookingInventoryDefinition(
                "event-1", "session-1", "tier-1", BigDecimal.TEN,
                InventoryMode.RESERVED_SEATING, 2, List.of(
                        new InventorySeatDefinition("seat-a", "A", "1", "1", false),
                        new InventorySeatDefinition("seat-b", "A", "1", "2", false)));

        adapter.hold(booking, definition);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(seats);
        order.verify(seats).holdSeat("event-1", "session-1", "tier-1", "seat-a",
                "booking-1", booking.getExpiresAt(), now);
        order.verify(seats).holdSeat("event-1", "session-1", "tier-1", "seat-b",
                "booking-1", booking.getExpiresAt(), now);
    }

    private BookingInventoryDefinition generalAdmissionDefinition() {
        return new BookingInventoryDefinition(
                "event-1", "session-1", "tier-1", BigDecimal.TEN,
                InventoryMode.GENERAL_ADMISSION, 20, List.of());
    }
}
