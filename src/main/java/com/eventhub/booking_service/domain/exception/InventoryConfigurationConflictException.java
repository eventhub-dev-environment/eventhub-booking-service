package com.eventhub.booking_service.domain.exception;

public class InventoryConfigurationConflictException extends RuntimeException {
    public InventoryConfigurationConflictException(String message) {
        super(message);
    }
}
