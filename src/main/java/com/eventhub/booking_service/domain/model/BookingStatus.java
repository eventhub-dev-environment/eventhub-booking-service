package com.eventhub.booking_service.domain.model;

public enum BookingStatus {
    PENDING_PAYMENT,
    CONFIRMED,
    CANCELLED,
    EXPIRED,
    PAYMENT_REVIEW_REQUIRED
}