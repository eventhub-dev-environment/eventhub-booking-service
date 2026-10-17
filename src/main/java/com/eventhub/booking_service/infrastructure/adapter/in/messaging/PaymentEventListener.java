package com.eventhub.booking_service.infrastructure.adapter.in.messaging;

import com.eventhub.booking_service.domain.event.BookingConfirmedEvent;
import com.eventhub.booking_service.domain.model.Booking;
import com.eventhub.booking_service.domain.model.BookingStatus;
import com.eventhub.booking_service.domain.model.Ticket;
import com.eventhub.booking_service.domain.model.TicketStatus;
import com.eventhub.booking_service.domain.port.out.BookingEventPublisherPort;
import com.eventhub.booking_service.domain.port.out.BookingRepositoryPort;
import com.eventhub.booking_service.domain.port.out.InventoryRepositoryPort;
import com.eventhub.booking_service.infrastructure.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private final BookingRepositoryPort bookingRepositoryPort;
    private final BookingEventPublisherPort eventPublisherPort;
    private final InventoryRepositoryPort inventoryRepositoryPort;
    @Value("${payment.currency:COP}")
    private String paymentCurrency = "COP";

    @RabbitListener(queues = RabbitMQConfig.PAYMENT_COMPLETED_QUEUE)
    @Transactional
    public void handlePaymentCompleted(Map<String, Object> event) {
        String bookingId = (String) event.get("bookingId");
        String transactionId = (String) event.get("transactionId");
        BigDecimal paymentAmount = parseAmount(event.get("amount"));
        if (bookingId == null || bookingId.isBlank() || transactionId == null || transactionId.isBlank()
                || paymentAmount == null) {
            log.error("Evento de pago aprobado inválido: faltan bookingId, transactionId o amount");
            return;
        }

        log.info("Evento de pago completado recibido para bookingId: {}, transactionId: {}", bookingId, transactionId);

        bookingRepositoryPort.findByIdForUpdate(bookingId).ifPresentOrElse(booking -> {
            if (booking.getStatus() == BookingStatus.CONFIRMED
                    || booking.getStatus() == BookingStatus.PAYMENT_REVIEW_REQUIRED) {
                log.info("El pago de la reserva {} ya fue procesado; se ignora la entrega repetida", bookingId);
                return;
            }

            LocalDateTime now = LocalDateTime.now();
            boolean paymentMatches = booking.getTotalPrice() != null
                    && booking.getTotalPrice().compareTo(paymentAmount) == 0;
            boolean currencyMatches = paymentCurrency.equals(event.get("currency"));
            boolean reservationIsLive = booking.getStatus() == BookingStatus.PENDING_PAYMENT
                    && booking.getExpiresAt() != null
                    && Boolean.TRUE.equals(booking.getInventoryHeld())
                    && booking.getExpiresAt().isAfter(now);
            if (!paymentMatches || !currencyMatches || !reservationIsLive) {
                if (booking.getStatus() == BookingStatus.PENDING_PAYMENT
                        && Boolean.TRUE.equals(booking.getInventoryHeld())) {
                    inventoryRepositoryPort.release(booking);
                    booking.setInventoryHeld(false);
                }
                booking.setStatus(BookingStatus.PAYMENT_REVIEW_REQUIRED);
                booking.setPaymentTransactionId(transactionId);
                booking.setUpdatedAt(now);
                bookingRepositoryPort.save(booking);
                log.error(
                        "Pago aprobado requiere revisión manual; bookingId={}, transactionId={}, amountMatches={}, currencyMatches={}, reservationLive={}",
                        bookingId, transactionId, paymentMatches, currencyMatches, reservationIsLive);
                return;
            }

            inventoryRepositoryPort.confirm(booking);
            booking.setInventoryHeld(false);
            booking.setStatus(BookingStatus.CONFIRMED);
            booking.setPaymentTransactionId(transactionId);
            booking.setUpdatedAt(now);
            bookingRepositoryPort.save(booking);

            List<BookingConfirmedEvent.TicketDto> ticketDtos = new ArrayList<>();

            for (int i = 0; i < booking.getQuantity(); i++) {
                Ticket ticket = Ticket.builder()
                        .bookingId(booking.getId())
                        .eventId(booking.getEventId())
                        .sessionId(booking.getSessionId())
                        .ticketTierId(booking.getTicketTierId())
                        .seatId(booking.getSeatIds() != null && i < booking.getSeatIds().size()
                                ? booking.getSeatIds().get(i)
                                : null)
                        .qrCodeHash(UUID.randomUUID().toString())
                        .status(TicketStatus.ISSUED)
                        .issuedAt(now)
                        .build();

                Ticket savedTicket = bookingRepositoryPort.saveTicket(ticket);

                ticketDtos.add(BookingConfirmedEvent.TicketDto.builder()
                        .ticketId(savedTicket.getId())
                        .qrCodeHash(savedTicket.getQrCodeHash())
                        .ticketTierId(savedTicket.getTicketTierId())
                        .seatId(savedTicket.getSeatId())
                        .build());
            }

            BookingConfirmedEvent confirmedEvent = BookingConfirmedEvent.builder()
                    .bookingId(booking.getId())
                    .eventId(booking.getEventId())
                    .sessionId(booking.getSessionId())
                    .userId(booking.getUserId())
                    .userEmail(booking.getUserEmail())
                    .transactionId(transactionId)
                    .totalPrice(booking.getTotalPrice())
                    .tickets(ticketDtos)
                    .build();

            eventPublisherPort.publishBookingConfirmed(confirmedEvent);
            log.info("Reserva {} confirmada exitosamente, tickets emitidos y evento de confirmación publicado.", bookingId);
        }, () -> log.error("Se recibió un pago aprobado para una reserva inexistente: {}", bookingId));
    }

    private BigDecimal parseAmount(Object amount) {
        if (amount instanceof BigDecimal decimal) {
            return decimal;
        }
        if (amount instanceof Number || amount instanceof String) {
            try {
                return new BigDecimal(amount.toString());
            } catch (NumberFormatException exception) {
                log.error("El evento de pago aprobado contiene un importe inválido");
            }
        }
        return null;
    }
}