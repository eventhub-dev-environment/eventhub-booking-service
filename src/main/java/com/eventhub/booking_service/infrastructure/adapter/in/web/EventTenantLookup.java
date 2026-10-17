package com.eventhub.booking_service.infrastructure.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class EventTenantLookup {

    @Value("${event.service.base-url:http://event-service:8083}")
    private String eventServiceBaseUrl;

    @Value("${gateway.internal-secret}")
    private String gatewayInternalSecret;

    public String findTenantId(String eventId) {
        try {
            JsonNode response = RestClient.create(eventServiceBaseUrl)
                    .get()
                    .uri("/api/v1/events/{eventId}", eventId)
                    .header("X-Gateway-Secret", gatewayInternalSecret)
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode tenantId = response == null ? null : response.path("data").path("tenantId");
            if (tenantId == null || !tenantId.isTextual() || tenantId.asText().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Event Service devolvió un tenant inválido");
            }
            return tenantId.asText();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Evento no encontrado");
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No fue posible verificar el tenant del evento");
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Event Service no está disponible");
        }
    }
}
