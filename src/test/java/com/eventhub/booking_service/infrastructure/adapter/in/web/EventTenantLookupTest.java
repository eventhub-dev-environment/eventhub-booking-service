package com.eventhub.booking_service.infrastructure.adapter.in.web;

import com.eventhub.booking_service.domain.model.BookingRegistrationField;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventTenantLookupTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void loadsRegistrationFieldDefinitionsFromEventService() throws IOException {
        startServer("""
                {
                  "data": {
                    "id": "event-123",
                    "status": "PUBLISHED",
                    "eventDate": "2027-01-01T10:00:00",
                    "ticketTiers": [
                      {
                        "id": "tier-123",
                        "price": 10.00,
                        "capacity": 20,
                        "inventoryMode": "GENERAL_ADMISSION",
                        "seats": []
                      }
                    ],
                    "registrationFields": [
                      {
                        "key": "shirtSize",
                        "label": "Shirt size",
                        "type": "ENUM",
                        "required": true,
                        "options": [
                          {"label": "Small", "value": "S"},
                          {"label": "Medium", "value": "M"}
                        ]
                      }
                    ]
                  }
                }
                """);
        EventTenantLookup lookup = lookup();

        var definition = lookup.findBookingInventory("event-123", null, "tier-123");

        assertThat(definition.registrationFields()).containsExactly(
                new BookingRegistrationField("shirtSize", "ENUM", true, List.of("S", "M")));
    }

    @Test
    void rejectsInvalidRegistrationDefinitionsFromEventService() throws IOException {
        startServer("""
                {
                  "data": {
                    "id": "event-123",
                    "status": "PUBLISHED",
                    "ticketTiers": [
                      {
                        "id": "tier-123",
                        "price": 10.00,
                        "capacity": 20,
                        "inventoryMode": "GENERAL_ADMISSION",
                        "seats": []
                      }
                    ],
                    "registrationFields": [
                      {
                        "key": "shirtSize",
                        "label": "Shirt size",
                        "type": "ENUM",
                        "required": true,
                        "options": []
                      }
                    ]
                  }
                }
                """);
        EventTenantLookup lookup = lookup();

        assertThatThrownBy(() -> lookup.findBookingInventory("event-123", null, "tier-123"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    private EventTenantLookup lookup() {
        EventTenantLookup lookup = new EventTenantLookup(new ObjectMapper());
        ReflectionTestUtils.setField(lookup, "eventServiceBaseUrl",
                "http://127.0.0.1:" + server.getAddress().getPort());
        ReflectionTestUtils.setField(lookup, "gatewayInternalSecret", "test-secret");
        return lookup;
    }

    private void startServer(String responseJson) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        byte[] responseBody = responseJson.getBytes(StandardCharsets.UTF_8);
        server.createContext("/api/v1/events/event-123", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBody.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(responseBody);
            }
        });
        server.start();
    }
}
