package com.eventhub.booking_service.infrastructure.adapter.in.web;

import com.eventhub.booking_service.domain.model.Booking;
import com.eventhub.booking_service.domain.model.Ticket;
import com.eventhub.booking_service.domain.port.in.CreateBookingUseCase;
import com.eventhub.booking_service.domain.port.in.GetBookingUseCase;
import com.eventhub.booking_service.domain.port.in.ValidateTicketUseCase;
import com.eventhub.booking_service.infrastructure.adapter.in.web.dto.ApiResponse;
import com.eventhub.booking_service.infrastructure.adapter.in.web.dto.CreateBookingRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final CreateBookingUseCase createBookingUseCase;
    private final GetBookingUseCase getBookingUseCase;
    private final ValidateTicketUseCase validateTicketUseCase; // Inyectado correctamente

    @PostMapping
    public ResponseEntity<ApiResponse<Booking>> createBooking(
            @Valid @RequestBody CreateBookingRequest request,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Email") String userEmail) {
        Booking booking = Booking.builder()
                .eventId(request.getEventId())
                .ticketTierId(request.getTicketTierId())
                .userId(userId)
                .userEmail(userEmail)
                .quantity(request.getQuantity())
                .totalPrice(request.getTotalPrice())
                .build();

        Booking created = createBookingUseCase.createBooking(booking);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "Reserva creada exitosamente. Dispone de 10 minutos para realizar el pago."));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Booking>> getById(@PathVariable String id) {
        return getBookingUseCase.getById(id)
                .map(booking -> ResponseEntity.ok(ApiResponse.success(booking, "Reserva encontrada")))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Reserva no encontrada con ID: " + id, null)));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<Booking>>> getByUserId(@PathVariable String userId) {
        List<Booking> bookings = getBookingUseCase.getByUserId(userId);
        return ResponseEntity.ok(ApiResponse.success(bookings, "Reservas del usuario recuperadas exitosamente"));
    }

    @GetMapping("/{id}/tickets")
    public ResponseEntity<ApiResponse<List<Ticket>>> getTicketsByBookingId(@PathVariable String id) {
        List<Ticket> tickets = getBookingUseCase.getTicketsByBookingId(id);
        return ResponseEntity.ok(ApiResponse.success(tickets, "Tickets de la reserva recuperados exitosamente"));
    }

    @PostMapping("/tickets/validate")
    public ResponseEntity<ApiResponse<Ticket>> validateTicket(
            @RequestParam String ticketId,
            @RequestParam String qrCodeHash) {

        Ticket validatedTicket = validateTicketUseCase.validateAndUseTicket(ticketId, qrCodeHash);
        return ResponseEntity.ok(ApiResponse.success(validatedTicket, "ACCESO CONCEDIDO: Ticket válido y marcado como USADO."));
    }
}