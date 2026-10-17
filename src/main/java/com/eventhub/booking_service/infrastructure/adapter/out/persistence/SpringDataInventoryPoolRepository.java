package com.eventhub.booking_service.infrastructure.adapter.out.persistence;

import com.eventhub.booking_service.infrastructure.adapter.out.persistence.entity.InventoryPoolEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SpringDataInventoryPoolRepository extends JpaRepository<InventoryPoolEntity, String> {
    Optional<InventoryPoolEntity> findByEventIdAndSessionIdAndTicketTierId(
            String eventId,
            String sessionId,
            String ticketTierId);
}
