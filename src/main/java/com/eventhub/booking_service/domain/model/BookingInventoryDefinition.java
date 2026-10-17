package com.eventhub.booking_service.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record BookingInventoryDefinition(
        String eventId,
        String sessionId,
        String ticketTierId,
        BigDecimal unitPrice,
        InventoryMode inventoryMode,
        Integer capacity,
        List<InventorySeatDefinition> seats,
        LocalDateTime eventStartsAt) {
}
