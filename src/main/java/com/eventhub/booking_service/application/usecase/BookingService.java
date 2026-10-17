package com.eventhub.booking_service.application.usecase;

import com.eventhub.booking_service.domain.event.BookingCreatedEvent;
import com.eventhub.booking_service.domain.exception.InventoryUnavailableException;
import com.eventhub.booking_service.domain.model.Booking;
import com.eventhub.booking_service.domain.model.BookingInventoryDefinition;
import com.eventhub.booking_service.domain.model.BookingStatus;
import com.eventhub.booking_service.domain.model.InventoryMode;
import com.eventhub.booking_service.domain.model.Ticket;
import com.eventhub.booking_service.domain.model.TicketStatus;
import com.eventhub.booking_service.domain.port.in.CreateBookingUseCase;
import com.eventhub.booking_service.domain.port.in.GetBookingUseCase;
import com.eventhub.booking_service.domain.port.in.ValidateTicketUseCase;
import com.eventhub.booking_service.domain.port.out.BookingEventPublisherPort;
import com.eventhub.booking_service.domain.port.out.BookingRepositoryPort;
import com.eventhub.booking_service.domain.port.out.InventoryRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BookingService implements CreateBookingUseCase, GetBookingUseCase, ValidateTicketUseCase {

    private final BookingRepositoryPort bookingRepositoryPort;
    private final BookingEventPublisherPort eventPublisherPort;
    private final InventoryRepositoryPort inventoryRepositoryPort;

    @Override
    @Transactional
    public Booking createBooking(Booking booking, BookingInventoryDefinition inventoryDefinition) {
        if (booking == null || inventoryDefinition == null
                || booking.getEventId() == null
                || booking.getSessionId() == null
                || booking.getTicketTierId() == null
                || inventoryDefinition.inventoryMode() == null
                || inventoryDefinition.unitPrice() == null
                || inventoryDefinition.unitPrice().signum() < 0
                || inventoryDefinition.capacity() == null
                || inventoryDefinition.capacity() < 1
                || inventoryDefinition.seats() == null
                || (inventoryDefinition.inventoryMode() == InventoryMode.RESERVED_SEATING
                    && inventoryDefinition.seats().size() != inventoryDefinition.capacity())
                || (inventoryDefinition.inventoryMode() == InventoryMode.GENERAL_ADMISSION
                    && !inventoryDefinition.seats().isEmpty())
                || booking.getQuantity() == null || booking.getQuantity() < 1
                || !booking.getEventId().equals(inventoryDefinition.eventId())
                || !booking.getSessionId().equals(inventoryDefinition.sessionId())
                || !booking.getTicketTierId().equals(inventoryDefinition.ticketTierId())) {
            throw new InventoryUnavailableException("La selección de inventario no es válida");
        }
        if (inventoryDefinition.inventoryMode() == InventoryMode.RESERVED_SEATING
                && (booking.getSeatIds() == null || booking.getSeatIds().size() != booking.getQuantity()
                    || booking.getSeatIds().stream().distinct().count() != booking.getSeatIds().size())) {
            throw new InventoryUnavailableException("Seleccione un asiento distinto por cada entrada");
        }
        if (inventoryDefinition.inventoryMode() == InventoryMode.GENERAL_ADMISSION
                && booking.getSeatIds() != null && !booking.getSeatIds().isEmpty()) {
            throw new InventoryUnavailableException("La tarifa seleccionada no admite asientos numerados");
        }
        if (booking.getSeatIds() != null && booking.getSeatIds().stream().anyMatch(seatId ->
                inventoryDefinition.seats().stream().noneMatch(seat -> seat.seatId().equals(seatId)))) {
            throw new InventoryUnavailableException("Uno o más asientos no pertenecen a la tarifa seleccionada");
        }

        LocalDateTime now = LocalDateTime.now();
        booking.setStatus(BookingStatus.PENDING_PAYMENT);
        booking.setTotalPrice(inventoryDefinition.unitPrice().multiply(
                java.math.BigDecimal.valueOf(booking.getQuantity())).setScale(2, RoundingMode.HALF_UP));
        booking.setCreatedAt(now);
        booking.setExpiresAt(now.plusMinutes(10));
        booking.setUpdatedAt(now);

        Booking savedBooking = bookingRepositoryPort.save(booking);
        inventoryRepositoryPort.initialize(inventoryDefinition);
        inventoryRepositoryPort.hold(savedBooking, inventoryDefinition);
        savedBooking.setInventoryHeld(true);
        savedBooking = bookingRepositoryPort.save(savedBooking);

        BookingCreatedEvent event = BookingCreatedEvent.builder()
                .bookingId(savedBooking.getId())
                .eventId(savedBooking.getEventId())
                .sessionId(savedBooking.getSessionId())
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

    @Scheduled(fixedDelayString = "${booking.expiration.scan-interval-ms:15000}")
    @Transactional
    public void expirePendingBookings() {
        LocalDateTime now = LocalDateTime.now();
        List<String> expiredBookingIds = bookingRepositoryPort.findExpiredPendingBookingIds(now, 100);
        for (String bookingId : expiredBookingIds) {
            bookingRepositoryPort.findByIdForUpdate(bookingId).ifPresent(booking -> {
                if (booking.getStatus() == BookingStatus.PENDING_PAYMENT
                        && booking.getExpiresAt() != null
                        && !booking.getExpiresAt().isAfter(now)) {
                    if (Boolean.TRUE.equals(booking.getInventoryHeld())) {
                        inventoryRepositoryPort.release(booking);
                        booking.setInventoryHeld(false);
                    }
                    booking.setStatus(BookingStatus.EXPIRED);
                    booking.setUpdatedAt(now);
                    bookingRepositoryPort.save(booking);
                }
            });
        }
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