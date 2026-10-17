package com.eventhub.booking_service.infrastructure.adapter.out.persistence;

import com.eventhub.booking_service.domain.model.Booking;
import com.eventhub.booking_service.domain.model.Ticket;
import com.eventhub.booking_service.domain.port.out.BookingRepositoryPort;
import com.eventhub.booking_service.infrastructure.adapter.out.persistence.entity.BookingEntity;
import com.eventhub.booking_service.infrastructure.adapter.out.persistence.entity.TicketEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class BookingJpaAdapter implements BookingRepositoryPort {

    private final SpringDataJpaBookingRepository bookingRepository;
    private final SpringDataJpaTicketRepository ticketRepository;

    @Override
    public Booking save(Booking booking) {
        BookingEntity entity = mapToBookingEntity(booking);
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(LocalDateTime.now());
        }
        entity.setUpdatedAt(LocalDateTime.now());

        BookingEntity saved = bookingRepository.save(entity);
        return mapToBookingDomain(saved);
    }

    @Override
    public Optional<Booking> findById(String id) {
        return bookingRepository.findById(id).map(this::mapToBookingDomain);
    }

    @Override
    public List<Booking> findByUserId(String userId) {
        return bookingRepository.findByUserId(userId).stream()
                .map(this::mapToBookingDomain)
                .toList();
    }

    @Override
    public Ticket saveTicket(Ticket ticket) {
        TicketEntity entity = TicketEntity.builder()
                .id(ticket.getId())
                .bookingId(ticket.getBookingId())
                .eventId(ticket.getEventId())
                .sessionId(ticket.getSessionId())
                .ticketTierId(ticket.getTicketTierId())
                .seatId(ticket.getSeatId())
                .qrCodeHash(ticket.getQrCodeHash())
                .status(ticket.getStatus())
                .issuedAt(ticket.getIssuedAt() != null ? ticket.getIssuedAt() : LocalDateTime.now())
                .build();

        TicketEntity saved = ticketRepository.save(entity);
        return mapToTicketDomain(saved);
    }

    @Override
    public List<Ticket> findTicketsByBookingId(String bookingId) {
        return ticketRepository.findByBookingId(bookingId).stream()
                .map(this::mapToTicketDomain)
                .toList();
    }

    @Override
    public Optional<Ticket> findTicketById(String id) {
        return ticketRepository.findById(id).map(this::mapToTicketDomain);
    }

    private BookingEntity mapToBookingEntity(Booking domain) {
        return BookingEntity.builder()
                .id(domain.getId())
                .eventId(domain.getEventId())
                .sessionId(domain.getSessionId())
                .ticketTierId(domain.getTicketTierId())
                .userId(domain.getUserId())
                .userEmail(domain.getUserEmail())
                .quantity(domain.getQuantity())
                .attendeeData(domain.getAttendeeData() == null ? List.of() : domain.getAttendeeData())
                .totalPrice(domain.getTotalPrice())
                .status(domain.getStatus())
                .expiresAt(domain.getExpiresAt())
                .seatIds(domain.getSeatIds() == null ? List.of() : domain.getSeatIds())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .build();
    }

    private Booking mapToBookingDomain(BookingEntity entity) {
        return Booking.builder()
                .id(entity.getId())
                .eventId(entity.getEventId())
                .sessionId(entity.getSessionId())
                .ticketTierId(entity.getTicketTierId())
                .userId(entity.getUserId())
                .userEmail(entity.getUserEmail())
                .quantity(entity.getQuantity())
                .attendeeData(entity.getAttendeeData() == null ? List.of() : entity.getAttendeeData())
                .totalPrice(entity.getTotalPrice())
                .status(entity.getStatus())
                .expiresAt(entity.getExpiresAt())
                .seatIds(entity.getSeatIds() == null ? List.of() : entity.getSeatIds())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private Ticket mapToTicketDomain(TicketEntity entity) {
        return Ticket.builder()
                .id(entity.getId())
                .bookingId(entity.getBookingId())
                .eventId(entity.getEventId())
                .sessionId(entity.getSessionId())
                .ticketTierId(entity.getTicketTierId())
                .seatId(entity.getSeatId())
                .qrCodeHash(entity.getQrCodeHash())
                .status(entity.getStatus())
                .issuedAt(entity.getIssuedAt())
                .build();
    }
}