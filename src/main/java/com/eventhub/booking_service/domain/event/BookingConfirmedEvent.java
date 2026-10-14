package com.eventhub.booking_service.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingConfirmedEvent {
    private String bookingId;
    private String eventId;
    private String userId;
    private List<TicketDto> tickets;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TicketDto {
        private String ticketId;
        private String qrCodeHash;
        private String ticketTierId;
    }
}