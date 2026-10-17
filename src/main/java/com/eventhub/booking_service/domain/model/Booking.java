package com.eventhub.booking_service.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Booking {
    private String id;
    private String eventId;
    private String sessionId;
    private String ticketTierId;
    private List<String> seatIds;
    private String userId;
    private String userEmail;
    private Integer quantity;
    private List<Map<String, String>> attendeeData;
    private BigDecimal totalPrice;
    private BookingStatus status;
    private LocalDateTime expiresAt;
    private String paymentTransactionId;
    private Boolean inventoryHeld;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}