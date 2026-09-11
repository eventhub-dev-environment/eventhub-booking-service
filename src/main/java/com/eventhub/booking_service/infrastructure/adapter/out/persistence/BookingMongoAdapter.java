package com.eventhub.booking_service.infrastructure.adapter.out.persistence;

import com.eventhub.booking_service.domain.model.Booking;
import com.eventhub.booking_service.domain.model.Ticket;
import com.eventhub.booking_service.domain.port.out.BookingRepositoryPort;
import com.eventhub.booking_service.infrastructure.adapter.out.persistence.entity.BookingDocument;
import com.eventhub.booking_service.infrastructure.adapter.out.persistence.entity.TicketDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class BookingMongoAdapter implements BookingRepositoryPort {

    private final SpringDataMongoBookingRepository bookingRepository;
    private final SpringDataMongoTicketRepository ticketRepository;

    @Override
    public Booking save(Booking booking) {
        BookingDocument doc = mapToBookingDocument(booking);
        if (doc.getCreatedAt() == null) {
            doc.setCreatedAt(LocalDateTime.now());
        }
        doc.setUpdatedAt(LocalDateTime.now());

        BookingDocument saved = bookingRepository.save(doc);
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
        TicketDocument doc = TicketDocument.builder()
                .id(ticket.getId())
                .bookingId(ticket.getBookingId())
                .eventId(ticket.getEventId())
                .ticketTierId(ticket.getTicketTierId())
                .qrCodeHash(ticket.getQrCodeHash())
                .status(ticket.getStatus())
                .issuedAt(ticket.getIssuedAt() != null ? ticket.getIssuedAt() : LocalDateTime.now())
                .build();

        TicketDocument saved = ticketRepository.save(doc);
        return mapToTicketDomain(saved);
    }

    @Override
    public List<Ticket> findTicketsByBookingId(String bookingId) {
        return ticketRepository.findByBookingId(bookingId).stream()
                .map(this::mapToTicketDomain)
                .toList();
    }

    private BookingDocument mapToBookingDocument(Booking domain) {
        return BookingDocument.builder()
                .id(domain.getId())
                .eventId(domain.getEventId())
                .ticketTierId(domain.getTicketTierId())
                .userId(domain.getUserId())
                .quantity(domain.getQuantity())
                .totalPrice(domain.getTotalPrice())
                .status(domain.getStatus())
                .expiresAt(domain.getExpiresAt())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .build();
    }

    private Booking mapToBookingDomain(BookingDocument doc) {
        return Booking.builder()
                .id(doc.getId())
                .eventId(doc.getEventId())
                .ticketTierId(doc.getTicketTierId())
                .userId(doc.getUserId())
                .quantity(doc.getQuantity())
                .totalPrice(doc.getTotalPrice())
                .status(doc.getStatus())
                .expiresAt(doc.getExpiresAt())
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .build();
    }

    private Ticket mapToTicketDomain(TicketDocument doc) {
        return Ticket.builder()
                .id(doc.getId())
                .bookingId(doc.getBookingId())
                .eventId(doc.getEventId())
                .ticketTierId(doc.getTicketTierId())
                .qrCodeHash(doc.getQrCodeHash())
                .status(doc.getStatus())
                .issuedAt(doc.getIssuedAt())
                .build();
    }
}