package com.eventhub.booking_service.domain.model;

import java.util.List;

public record BookingRegistrationField(
        String key,
        String type,
        boolean required,
        List<String> allowedValues) {
}
