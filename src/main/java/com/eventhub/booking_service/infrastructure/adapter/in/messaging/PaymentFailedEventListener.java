package com.eventhub.booking_service.infrastructure.adapter.in.messaging;

import com.eventhub.booking_service.infrastructure.adapter.out.persistence.SpringDataJpaPaymentInboxRepository;
import com.eventhub.booking_service.infrastructure.adapter.out.persistence.entity.PaymentInboxEventEntity;
import com.eventhub.booking_service.infrastructure.config.RabbitMQConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Component
public class PaymentFailedEventListener {

    private final SpringDataJpaPaymentInboxRepository paymentInboxRepository;

    public PaymentFailedEventListener(SpringDataJpaPaymentInboxRepository paymentInboxRepository) {
        this.paymentInboxRepository = paymentInboxRepository;
    }

    @RabbitListener(queues = RabbitMQConfig.PAYMENT_FAILED_QUEUE)
    @Transactional
    public void handlePaymentFailed(
            Map<String, Object> event,
            @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId) {
        Object bookingId = event.get("bookingId");
        Object paymentId = event.get("paymentId");
        if (!(bookingId instanceof String bookingIdValue) || bookingIdValue.isBlank()
                || !(paymentId instanceof String paymentIdValue) || paymentIdValue.isBlank()) {
            log.error("Evento de pago rechazado inválido: faltan bookingId o paymentId");
            throw new IllegalArgumentException("Evento de pago rechazado inválido");
        }
        String eventId = messageId == null || messageId.isBlank()
                ? "legacy:payment.failed:" + bookingIdValue + ":" + paymentIdValue
                : messageId;
        if (paymentInboxRepository.existsById(eventId)) {
            log.info("Se ignora evento de pago rechazado ya procesado: {}", eventId);
            return;
        }
        log.info(
                "Pago rechazado para bookingId={}, paymentId={}; se mantiene la retención hasta su expiración para permitir un reintento",
                bookingIdValue,
                paymentIdValue);
        paymentInboxRepository.save(PaymentInboxEventEntity.builder()
                .eventId(eventId)
                .eventType("payment.failed")
                .processedAt(LocalDateTime.now())
                .build());
    }
}
