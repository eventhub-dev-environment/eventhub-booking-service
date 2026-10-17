package com.eventhub.booking_service.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingCancellationRequestedEvent {
    private String bookingId;
    private String transactionId;
    private BigDecimal amount;
    private String currency;
}
