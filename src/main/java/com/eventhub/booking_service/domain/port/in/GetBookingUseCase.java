package com.eventhub.booking_service.domain.port.in;

import com.eventhub.booking_service.domain.model.Booking;
import com.eventhub.booking_service.domain.model.Ticket;

import java.util.List;
import java.util.Optional;

public interface GetBookingUseCase {
    Optional<Booking> getById(String id);
    List<Booking> getByUserId(String userId);
    List<Ticket> getTicketsByBookingId(String bookingId);
}