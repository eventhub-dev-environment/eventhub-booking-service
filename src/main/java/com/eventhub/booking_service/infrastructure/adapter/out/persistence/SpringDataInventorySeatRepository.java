package com.eventhub.booking_service.infrastructure.adapter.out.persistence;

import com.eventhub.booking_service.infrastructure.adapter.out.persistence.entity.InventorySeatEntity;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SpringDataInventorySeatRepository extends JpaRepository<InventorySeatEntity, String> {
    @Modifying
    @Query(value = """
            INSERT INTO inventory_seats
                (id, event_id, session_id, ticket_tier_id, seat_id, section,
                 seat_row, seat_number, accessible, state, booking_id, hold_expires_at)
            VALUES
                (:id, :eventId, :sessionId, :ticketTierId, :seatId, :section,
                 :seatRow, :seatNumber, :accessible, 'AVAILABLE', NULL, NULL)
            ON CONFLICT (event_id, session_id, seat_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") String id,
            @Param("eventId") String eventId,
            @Param("sessionId") String sessionId,
            @Param("ticketTierId") String ticketTierId,
            @Param("seatId") String seatId,
            @Param("section") String section,
            @Param("seatRow") String seatRow,
            @Param("seatNumber") String seatNumber,
            @Param("accessible") boolean accessible);

    Optional<InventorySeatEntity> findByEventIdAndSessionIdAndSeatId(
            String eventId,
            String sessionId,
            String seatId);

    List<InventorySeatEntity> findByEventIdAndSessionIdAndTicketTierId(
            String eventId,
            String sessionId,
            String ticketTierId);

    @Modifying
    @Query(value = """
            UPDATE inventory_seats
            SET state = 'HELD', booking_id = :bookingId, hold_expires_at = :expiresAt
            WHERE event_id = :eventId
              AND session_id = :sessionId
              AND ticket_tier_id = :ticketTierId
              AND seat_id = :seatId
              AND (state = 'AVAILABLE' OR (state = 'HELD' AND hold_expires_at <= :now))
            """, nativeQuery = true)
    int holdSeat(
            @Param("eventId") String eventId,
            @Param("sessionId") String sessionId,
            @Param("ticketTierId") String ticketTierId,
            @Param("seatId") String seatId,
            @Param("bookingId") String bookingId,
            @Param("expiresAt") LocalDateTime expiresAt,
            @Param("now") LocalDateTime now);

    @Modifying
    @Query(value = """
            UPDATE inventory_seats
            SET state = 'SOLD', hold_expires_at = NULL
            WHERE event_id = :eventId
              AND session_id = :sessionId
              AND ticket_tier_id = :ticketTierId
              AND booking_id = :bookingId
              AND state = 'HELD'
            """, nativeQuery = true)
    int confirmHeldSeats(
            @Param("eventId") String eventId,
            @Param("sessionId") String sessionId,
            @Param("ticketTierId") String ticketTierId,
            @Param("bookingId") String bookingId);

    @Modifying
    @Query(value = """
            UPDATE inventory_seats
            SET state = 'AVAILABLE', booking_id = NULL, hold_expires_at = NULL
            WHERE event_id = :eventId
              AND session_id = :sessionId
              AND ticket_tier_id = :ticketTierId
              AND booking_id = :bookingId
              AND state = 'HELD'
            """, nativeQuery = true)
    int releaseHeldSeats(
            @Param("eventId") String eventId,
            @Param("sessionId") String sessionId,
            @Param("ticketTierId") String ticketTierId,
            @Param("bookingId") String bookingId);
}
