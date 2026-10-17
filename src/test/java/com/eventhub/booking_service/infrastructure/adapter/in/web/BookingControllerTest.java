package com.eventhub.booking_service.infrastructure.adapter.in.web;

import com.eventhub.booking_service.domain.model.Booking;
import com.eventhub.booking_service.domain.model.BookingInventoryDefinition;
import com.eventhub.booking_service.domain.model.BookingRegistrationField;
import com.eventhub.booking_service.domain.model.InventoryMode;
import com.eventhub.booking_service.domain.model.Ticket;
import com.eventhub.booking_service.domain.port.in.CreateBookingUseCase;
import com.eventhub.booking_service.domain.port.in.CancelBookingUseCase;
import com.eventhub.booking_service.domain.port.in.GetBookingUseCase;
import com.eventhub.booking_service.domain.port.in.ValidateTicketUseCase;
import com.eventhub.booking_service.infrastructure.adapter.in.web.dto.CreateBookingRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class BookingControllerTest {

    @Test
    void createsBookingUsingUserIdFromGatewayHeader() {
        AtomicReference<Booking> submittedBooking = new AtomicReference<>();
        CreateBookingUseCase createBookingUseCase = (booking, definition) -> {
            submittedBooking.set(booking);
            return booking;
        };
        EventTenantLookup eventTenantLookup = mock(EventTenantLookup.class);
        when(eventTenantLookup.findBookingInventory("event-123", "session-123", "tier-123"))
                .thenReturn(new BookingInventoryDefinition(
                        "event-123", "session-123", "tier-123", new BigDecimal("10.00"),
                        InventoryMode.GENERAL_ADMISSION, 100, List.of(), null,
                        List.of(new BookingRegistrationField("shirtSize", "ENUM", true, List.of("M", "S")))));
        BookingController controller = new BookingController(
                createBookingUseCase,
                mock(GetBookingUseCase.class),
                mock(ValidateTicketUseCase.class),
                eventTenantLookup,
                mock(CancelBookingUseCase.class));
        CreateBookingRequest request = new CreateBookingRequest();
        request.setEventId("event-123");
        request.setSessionId("session-123");
        request.setTicketTierId("tier-123");
        request.setQuantity(2);
        request.setAttendeeData(List.of(Map.of("shirtSize", "M"), Map.of("shirtSize", "S")));
        request.setTotalPrice(new BigDecimal("20.00"));

        controller.createBooking(request, "signed-user-id", "buyer@example.com", "CUSTOMER");

        assertThat(submittedBooking.get().getUserId()).isEqualTo("signed-user-id");
        assertThat(submittedBooking.get().getUserEmail()).isEqualTo("buyer@example.com");
        assertThat(submittedBooking.get().getSessionId()).isEqualTo("session-123");
        assertThat(submittedBooking.get().getAttendeeData())
                .containsExactly(Map.of("shirtSize", "M"), Map.of("shirtSize", "S"));
    }

    @Test
    void hidesAnotherUsersBooking() {
        GetBookingUseCase getBookingUseCase = mock(GetBookingUseCase.class);
        when(getBookingUseCase.getById("booking-123")).thenReturn(Optional.of(Booking.builder()
                .id("booking-123")
                .userId("owner-user")
                .build()));
        BookingController controller = new BookingController(
                mock(CreateBookingUseCase.class),
                getBookingUseCase,
                mock(ValidateTicketUseCase.class),
                mock(EventTenantLookup.class),
                mock(CancelBookingUseCase.class));

        assertThatThrownBy(() -> controller.getById("booking-123", "different-user", "CUSTOMER"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void customerCanRequestCancellationForTheirBooking() {
        CancelBookingUseCase cancelBookingUseCase = mock(CancelBookingUseCase.class);
        Booking booking = Booking.builder().id("booking-123").build();
        when(cancelBookingUseCase.requestCancellation("booking-123", "user-123")).thenReturn(booking);
        BookingController controller = new BookingController(
                mock(CreateBookingUseCase.class),
                mock(GetBookingUseCase.class),
                mock(ValidateTicketUseCase.class),
                mock(EventTenantLookup.class),
                cancelBookingUseCase);

        var response = controller.cancelBooking("booking-123", "user-123", "CUSTOMER");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(cancelBookingUseCase).requestCancellation("booking-123", "user-123");
    }

    @Test
    void nonCustomerCannotRequestCancellation() {
        CancelBookingUseCase cancelBookingUseCase = mock(CancelBookingUseCase.class);
        BookingController controller = new BookingController(
                mock(CreateBookingUseCase.class),
                mock(GetBookingUseCase.class),
                mock(ValidateTicketUseCase.class),
                mock(EventTenantLookup.class),
                cancelBookingUseCase);

        assertThatThrownBy(() -> controller.cancelBooking("booking-123", "user-123", "EVENT_ORGANIZER"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        verify(cancelBookingUseCase, never()).requestCancellation("booking-123", "user-123");
    }

    @Test
    void customerCannotCreateBookingWithoutCustomerRole() {
        BookingController controller = new BookingController(
                mock(CreateBookingUseCase.class),
                mock(GetBookingUseCase.class),
                mock(ValidateTicketUseCase.class),
                mock(EventTenantLookup.class),
                mock(CancelBookingUseCase.class));
        CreateBookingRequest request = new CreateBookingRequest();
        request.setEventId("event-123");
        request.setTicketTierId("tier-123");
        request.setQuantity(1);
        request.setTotalPrice(new BigDecimal("10.00"));

        assertThatThrownBy(() -> controller.createBooking(request, "user-123", "buyer@example.com", "EVENT_ORGANIZER"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void rejectsRegistrationDataCountThatDiffersFromTicketQuantity() {
        BookingController controller = new BookingController(
                mock(CreateBookingUseCase.class),
                mock(GetBookingUseCase.class),
                mock(ValidateTicketUseCase.class),
                mock(EventTenantLookup.class),
                mock(CancelBookingUseCase.class));
        CreateBookingRequest request = new CreateBookingRequest();
        request.setEventId("event-123");
        request.setTicketTierId("tier-123");
        request.setQuantity(2);
        request.setTotalPrice(new BigDecimal("20.00"));
        request.setAttendeeData(List.of(Map.of("shirtSize", "M")));

        assertThatThrownBy(() -> controller.createBooking(
                request, "user-123", "buyer@example.com", "CUSTOMER"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void validatesRequiredFieldsForEveryAttendee() {
        EventTenantLookup eventTenantLookup = mock(EventTenantLookup.class);
        when(eventTenantLookup.findBookingInventory("event-123", null, "tier-123"))
                .thenReturn(definitionWithFields(List.of(
                        new BookingRegistrationField("fullName", "TEXT", true, List.of()))));
        CreateBookingUseCase createBookingUseCase = mock(CreateBookingUseCase.class);
        BookingController controller = new BookingController(
                createBookingUseCase,
                mock(GetBookingUseCase.class),
                mock(ValidateTicketUseCase.class),
                eventTenantLookup,
                mock(CancelBookingUseCase.class));
        CreateBookingRequest request = bookingRequest(2);
        request.setAttendeeData(List.of(
                Map.of("fullName", "Ada Lovelace"),
                Map.of()));

        assertThatThrownBy(() -> controller.createBooking(
                request, "user-123", "buyer@example.com", "CUSTOMER"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(createBookingUseCase, never()).createBooking(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void requiresAttendeeDataWhenEventFieldsAreRequired() {
        EventTenantLookup eventTenantLookup = mock(EventTenantLookup.class);
        when(eventTenantLookup.findBookingInventory("event-123", null, "tier-123"))
                .thenReturn(definitionWithFields(List.of(
                        new BookingRegistrationField("fullName", "TEXT", true, List.of()))));
        BookingController controller = new BookingController(
                mock(CreateBookingUseCase.class),
                mock(GetBookingUseCase.class),
                mock(ValidateTicketUseCase.class),
                eventTenantLookup,
                mock(CancelBookingUseCase.class));

        assertThatThrownBy(() -> controller.createBooking(
                bookingRequest(1), "user-123", "buyer@example.com", "CUSTOMER"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsUnknownRegistrationFields() {
        EventTenantLookup eventTenantLookup = mock(EventTenantLookup.class);
        when(eventTenantLookup.findBookingInventory("event-123", null, "tier-123"))
                .thenReturn(definitionWithFields(List.of(
                        new BookingRegistrationField("fullName", "TEXT", false, List.of()))));
        BookingController controller = new BookingController(
                mock(CreateBookingUseCase.class),
                mock(GetBookingUseCase.class),
                mock(ValidateTicketUseCase.class),
                eventTenantLookup,
                mock(CancelBookingUseCase.class));
        CreateBookingRequest request = bookingRequest(1);
        request.setAttendeeData(List.of(Map.of("unconfiguredField", "value")));

        assertThatThrownBy(() -> controller.createBooking(
                request, "user-123", "buyer@example.com", "CUSTOMER"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void validatesRegistrationValuesUsingConfiguredTypes() {
        EventTenantLookup eventTenantLookup = mock(EventTenantLookup.class);
        when(eventTenantLookup.findBookingInventory("event-123", null, "tier-123"))
                .thenReturn(definitionWithFields(List.of(
                        new BookingRegistrationField("fullName", "TEXT", true, List.of()),
                        new BookingRegistrationField("age", "NUMBER", true, List.of()),
                        new BookingRegistrationField("hasDietaryNeeds", "BOOLEAN", true, List.of()),
                        new BookingRegistrationField("shirtSize", "ENUM", true, List.of("S", "M")))));
        BookingController controller = new BookingController(
                mock(CreateBookingUseCase.class),
                mock(GetBookingUseCase.class),
                mock(ValidateTicketUseCase.class),
                eventTenantLookup,
                mock(CancelBookingUseCase.class));
        CreateBookingRequest request = bookingRequest(1);
        request.setAttendeeData(List.of(Map.of(
                "fullName", "Ada Lovelace",
                "age", "36",
                "hasDietaryNeeds", "false",
                "shirtSize", "M")));

        assertThatCode(() -> controller.createBooking(
                request, "user-123", "buyer@example.com", "CUSTOMER"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsValuesThatDoNotMatchConfiguredTypes() {
        EventTenantLookup eventTenantLookup = mock(EventTenantLookup.class);
        when(eventTenantLookup.findBookingInventory("event-123", null, "tier-123"))
                .thenReturn(definitionWithFields(List.of(
                        new BookingRegistrationField("age", "NUMBER", true, List.of()))));
        BookingController controller = new BookingController(
                mock(CreateBookingUseCase.class),
                mock(GetBookingUseCase.class),
                mock(ValidateTicketUseCase.class),
                eventTenantLookup,
                mock(CancelBookingUseCase.class));
        CreateBookingRequest request = bookingRequest(1);
        request.setAttendeeData(List.of(Map.of("age", "not-a-number")));

        assertThatThrownBy(() -> controller.createBooking(
                request, "user-123", "buyer@example.com", "CUSTOMER"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsEnumValuesOutsideConfiguredOptions() {
        EventTenantLookup eventTenantLookup = mock(EventTenantLookup.class);
        when(eventTenantLookup.findBookingInventory("event-123", null, "tier-123"))
                .thenReturn(definitionWithFields(List.of(
                        new BookingRegistrationField("shirtSize", "ENUM", true, List.of("S", "M")))));
        BookingController controller = new BookingController(
                mock(CreateBookingUseCase.class),
                mock(GetBookingUseCase.class),
                mock(ValidateTicketUseCase.class),
                eventTenantLookup,
                mock(CancelBookingUseCase.class));
        CreateBookingRequest request = bookingRequest(1);
        request.setAttendeeData(List.of(Map.of("shirtSize", "XL")));

        assertThatThrownBy(() -> controller.createBooking(
                request, "user-123", "buyer@example.com", "CUSTOMER"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsClientPriceThatDoesNotMatchPublishedTierPrice() {
        EventTenantLookup eventTenantLookup = mock(EventTenantLookup.class);
        when(eventTenantLookup.findBookingInventory("event-123", null, "tier-123"))
                .thenReturn(new BookingInventoryDefinition(
                        "event-123", "event-123", "tier-123", new BigDecimal("10.00"),
                        InventoryMode.GENERAL_ADMISSION, 100, List.of(), null, List.of()));
        CreateBookingUseCase createBookingUseCase = mock(CreateBookingUseCase.class);
        BookingController controller = new BookingController(
                createBookingUseCase,
                mock(GetBookingUseCase.class),
                mock(ValidateTicketUseCase.class),
                eventTenantLookup,
                mock(CancelBookingUseCase.class));
        CreateBookingRequest request = new CreateBookingRequest();
        request.setEventId("event-123");
        request.setTicketTierId("tier-123");
        request.setQuantity(1);
        request.setTotalPrice(new BigDecimal("0.01"));

        assertThatThrownBy(() -> controller.createBooking(
                request, "user-123", "buyer@example.com", "CUSTOMER"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(createBookingUseCase, never()).createBooking(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void tenantStaffCanValidateOnlyTicketsForTheirTenant() {
        GetBookingUseCase getBookingUseCase = mock(GetBookingUseCase.class);
        ValidateTicketUseCase validateTicketUseCase = mock(ValidateTicketUseCase.class);
        EventTenantLookup eventTenantLookup = mock(EventTenantLookup.class);
        Ticket ticket = Ticket.builder().id("ticket-123").bookingId("booking-123").build();
        Booking booking = Booking.builder().id("booking-123").eventId("event-123").build();
        when(getBookingUseCase.getTicketById("ticket-123")).thenReturn(Optional.of(ticket));
        when(getBookingUseCase.getById("booking-123")).thenReturn(Optional.of(booking));
        when(eventTenantLookup.findTenantId("event-123")).thenReturn("tenant-a");
        BookingController controller = new BookingController(
                mock(CreateBookingUseCase.class),
                getBookingUseCase,
                validateTicketUseCase,
                eventTenantLookup,
                mock(CancelBookingUseCase.class));

        assertThatThrownBy(() -> controller.validateTicket(
                "ticket-123", "qr-hash", "CUSTOMER", "tenant-b", "EVENT_ORGANIZER"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        verify(validateTicketUseCase, never()).validateAndUseTicket("ticket-123", "qr-hash");
    }

    private BookingInventoryDefinition definitionWithFields(List<BookingRegistrationField> fields) {
        return new BookingInventoryDefinition(
                "event-123", "event-123", "tier-123", new BigDecimal("10.00"),
                InventoryMode.GENERAL_ADMISSION, 100, List.of(), null, fields);
    }

    private CreateBookingRequest bookingRequest(int quantity) {
        CreateBookingRequest request = new CreateBookingRequest();
        request.setEventId("event-123");
        request.setTicketTierId("tier-123");
        request.setQuantity(quantity);
        request.setTotalPrice(BigDecimal.TEN.multiply(BigDecimal.valueOf(quantity)));
        return request;
    }
}
