package com.eventhub.booking_service.domain.port.out;

import com.eventhub.booking_service.domain.event.BookingCreatedEvent;

public interface BookingEventPublisherPort {
    void publishBookingCreated(BookingCreatedEvent event);
}