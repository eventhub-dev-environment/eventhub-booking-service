package com.eventhub.booking_service.domain.port.out;

import com.eventhub.booking_service.domain.model.Booking;
import com.eventhub.booking_service.domain.model.Ticket;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

public interface BookingRepositoryPort {
    Booking save(Booking booking);
    Optional<Booking> findById(String id);
    Optional<Booking> findByIdForUpdate(String id);
    List<String> findExpiredPendingBookingIds(LocalDateTime now, int limit);
    List<Booking> findByUserId(String userId);
    Ticket saveTicket(Ticket ticket);
    List<Ticket> findTicketsByBookingId(String bookingId);
    Optional<Ticket> findTicketById(String id);
}