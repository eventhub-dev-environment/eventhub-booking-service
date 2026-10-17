package com.eventhub.booking_service.infrastructure.adapter.out.persistence;

import com.eventhub.booking_service.infrastructure.adapter.out.persistence.entity.InventorySeatEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SpringDataInventorySeatRepository extends JpaRepository<InventorySeatEntity, String> {
    Optional<InventorySeatEntity> findByEventIdAndSessionIdAndSeatId(
            String eventId,
            String sessionId,
            String seatId);

    List<InventorySeatEntity> findByEventIdAndSessionIdAndTicketTierId(
            String eventId,
            String sessionId,
            String ticketTierId);
}
