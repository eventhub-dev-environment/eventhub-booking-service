package com.eventhub.booking_service.infrastructure.adapter.in.web;

import com.eventhub.booking_service.domain.model.Booking;
import com.eventhub.booking_service.domain.model.BookingInventoryDefinition;
import com.eventhub.booking_service.domain.model.BookingRegistrationField;
import com.eventhub.booking_service.domain.model.Ticket;
import com.eventhub.booking_service.domain.port.in.CancelBookingUseCase;
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
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final CreateBookingUseCase createBookingUseCase;
    private final GetBookingUseCase getBookingUseCase;
    private final ValidateTicketUseCase validateTicketUseCase;
    private final EventTenantLookup eventTenantLookup;
    private final CancelBookingUseCase cancelBookingUseCase;

    @PostMapping
    public ResponseEntity<ApiResponse<Booking>> createBooking(
            @Valid @RequestBody CreateBookingRequest request,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Email") String userEmail,
            @RequestHeader("X-User-Roles") String userRoles) {
        if (!parseRoles(userRoles).contains("CUSTOMER")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere el rol CUSTOMER");
        }
        validateAttendeeDataStructure(request.getAttendeeData(), request.getQuantity());
        BookingInventoryDefinition inventoryDefinition = eventTenantLookup.findBookingInventory(
                request.getEventId(), request.getSessionId(), request.getTicketTierId());
        validateAttendeeData(request.getAttendeeData(), request.getQuantity(), inventoryDefinition.registrationFields());
        BigDecimal expectedTotal = inventoryDefinition.unitPrice()
                .multiply(BigDecimal.valueOf(request.getQuantity()))
                .setScale(2, RoundingMode.HALF_UP);
        if (request.getTotalPrice().compareTo(expectedTotal) != 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El precio de la tarifa cambió; actualice la reserva e inténtelo de nuevo");
        }
        Booking booking = Booking.builder()
                .eventId(request.getEventId())
                .sessionId(inventoryDefinition.sessionId())
                .ticketTierId(request.getTicketTierId())
                .userId(userId)
                .userEmail(userEmail)
                .quantity(request.getQuantity())
                .seatIds(request.getSeatIds())
                .attendeeData(request.getAttendeeData())
                .totalPrice(expectedTotal)
                .build();

        Booking created = createBookingUseCase.createBooking(booking, inventoryDefinition);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "Reserva creada exitosamente. Dispone de 10 minutos para realizar el pago."));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Booking>> getById(
            @PathVariable String id,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Roles") String userRoles) {
        Booking booking = getBookingUseCase.getById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reserva no encontrada"));
        requireOwnerOrPlatformAdmin(booking, userId, userRoles);
        return ResponseEntity.ok(ApiResponse.success(booking, "Reserva encontrada"));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<Booking>> cancelBooking(
            @PathVariable String id,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Roles") String userRoles) {
        if (!parseRoles(userRoles).contains("CUSTOMER")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere el rol CUSTOMER");
        }
        Booking booking = cancelBookingUseCase.requestCancellation(id, userId);
        return ResponseEntity.ok(ApiResponse.success(booking, "Solicitud de cancelación procesada"));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<Booking>>> getByUserId(
            @PathVariable String userId,
            @RequestHeader("X-User-Id") String authenticatedUserId,
            @RequestHeader("X-User-Roles") String userRoles) {
        if (!isPlatformAdmin(userRoles) && !userId.equals(authenticatedUserId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Reservas no encontradas");
        }
        String requestedUserId = isPlatformAdmin(userRoles) ? userId : authenticatedUserId;
        List<Booking> bookings = getBookingUseCase.getByUserId(requestedUserId);
        return ResponseEntity.ok(ApiResponse.success(bookings, "Reservas del usuario recuperadas exitosamente"));
    }

    @GetMapping("/{id}/tickets")
    public ResponseEntity<ApiResponse<List<Ticket>>> getTicketsByBookingId(
            @PathVariable String id,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Roles") String userRoles) {
        Booking booking = getBookingUseCase.getById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reserva no encontrada"));
        requireOwnerOrPlatformAdmin(booking, userId, userRoles);
        List<Ticket> tickets = getBookingUseCase.getTicketsByBookingId(id);
        return ResponseEntity.ok(ApiResponse.success(tickets, "Tickets de la reserva recuperados exitosamente"));
    }

    @PostMapping("/tickets/validate")
    public ResponseEntity<ApiResponse<Ticket>> validateTicket(
            @RequestParam String ticketId,
            @RequestParam String qrCodeHash,
            @RequestHeader("X-User-Roles") String userRoles,
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantId,
            @RequestHeader(value = "X-Tenant-Roles", required = false) String tenantRoles) {
        boolean platformAdmin = isPlatformAdmin(userRoles);
        if (!platformAdmin) {
            Set<String> roles = parseRoles(tenantRoles);
            if (tenantId == null || roles.stream().noneMatch(Set.of("TENANT_ADMIN", "EVENT_ORGANIZER")::contains)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere un rol de personal del tenant");
            }
            Ticket ticket = getBookingUseCase.getTicketById(ticketId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket no encontrado"));
            Booking booking = getBookingUseCase.getById(ticket.getBookingId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reserva no encontrada"));
            if (!tenantId.equals(eventTenantLookup.findTenantId(booking.getEventId()))) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El ticket pertenece a otro tenant");
            }
        }

        Ticket validatedTicket = validateTicketUseCase.validateAndUseTicket(ticketId, qrCodeHash);
        return ResponseEntity.ok(ApiResponse.success(validatedTicket, "ACCESO CONCEDIDO: Ticket válido y marcado como USADO."));
    }

    private void requireOwnerOrPlatformAdmin(Booking booking, String userId, String userRoles) {
        if (!isPlatformAdmin(userRoles) && !booking.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Reserva no encontrada");
        }
    }

    private boolean isPlatformAdmin(String userRoles) {
        return parseRoles(userRoles).contains("PLATFORM_ADMIN");
    }

    private Set<String> parseRoles(String roles) {
        if (roles == null || roles.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(roles.split(","))
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .collect(Collectors.toSet());
    }

    private void validateAttendeeDataStructure(List<Map<String, String>> attendeeData, Integer quantity) {
        if (attendeeData == null || attendeeData.isEmpty()) {
            return;
        }
        if (quantity == null || attendeeData.size() != quantity
                || attendeeData.stream().anyMatch(fields -> fields == null
                    || fields.keySet().stream().anyMatch(key -> key == null || key.isBlank())
                    || fields.values().stream().anyMatch(value -> value == null))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Los datos de registro deben incluir un mapa válido por cada entrada.");
        }
    }

    private void validateAttendeeData(
            List<Map<String, String>> attendeeData,
            Integer quantity,
            List<BookingRegistrationField> registrationFields) {
        if (registrationFields == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Event Service devolvió requisitos de registro inválidos");
        }
        if (attendeeData == null || attendeeData.isEmpty()) {
            if (registrationFields.stream().anyMatch(BookingRegistrationField::required)) {
                throw invalidAttendeeData("Debe completar los campos obligatorios para cada entrada.");
            }
            return;
        }
        if (quantity == null || attendeeData.size() != quantity
                || attendeeData.stream().anyMatch(fields -> fields == null
                    || fields.keySet().stream().anyMatch(key -> key == null || key.isBlank())
                    || fields.values().stream().anyMatch(value -> value == null))) {
            throw invalidAttendeeData("Los datos de registro deben incluir un mapa válido por cada entrada.");
        }

        Set<String> knownKeys = registrationFields.stream()
                .map(BookingRegistrationField::key)
                .collect(Collectors.toSet());
        for (Map<String, String> attendee : attendeeData) {
            if (!knownKeys.containsAll(attendee.keySet())) {
                throw invalidAttendeeData("Se incluyó un campo de registro no definido para el evento.");
            }
            for (BookingRegistrationField field : registrationFields) {
                String value = attendee.get(field.key());
                if (value == null || value.isBlank()) {
                    if (field.required()) {
                        throw invalidAttendeeData("Debe completar los campos obligatorios para cada entrada.");
                    }
                    continue;
                }
                if (!isValidRegistrationValue(field, value)) {
                    throw invalidAttendeeData("Uno o más valores de registro no cumplen la configuración del evento.");
                }
            }
        }
    }

    private boolean isValidRegistrationValue(BookingRegistrationField field, String value) {
        return switch (field.type()) {
            case "TEXT" -> true;
            case "NUMBER" -> isNumber(value);
            case "ENUM" -> field.allowedValues() != null && field.allowedValues().contains(value);
            case "BOOLEAN" -> "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
            default -> false;
        };
    }

    private boolean isNumber(String value) {
        try {
            new BigDecimal(value);
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private ResponseStatusException invalidAttendeeData(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}