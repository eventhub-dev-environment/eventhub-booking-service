package com.eventhub.booking_service.infrastructure.adapter.out.persistence.entity;

import com.eventhub.booking_service.domain.model.BookingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "bookings")
public class BookingDocument {
    @Id
    private String id;
    private String eventId;
    private String ticketTierId;
    private String userId;
    private Integer quantity;
    private BigDecimal totalPrice;
    private BookingStatus status;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}