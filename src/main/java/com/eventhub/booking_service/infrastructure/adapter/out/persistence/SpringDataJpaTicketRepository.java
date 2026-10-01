package com.eventhub.booking_service.infrastructure.adapter.out.persistence;

import com.eventhub.booking_service.infrastructure.adapter.out.persistence.entity.TicketEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpringDataJpaTicketRepository extends JpaRepository<TicketEntity, String> {
    List<TicketEntity> findByBookingId(String bookingId);
}