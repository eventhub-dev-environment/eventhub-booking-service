package com.eventhub.booking_service.application.usecase;

import com.eventhub.booking_service.domain.event.BookingCreatedEvent;
import com.eventhub.booking_service.domain.model.Booking;
import com.eventhub.booking_service.domain.model.BookingStatus;
import com.eventhub.booking_service.domain.model.Ticket;
import com.eventhub.booking_service.domain.model.TicketStatus;
import com.eventhub.booking_service.domain.port.in.CreateBookingUseCase;
import com.eventhub.booking_service.domain.port.in.GetBookingUseCase;
import com.eventhub.booking_service.domain.port.in.ValidateTicketUseCase;
import com.eventhub.booking_service.domain.port.out.BookingEventPublisherPort;
import com.eventhub.booking_service.domain.port.out.BookingRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BookingService implements CreateBookingUseCase, GetBookingUseCase, ValidateTicketUseCase {

    private final BookingRepositoryPort bookingRepositoryPort;
    private final BookingEventPublisherPort eventPublisherPort;

    @Override
    public Booking createBooking(Booking booking) {
        booking.setStatus(BookingStatus.PENDING_PAYMENT);
        booking.setExpiresAt(LocalDateTime.now().plusMinutes(10)); // Bloqueo de 10 minutos para pago

        Booking savedBooking = bookingRepositoryPort.save(booking);

        // Notificación asíncrona a RabbitMQ
        BookingCreatedEvent event = BookingCreatedEvent.builder()
                .bookingId(savedBooking.getId())
                .eventId(savedBooking.getEventId())
                .ticketTierId(savedBooking.getTicketTierId())
                .userId(savedBooking.getUserId())
                .quantity(savedBooking.getQuantity())
                .totalPrice(savedBooking.getTotalPrice())
                .createdAt(savedBooking.getCreatedAt())
                .build();

        eventPublisherPort.publishBookingCreated(event);

        return savedBooking;
    }

    @Override
    public Optional<Booking> getById(String id) {
        return bookingRepositoryPort.findById(id);
    }

    @Override
    public List<Booking> getByUserId(String userId) {
        return bookingRepositoryPort.findByUserId(userId);
    }

    @Override
    public List<Ticket> getTicketsByBookingId(String bookingId) {
        return bookingRepositoryPort.findTicketsByBookingId(bookingId);
    }

    @Override
    public Optional<Ticket> getTicketById(String ticketId) {
        return bookingRepositoryPort.findTicketById(ticketId);
    }

    @Override
    public Ticket validateAndUseTicket(String ticketId, String qrCodeHash) {
        Ticket ticket = bookingRepositoryPort.findTicketById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("El ticket especificado no existe."));

        if (!ticket.getQrCodeHash().equals(qrCodeHash)) {
            throw new IllegalStateException("CÓDIGO QR INVÁLIDO O FALSIFICADO.");
        }

        if (ticket.getStatus() == TicketStatus.USED) {
            throw new IllegalStateException("EL TICKET YA FUE UTILIZADO ANTERIORMENTE.");
        }

        if (ticket.getStatus() == TicketStatus.CANCELLED) {
            throw new IllegalStateException("EL TICKET SE ENCUENTRA CANCELADO.");
        }

        // Marcar ticket como UTILIZADO
        ticket.setStatus(TicketStatus.USED);
        return bookingRepositoryPort.saveTicket(ticket);
    }
}