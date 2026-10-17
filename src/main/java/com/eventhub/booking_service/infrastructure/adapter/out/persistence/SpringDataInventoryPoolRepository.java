package com.eventhub.booking_service.infrastructure.adapter.out.persistence;

import com.eventhub.booking_service.infrastructure.adapter.out.persistence.entity.InventoryPoolEntity;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SpringDataInventoryPoolRepository extends JpaRepository<InventoryPoolEntity, String> {
    @Modifying
    @Query(value = """
            INSERT INTO inventory_pools
                (id, event_id, session_id, ticket_tier_id, total_capacity,
                 available_quantity, held_quantity, sold_quantity, version)
            VALUES
                (:id, :eventId, :sessionId, :ticketTierId, :capacity,
                 :capacity, 0, 0, 0)
            ON CONFLICT (event_id, session_id, ticket_tier_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") String id,
            @Param("eventId") String eventId,
            @Param("sessionId") String sessionId,
            @Param("ticketTierId") String ticketTierId,
            @Param("capacity") int capacity);

    Optional<InventoryPoolEntity> findByEventIdAndSessionIdAndTicketTierId(
            String eventId,
            String sessionId,
            String ticketTierId);

    @Modifying
    @Query(value = """
            UPDATE inventory_pools
            SET available_quantity = available_quantity - :quantity,
                held_quantity = held_quantity + :quantity
            WHERE event_id = :eventId
              AND session_id = :sessionId
              AND ticket_tier_id = :ticketTierId
              AND available_quantity >= :quantity
            """, nativeQuery = true)
    int holdAvailable(
            @Param("eventId") String eventId,
            @Param("sessionId") String sessionId,
            @Param("ticketTierId") String ticketTierId,
            @Param("quantity") int quantity);

    @Modifying
    @Query(value = """
            UPDATE inventory_pools
            SET held_quantity = held_quantity - :quantity,
                sold_quantity = sold_quantity + :quantity
            WHERE event_id = :eventId
              AND session_id = :sessionId
              AND ticket_tier_id = :ticketTierId
              AND held_quantity >= :quantity
            """, nativeQuery = true)
    int confirmHeld(
            @Param("eventId") String eventId,
            @Param("sessionId") String sessionId,
            @Param("ticketTierId") String ticketTierId,
            @Param("quantity") int quantity);

    @Modifying
    @Query(value = """
            UPDATE inventory_pools
            SET held_quantity = held_quantity - :quantity,
                available_quantity = available_quantity + :quantity
            WHERE event_id = :eventId
              AND session_id = :sessionId
              AND ticket_tier_id = :ticketTierId
              AND held_quantity >= :quantity
            """, nativeQuery = true)
    int releaseHeld(
            @Param("eventId") String eventId,
            @Param("sessionId") String sessionId,
            @Param("ticketTierId") String ticketTierId,
            @Param("quantity") int quantity);
}
