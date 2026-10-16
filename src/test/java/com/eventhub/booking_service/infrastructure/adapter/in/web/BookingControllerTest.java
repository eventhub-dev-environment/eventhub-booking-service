package com.eventhub.booking_service.infrastructure.adapter.in.web;

import com.eventhub.booking_service.domain.model.Booking;
import com.eventhub.booking_service.domain.port.in.CreateBookingUseCase;
import com.eventhub.booking_service.domain.port.in.GetBookingUseCase;
import com.eventhub.booking_service.domain.port.in.ValidateTicketUseCase;
import com.eventhub.booking_service.infrastructure.adapter.in.web.dto.CreateBookingRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

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
                mock(ValidateTicketUseCase.class));
        CreateBookingRequest request = new CreateBookingRequest();
        request.setEventId("event-123");
        request.setTicketTierId("tier-123");
        request.setQuantity(2);
        request.setTotalPrice(new BigDecimal("20.00"));

        controller.createBooking(request, "signed-user-id", "buyer@example.com");

        assertThat(submittedBooking.get().getUserId()).isEqualTo("signed-user-id");
        assertThat(submittedBooking.get().getUserEmail()).isEqualTo("buyer@example.com");
    }
}
