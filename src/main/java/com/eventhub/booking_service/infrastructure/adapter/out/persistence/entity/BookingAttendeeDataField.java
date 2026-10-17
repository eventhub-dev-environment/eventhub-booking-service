package com.eventhub.booking_service.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingAttendeeDataField {

    @Column(name = "attendee_index", nullable = false)
    private Integer attendeeIndex;

    @Column(name = "field_key", nullable = false)
    private String fieldKey;

    @Column(name = "field_value")
    private String fieldValue;
}
