package com.eventhub.booking_service.infrastructure.adapter.in.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PaymentEventListener {

    // Escucha eventos del payment-service cuando un pago es aprobado
    @RabbitListener(queues = "payment.completed.queue")
    public void handlePaymentCompleted(Object paymentEvent) {
        log.info("Evento de pago recibido en booking-service: {}", paymentEvent);
        // Lógica para cambiar estado de Booking a CONFIRMED y emitir Ticket con QR
    }
}