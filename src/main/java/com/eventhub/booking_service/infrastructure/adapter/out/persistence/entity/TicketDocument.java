package com.eventhub.booking_service.infrastructure.adapter.out.persistence.entity;

import com.eventhub.booking_service.domain.model.TicketStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "tickets")
public class TicketDocument {
    @Id
    private String id;
    private String bookingId;
    private String eventId;
    private String ticketTierId;
    private String qrCodeHash;
    private TicketStatus status;
    private LocalDateTime issuedAt;
}