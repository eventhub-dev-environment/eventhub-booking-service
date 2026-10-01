package com.eventhub.booking_service.infrastructure.adapter.out.persistence.entity;

import com.eventhub.booking_service.domain.model.TicketStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "tickets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String bookingId;

    @Column(nullable = false)
    private String eventId;

    @Column(nullable = false)
    private String ticketTierId;

    private String qrCodeHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status;

    @Column(nullable = false)
    private LocalDateTime issuedAt;
}