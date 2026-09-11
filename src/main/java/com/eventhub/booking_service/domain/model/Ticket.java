package com.eventhub.booking_service.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Ticket {
    private String id;
    private String bookingId;
    private String eventId;
    private String ticketTierId;
    private String qrCodeHash;
    private TicketStatus status;
    private LocalDateTime issuedAt;
}