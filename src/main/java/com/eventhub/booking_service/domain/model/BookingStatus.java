package com.eventhub.booking_service.domain.model;

public enum BookingStatus {
    PENDING_PAYMENT,
    CONFIRMED,
    CANCELLATION_REQUESTED,
    CANCELLATION_REVIEW_REQUIRED,
    CANCELLED,
    EXPIRED,
    PAYMENT_REVIEW_REQUIRED
}