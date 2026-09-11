package com.eventhub.booking_service.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingCreatedEvent {
    private String bookingId;
    private String eventId;
    private String ticketTierId;
    private String userId;
    private Integer quantity;
    private BigDecimal totalPrice;
    private LocalDateTime createdAt;
}