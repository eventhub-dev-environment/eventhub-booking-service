package com.eventhub.booking_service.domain.port.out;

import com.eventhub.booking_service.domain.model.BookingOutboxEvent;

import java.util.List;

public interface BookingOutboxRepositoryPort {

    BookingOutboxEvent save(BookingOutboxEvent event);

    List<BookingOutboxEvent> findPendingForUpdate(int limit);
}
