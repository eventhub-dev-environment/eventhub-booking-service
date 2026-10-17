package com.eventhub.booking_service.domain.model;

public record InventorySeatDefinition(
        String seatId,
        String section,
        String row,
        String number,
        boolean accessible) {
}
