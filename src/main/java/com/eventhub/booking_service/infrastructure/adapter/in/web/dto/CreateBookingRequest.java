package com.eventhub.booking_service.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateBookingRequest {

    @NotBlank(message = "El eventId es obligatorio")
    private String eventId;

    @NotBlank(message = "El ticketTierId es obligatorio")
    private String ticketTierId;

    @NotBlank(message = "El userId es obligatorio")
    private String userId;

    @NotNull(message = "La cantidad es obligatoria")
    @Min(value = 1, message = "Debe reservar al menos 1 entrada")
    private Integer quantity;

    @NotNull(message = "El precio total es obligatorio")
    private BigDecimal totalPrice;
}