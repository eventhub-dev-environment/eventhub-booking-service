package com.eventhub.booking_service.domain.port.out;

import com.eventhub.booking_service.domain.model.Booking;
import com.eventhub.booking_service.domain.model.Ticket;

import java.util.List;
import java.util.Optional;

public interface BookingRepositoryPort {
    Booking save(Booking booking);
    Optional<Booking> findById(String id);
    List<Booking> findByUserId(String userId);
    Ticket saveTicket(Ticket ticket);
    List<Ticket> findTicketsByBookingId(String bookingId);
}