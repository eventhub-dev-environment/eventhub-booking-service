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
import com.eventhub.booking_service.infrastructure.adapter.out.persistence.SpringDataJpaPaymentInboxRepository;
import com.eventhub.booking_service.infrastructure.adapter.out.persistence.entity.PaymentInboxEventEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private final BookingRepositoryPort bookingRepositoryPort;
    private final BookingEventPublisherPort eventPublisherPort;
    private final InventoryRepositoryPort inventoryRepositoryPort;
    private final SpringDataJpaPaymentInboxRepository paymentInboxRepository;
    @Value("${payment.currency:COP}")
    private String paymentCurrency = "COP";

    @RabbitListener(queues = RabbitMQConfig.PAYMENT_COMPLETED_QUEUE)
    @Transactional
    public void handlePaymentCompleted(
            Map<String, Object> event,
            @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId) {
        String bookingId = (String) event.get("bookingId");
        String transactionId = (String) event.get("transactionId");
        String eventId = resolveEventId(messageId, "payment.completed", bookingId, transactionId);
        if (paymentInboxRepository.existsById(eventId)) {
            log.info("Se ignora evento de pago ya procesado: {}", eventId);
            return;
        }
        BigDecimal paymentAmount = parseAmount(event.get("amount"));
        if (bookingId == null || bookingId.isBlank() || transactionId == null || transactionId.isBlank()
                || paymentAmount == null) {
            log.error("Evento de pago aprobado inválido: faltan bookingId, transactionId o amount");
            throw new IllegalArgumentException("Evento de pago aprobado inválido");
        }

        log.info("Evento de pago completado recibido para bookingId: {}, transactionId: {}", bookingId, transactionId);

        Optional<Booking> bookingResult = bookingRepositoryPort.findByIdForUpdate(bookingId);
        if (bookingResult.isEmpty()) {
            log.error("Se recibió un pago aprobado para una reserva inexistente: {}", bookingId);
            throw new IllegalStateException("La reserva asociada al pago no existe");
        }
        bookingResult.ifPresent(booking -> {
            if (booking.getStatus() == BookingStatus.CONFIRMED
                    || booking.getStatus() == BookingStatus.PAYMENT_REVIEW_REQUIRED) {
                log.info("El pago de la reserva {} ya fue procesado; se ignora la entrega repetida", bookingId);
                return;
            }
            if ((booking.getStatus() == BookingStatus.CANCELLATION_REQUESTED
                    || booking.getStatus() == BookingStatus.CANCELLATION_REVIEW_REQUIRED
                    || booking.getStatus() == BookingStatus.CANCELLED)
                    && transactionId.equals(booking.getPaymentTransactionId())) {
                log.info("Se ignora el pago repetido de la reserva {} durante su cancelación", bookingId);
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
        });
        saveInboxEvent(eventId, "payment.completed");
    }

    @RabbitListener(queues = RabbitMQConfig.PAYMENT_REFUNDED_QUEUE)
    @Transactional
    public void handlePaymentRefunded(
            Map<String, Object> event,
            @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId) {
        String bookingId = (String) event.get("bookingId");
        String transactionId = (String) event.get("transactionId");
        String eventId = resolveEventId(messageId, "payment.refunded", bookingId, transactionId);
        if (paymentInboxRepository.existsById(eventId)) {
            return;
        }
        if (bookingId == null || bookingId.isBlank() || transactionId == null || transactionId.isBlank()) {
            throw new IllegalArgumentException("Evento de reembolso inválido");
        }

        Booking booking = bookingRepositoryPort.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new IllegalStateException("La reserva reembolsada no existe"));
        if (booking.getStatus() == BookingStatus.CANCELLED
                && transactionId.equals(booking.getPaymentTransactionId())) {
            saveInboxEvent(eventId, "payment.refunded");
            return;
        }
        if ((booking.getStatus() != BookingStatus.CANCELLATION_REQUESTED
                && booking.getStatus() != BookingStatus.CANCELLATION_REVIEW_REQUIRED)
                || !transactionId.equals(booking.getPaymentTransactionId())) {
            throw new IllegalStateException("El reembolso no coincide con una cancelación pendiente");
        }

        if (bookingRepositoryPort.countTicketsByBookingIdAndStatus(
                bookingId, TicketStatus.ISSUED) != booking.getQuantity()) {
            booking.setStatus(BookingStatus.CANCELLATION_REVIEW_REQUIRED);
            booking.setUpdatedAt(LocalDateTime.now());
            bookingRepositoryPort.save(booking);
            saveInboxEvent(eventId, "payment.refunded");
            return;
        }
        inventoryRepositoryPort.releaseConfirmed(booking);
        int cancelledTickets = bookingRepositoryPort.cancelIssuedTickets(bookingId);
        if (cancelledTickets != booking.getQuantity()) {
            throw new IllegalStateException("No se pudieron cancelar todas las entradas de la reserva");
        }
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setUpdatedAt(LocalDateTime.now());
        bookingRepositoryPort.save(booking);
        saveInboxEvent(eventId, "payment.refunded");
    }

    @RabbitListener(queues = RabbitMQConfig.PAYMENT_REFUND_FAILED_QUEUE)
    @Transactional
    public void handlePaymentRefundFailed(
            Map<String, Object> event,
            @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId) {
        String bookingId = (String) event.get("bookingId");
        String transactionId = (String) event.get("transactionId");
        String eventId = resolveEventId(messageId, "payment.refund.failed", bookingId, transactionId);
        if (paymentInboxRepository.existsById(eventId)) {
            return;
        }
        if (bookingId == null || bookingId.isBlank() || transactionId == null || transactionId.isBlank()) {
            throw new IllegalArgumentException("Evento de reembolso fallido inválido");
        }

        Booking booking = bookingRepositoryPort.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new IllegalStateException("La reserva asociada al reembolso no existe"));
        if (booking.getStatus() == BookingStatus.CANCELLATION_REQUESTED
                && transactionId.equals(booking.getPaymentTransactionId())) {
            booking.setStatus(BookingStatus.CANCELLATION_REVIEW_REQUIRED);
            booking.setUpdatedAt(LocalDateTime.now());
            bookingRepositoryPort.save(booking);
        }
        saveInboxEvent(eventId, "payment.refund.failed");
    }

    private void saveInboxEvent(String eventId, String eventType) {
        paymentInboxRepository.save(PaymentInboxEventEntity.builder()
                .eventId(eventId)
                .eventType(eventType)
                .processedAt(LocalDateTime.now())
                .build());
    }

    private String resolveEventId(String messageId, String eventType, String bookingId, String transactionId) {
        if (messageId != null && !messageId.isBlank()) {
            return messageId;
        }
        return "legacy:" + eventType + ":" + bookingId + ":" + transactionId;
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