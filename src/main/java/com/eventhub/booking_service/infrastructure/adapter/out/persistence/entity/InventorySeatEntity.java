package com.eventhub.booking_service.infrastructure.adapter.out.persistence.entity;

import com.eventhub.booking_service.domain.model.InventorySeatState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_seats", uniqueConstraints = @UniqueConstraint(
        name = "uk_inventory_seat_event_session_seat",
        columnNames = {"event_id", "session_id", "seat_id"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventorySeatEntity {

    @Id
    private String id;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "session_id", nullable = false)
    private String sessionId;

    @Column(name = "ticket_tier_id", nullable = false)
    private String ticketTierId;

    @Column(name = "seat_id", nullable = false)
    private String seatId;

    private String section;
    private String seatRow;
    private String seatNumber;
    private boolean accessible;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private InventorySeatState state = InventorySeatState.AVAILABLE;

    private String bookingId;
    private LocalDateTime holdExpiresAt;
}
