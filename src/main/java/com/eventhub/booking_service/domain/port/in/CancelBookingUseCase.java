package com.eventhub.booking_service.domain.port.in;

import com.eventhub.booking_service.domain.model.Booking;

public interface CancelBookingUseCase {
    Booking requestCancellation(String bookingId, String userId);
}
