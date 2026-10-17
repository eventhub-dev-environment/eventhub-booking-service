package com.eventhub.booking_service.infrastructure.adapter.in.web;

import com.eventhub.booking_service.domain.model.BookingInventoryDefinition;
import com.eventhub.booking_service.domain.model.InventoryMode;
import com.eventhub.booking_service.domain.model.InventorySeatDefinition;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

@Component
public class EventTenantLookup {

    private final ObjectMapper objectMapper;

    @Value("${event.service.base-url:http://event-service:8083}")
    private String eventServiceBaseUrl;

    @Value("${gateway.internal-secret}")
    private String gatewayInternalSecret;

    public EventTenantLookup(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String findTenantId(String eventId) {
        JsonNode event = fetchEvent(eventId);
        JsonNode tenantId = event.path("tenantId");
        if (!tenantId.isTextual() || tenantId.asText().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Event Service devolvió un tenant inválido");
        }
        return tenantId.asText();
    }

    public BookingInventoryDefinition findBookingInventory(
            String eventId,
            String requestedSessionId,
            String ticketTierId) {
        JsonNode eventNode = fetchEvent(eventId);
        EventDefinition event;
        try {
            event = objectMapper.treeToValue(eventNode, EventDefinition.class);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Event Service devolvió una configuración inválida");
        }

        if (event.id() == null || !eventId.equals(event.id()) || event.status() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Event Service devolvió una configuración inválida");
        }
        if (!"PUBLISHED".equals(event.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El evento no está publicado");
        }

        List<TicketTierDefinition> tiers;
        String sessionId;
        LocalDateTime eventStartsAt;
        if (event.sessions() != null && !event.sessions().isEmpty()) {
            if (requestedSessionId == null || requestedSessionId.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe seleccionar una sesión del evento");
            }
            SessionDefinition session = event.sessions().stream()
                    .filter(candidate -> requestedSessionId.equals(candidate.id()))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sesión no encontrada"));
            tiers = session.ticketTiers();
            sessionId = session.id();
            if (session.startsAt() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Event Service devolvió una sesión sin fecha");
            }
            eventStartsAt = session.startsAt().atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        } else {
            if (requestedSessionId != null && !requestedSessionId.equals(event.id())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sesión no encontrada");
            }
            tiers = event.ticketTiers();
            sessionId = event.id();
            eventStartsAt = event.eventDate();
        }

        if (tiers == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Event Service no devolvió tarifas");
        }
        TicketTierDefinition tier = tiers.stream()
                .filter(candidate -> candidate != null)
                .filter(candidate -> ticketTierId.equals(candidate.id()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tarifa no encontrada"));

        InventoryMode inventoryMode = tier.inventoryMode() == null
                ? InventoryMode.GENERAL_ADMISSION
                : tier.inventoryMode();
        List<InventorySeatDefinition> seats = tier.seats() == null
                ? List.of()
                : tier.seats().stream()
                        .map(seat -> new InventorySeatDefinition(
                                seat.seatId(), seat.section(), seat.row(), seat.number(), seat.accessible()))
                        .toList();
        if (tier.price() == null || tier.capacity() == null || tier.capacity() <= 0
                || (inventoryMode == InventoryMode.RESERVED_SEATING && seats.size() != tier.capacity())
                || (inventoryMode == InventoryMode.GENERAL_ADMISSION && !seats.isEmpty())) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Event Service devolvió una tarifa inválida");
        }
        return new BookingInventoryDefinition(
                event.id(), sessionId, tier.id(), tier.price(), inventoryMode, tier.capacity(), seats, eventStartsAt);
    }

    private JsonNode fetchEvent(String eventId) {
        try {
            JsonNode response = RestClient.create(eventServiceBaseUrl)
                    .get()
                    .uri("/api/v1/events/{eventId}", eventId)
                    .header("X-Gateway-Secret", gatewayInternalSecret)
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode event = response == null ? null : response.path("data");
            if (event == null || !event.isObject()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Event Service devolvió un evento inválido");
            }
            return event;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Evento no encontrado");
            }
            if (exception.getStatusCode().value() == HttpStatus.FORBIDDEN.value()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "El evento no está publicado");
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No fue posible consultar Event Service");
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Event Service no está disponible");
        }
    }

    private record EventDefinition(
            String id,
            String status,
            LocalDateTime eventDate,
            List<TicketTierDefinition> ticketTiers,
            List<SessionDefinition> sessions) {
    }

    private record SessionDefinition(
            String id,
            OffsetDateTime startsAt,
            List<TicketTierDefinition> ticketTiers) {
    }

    private record TicketTierDefinition(
            String id,
            BigDecimal price,
            Integer capacity,
            InventoryMode inventoryMode,
            List<SeatDefinition> seats) {
    }

    private record SeatDefinition(
            String seatId,
            String section,
            String row,
            String number,
            boolean accessible) {
    }
}
