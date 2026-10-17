package com.eventhub.booking_service.domain.port.out;

import com.eventhub.booking_service.domain.model.Booking;
import com.eventhub.booking_service.domain.model.BookingInventoryDefinition;

public interface InventoryRepositoryPort {
    void initialize(BookingInventoryDefinition definition);
    void hold(Booking booking, BookingInventoryDefinition definition);
    void confirm(Booking booking);
    void release(Booking booking);
    void releaseConfirmed(Booking booking);
}
