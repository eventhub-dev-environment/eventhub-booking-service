package com.eventhub.booking_service.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "payment_event_inbox")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentInboxEventEntity {

    @Id
    @Column(name = "event_id", nullable = false, length = 200)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 120)
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;
}
