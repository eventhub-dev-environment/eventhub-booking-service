package com.eventhub.booking_service.infrastructure.adapter.in.web;

import com.eventhub.booking_service.domain.model.Booking;
import com.eventhub.booking_service.domain.model.Ticket;
import com.eventhub.booking_service.domain.port.in.CreateBookingUseCase;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class BookingControllerTest {

    @Test
    void createsBookingUsingUserIdFromGatewayHeader() {
        AtomicReference<Booking> submittedBooking = new AtomicReference<>();
        CreateBookingUseCase createBookingUseCase = booking -> {
            submittedBooking.set(booking);
            return booking;
        };
        BookingController controller = new BookingController(
                createBookingUseCase,
                mock(GetBookingUseCase.class),
                mock(ValidateTicketUseCase.class),
                mock(EventTenantLookup.class));
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
                mock(EventTenantLookup.class));

        assertThatThrownBy(() -> controller.getById("booking-123", "different-user", "CUSTOMER"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void customerCannotCreateBookingWithoutCustomerRole() {
        BookingController controller = new BookingController(
                mock(CreateBookingUseCase.class),
                mock(GetBookingUseCase.class),
                mock(ValidateTicketUseCase.class),
                mock(EventTenantLookup.class));
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
                mock(EventTenantLookup.class));
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
                eventTenantLookup);

        assertThatThrownBy(() -> controller.validateTicket(
                "ticket-123", "qr-hash", "CUSTOMER", "tenant-b", "EVENT_ORGANIZER"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        verify(validateTicketUseCase, never()).validateAndUseTicket("ticket-123", "qr-hash");
    }
}
