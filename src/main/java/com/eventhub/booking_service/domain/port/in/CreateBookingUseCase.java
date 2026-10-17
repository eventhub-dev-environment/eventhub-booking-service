package com.eventhub.booking_service.domain.port.in;

import com.eventhub.booking_service.domain.model.Booking;
import com.eventhub.booking_service.domain.model.BookingInventoryDefinition;

public interface CreateBookingUseCase {
    Booking createBooking(Booking booking, BookingInventoryDefinition inventoryDefinition);
}