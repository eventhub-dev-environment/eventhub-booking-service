package com.eventhub.booking_service.infrastructure.adapter.in.messaging;

import com.eventhub.booking_service.domain.model.Booking;
import com.eventhub.booking_service.domain.model.BookingStatus;
import com.eventhub.booking_service.domain.model.Ticket;
import com.eventhub.booking_service.domain.model.TicketStatus;
import com.eventhub.booking_service.domain.port.out.BookingRepositoryPort;
import com.eventhub.booking_service.infrastructure.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private final BookingRepositoryPort bookingRepositoryPort;

    @RabbitListener(queues = RabbitMQConfig.PAYMENT_COMPLETED_QUEUE)
    public void handlePaymentCompleted(Map<String, Object> event) {
        String bookingId = (String) event.get("bookingId");
        String transactionId = (String) event.get("transactionId");

        log.info("Evento de pago completado recibido para bookingId: {}, transactionId: {}", bookingId, transactionId);

        bookingRepositoryPort.findById(bookingId).ifPresent(booking -> {
            if (booking.getStatus() == BookingStatus.PENDING_PAYMENT) {
                // 1. Confirmar Reserva
                booking.setStatus(BookingStatus.CONFIRMED);
                booking.setUpdatedAt(LocalDateTime.now());
                bookingRepositoryPort.save(booking);

                // 2. Generar Tickets (según la cantidad reservada)
                for (int i = 0; i < booking.getQuantity(); i++) {
                    Ticket ticket = Ticket.builder()
                            .bookingId(booking.getId())
                            .eventId(booking.getEventId())
                            .ticketTierId(booking.getTicketTierId())
                            .qrCodeHash(UUID.randomUUID().toString())
                            .status(TicketStatus.ISSUED)
                            .issuedAt(LocalDateTime.now())
                            .build();

                    bookingRepositoryPort.saveTicket(ticket);
                }

                log.info("Reserva {} confirmada exitosamente y tickets emitidos.", bookingId);
            }
        });
    }
}