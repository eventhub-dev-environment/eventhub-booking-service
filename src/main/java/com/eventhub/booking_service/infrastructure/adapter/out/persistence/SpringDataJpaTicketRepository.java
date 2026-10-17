package com.eventhub.booking_service.infrastructure.adapter.out.persistence;

import com.eventhub.booking_service.infrastructure.adapter.out.persistence.entity.TicketEntity;
import com.eventhub.booking_service.domain.model.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import org.springframework.data.repository.query.Param;

@Repository
public interface SpringDataJpaTicketRepository extends JpaRepository<TicketEntity, String> {
    List<TicketEntity> findByBookingId(String bookingId);
    boolean existsByBookingIdAndStatus(String bookingId, TicketStatus status);
    long countByBookingIdAndStatus(String bookingId, TicketStatus status);

    @Modifying
    @Query("UPDATE TicketEntity ticket SET ticket.status = :cancelled "
            + "WHERE ticket.bookingId = :bookingId AND ticket.status = :issued")
    int cancelIssuedTickets(
            @Param("bookingId") String bookingId,
            @Param("issued") TicketStatus issued,
            @Param("cancelled") TicketStatus cancelled);
}