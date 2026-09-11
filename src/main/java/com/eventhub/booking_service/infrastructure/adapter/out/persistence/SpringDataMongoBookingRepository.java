package com.eventhub.booking_service.infrastructure.adapter.out.persistence;

import com.eventhub.booking_service.infrastructure.adapter.out.persistence.entity.BookingDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpringDataMongoBookingRepository extends MongoRepository<BookingDocument, String> {
    List<BookingDocument> findByUserId(String userId);
}