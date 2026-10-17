package com.eventhub.booking_service.infrastructure.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RabbitMQConfigTest {

    private final RabbitMQConfig config = new RabbitMQConfig();

    @Test
    void sendsFailedPaymentMessagesToTheirDeadLetterQueue() {
        assertThat(config.paymentCompletedQueue().getArguments())
                .containsEntry("x-dead-letter-exchange", RabbitMQConfig.PAYMENT_DEAD_LETTER_EXCHANGE)
                .containsEntry("x-dead-letter-routing-key", RabbitMQConfig.PAYMENT_COMPLETED_DLQ_KEY);
        assertThat(config.paymentFailedQueue().getArguments())
                .containsEntry("x-dead-letter-exchange", RabbitMQConfig.PAYMENT_DEAD_LETTER_EXCHANGE)
                .containsEntry("x-dead-letter-routing-key", RabbitMQConfig.PAYMENT_FAILED_DLQ_KEY);
    }

    @Test
    void declaresDurableDeadLetterQueues() {
        assertThat(config.paymentCompletedDlq().isDurable()).isTrue();
        assertThat(config.paymentFailedDlq().isDurable()).isTrue();
    }
}
