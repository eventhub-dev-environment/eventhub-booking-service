package com.eventhub.booking_service.infrastructure.adapter.in.messaging;

import com.eventhub.booking_service.infrastructure.config.RabbitMQConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class PaymentFailedEventListener {

    @RabbitListener(queues = RabbitMQConfig.PAYMENT_FAILED_QUEUE)
    public void handlePaymentFailed(Map<String, Object> event) {
        Object bookingId = event.get("bookingId");
        Object paymentId = event.get("paymentId");
        log.info(
                "Pago rechazado para bookingId={}, paymentId={}; se mantiene la retención hasta su expiración para permitir un reintento",
                bookingId,
                paymentId);
    }
}
