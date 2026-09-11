package com.eventhub.booking_service.infrastructure.adapter.out.persistence;

import com.eventhub.booking_service.infrastructure.adapter.out.persistence.entity.TicketDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpringDataMongoTicketRepository extends MongoRepository<TicketDocument, String> {
    List<TicketDocument> findByBookingId(String bookingId);
}