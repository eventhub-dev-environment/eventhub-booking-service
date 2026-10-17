package com.eventhub.booking_service.infrastructure.adapter.out.persistence.entity;

import com.eventhub.booking_service.domain.model.BookingStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String eventId;

    private String sessionId;

    @Column(nullable = false)
    private String ticketTierId;

    @Column(nullable = false)
    private String userId;

    private String userEmail;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @ElementCollection
    @CollectionTable(
            name = "booking_attendee_data",
            joinColumns = @JoinColumn(name = "booking_id"),
            uniqueConstraints = @UniqueConstraint(columnNames = {"booking_id", "attendee_index", "field_key"}))
    @OrderColumn(name = "attendee_index")
    @MapKeyColumn(name = "field_key", nullable = false)
    @Column(name = "field_value")
    @Builder.Default
    private List<Map<String, String>> attendeeData = new ArrayList<>();

    @ElementCollection
    @CollectionTable(
            name = "booking_seats",
            joinColumns = @JoinColumn(name = "booking_id"),
            uniqueConstraints = @UniqueConstraint(columnNames = {"booking_id", "seat_id"}))
    @Column(name = "seat_id", nullable = false)
    @OrderColumn(name = "seat_index")
    @Builder.Default
    private List<String> seatIds = new ArrayList<>();
}