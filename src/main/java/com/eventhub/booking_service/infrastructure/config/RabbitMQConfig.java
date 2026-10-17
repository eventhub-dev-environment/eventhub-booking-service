package com.eventhub.booking_service.infrastructure.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String BOOKING_EXCHANGE = "booking.exchange";
    public static final String PAYMENT_EXCHANGE = "payment.exchange";
    public static final String PAYMENT_DEAD_LETTER_EXCHANGE = "booking.payment.dlx";

    public static final String BOOKING_CREATED_QUEUE = "booking.created.queue";
    public static final String BOOKING_CANCELLED_QUEUE = "booking.cancelled.queue";
    public static final String PAYMENT_COMPLETED_QUEUE = "payment.completed.queue";
    public static final String PAYMENT_FAILED_QUEUE = "payment.failed.queue";
    public static final String PAYMENT_COMPLETED_DLQ = "payment.completed.dlq";
    public static final String PAYMENT_FAILED_DLQ = "payment.failed.dlq";
    public static final String PAYMENT_REFUNDED_DLQ = "payment.refunded.dlq";
    public static final String PAYMENT_REFUND_FAILED_DLQ = "payment.refund.failed.dlq";

    public static final String BOOKING_CREATED_KEY = "booking.created";
    public static final String BOOKING_CANCELLED_KEY = "booking.cancelled";
    public static final String PAYMENT_COMPLETED_KEY = "payment.completed";
    public static final String PAYMENT_FAILED_KEY = "payment.failed";
    public static final String PAYMENT_REFUNDED_KEY = "payment.refunded";
    public static final String PAYMENT_REFUND_FAILED_KEY = "payment.refund.failed";
    public static final String PAYMENT_COMPLETED_DLQ_KEY = "payment.completed.dead";
    public static final String PAYMENT_FAILED_DLQ_KEY = "payment.failed.dead";
    public static final String PAYMENT_REFUNDED_DLQ_KEY = "payment.refunded.dead";
    public static final String PAYMENT_REFUND_FAILED_DLQ_KEY = "payment.refund.failed.dead";

    public static final String BOOKING_CONFIRMED_QUEUE = "booking.confirmed.queue";
    public static final String BOOKING_CONFIRMED_KEY = "booking.confirmed";
    public static final String PAYMENT_REFUNDED_QUEUE = "payment.refunded.queue";
    public static final String PAYMENT_REFUND_FAILED_QUEUE = "payment.refund.failed.queue";

    @Bean
    public TopicExchange bookingExchange() {
        return new TopicExchange(BOOKING_EXCHANGE);
    }

    @Bean
    public TopicExchange paymentExchange() {
        return new TopicExchange(PAYMENT_EXCHANGE);
    }

    @Bean
    public Queue bookingCreatedQueue() {
        return QueueBuilder.durable(BOOKING_CREATED_QUEUE).build();
    }

    @Bean
    public Queue bookingCancelledQueue() {
        return QueueBuilder.durable(BOOKING_CANCELLED_QUEUE).build();
    }

    @Bean
    public Queue paymentCompletedQueue() {
        return QueueBuilder.durable(PAYMENT_COMPLETED_QUEUE)
                .deadLetterExchange(PAYMENT_DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(PAYMENT_COMPLETED_DLQ_KEY)
                .build();
    }

    @Bean
    public Queue paymentFailedQueue() {
        return QueueBuilder.durable(PAYMENT_FAILED_QUEUE)
                .deadLetterExchange(PAYMENT_DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(PAYMENT_FAILED_DLQ_KEY)
                .build();
    }

    @Bean
    public Queue paymentRefundedQueue() {
        return QueueBuilder.durable(PAYMENT_REFUNDED_QUEUE)
                .deadLetterExchange(PAYMENT_DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(PAYMENT_REFUNDED_DLQ_KEY)
                .build();
    }

    @Bean
    public Queue paymentRefundFailedQueue() {
        return QueueBuilder.durable(PAYMENT_REFUND_FAILED_QUEUE)
                .deadLetterExchange(PAYMENT_DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(PAYMENT_REFUND_FAILED_DLQ_KEY)
                .build();
    }

    @Bean
    public DirectExchange paymentDeadLetterExchange() {
        return new DirectExchange(PAYMENT_DEAD_LETTER_EXCHANGE);
    }

    @Bean
    public Queue paymentCompletedDlq() {
        return QueueBuilder.durable(PAYMENT_COMPLETED_DLQ).build();
    }

    @Bean
    public Queue paymentFailedDlq() {
        return QueueBuilder.durable(PAYMENT_FAILED_DLQ).build();
    }

    @Bean
    public Queue paymentRefundedDlq() {
        return QueueBuilder.durable(PAYMENT_REFUNDED_DLQ).build();
    }

    @Bean
    public Queue paymentRefundFailedDlq() {
        return QueueBuilder.durable(PAYMENT_REFUND_FAILED_DLQ).build();
    }

    @Bean
    public Binding bookingCreatedBinding(Queue bookingCreatedQueue, TopicExchange bookingExchange) {
        return BindingBuilder.bind(bookingCreatedQueue).to(bookingExchange).with(BOOKING_CREATED_KEY);
    }

    @Bean
    public Binding bookingCancelledBinding(Queue bookingCancelledQueue, TopicExchange bookingExchange) {
        return BindingBuilder.bind(bookingCancelledQueue).to(bookingExchange).with(BOOKING_CANCELLED_KEY);
    }

    @Bean
    public Binding paymentCompletedBinding(Queue paymentCompletedQueue, TopicExchange paymentExchange) {
        return BindingBuilder.bind(paymentCompletedQueue).to(paymentExchange).with(PAYMENT_COMPLETED_KEY);
    }

    @Bean
    public Binding paymentFailedBinding(Queue paymentFailedQueue, TopicExchange paymentExchange) {
        return BindingBuilder.bind(paymentFailedQueue).to(paymentExchange).with(PAYMENT_FAILED_KEY);
    }

    @Bean
    public Binding paymentRefundedBinding(Queue paymentRefundedQueue, TopicExchange paymentExchange) {
        return BindingBuilder.bind(paymentRefundedQueue).to(paymentExchange).with(PAYMENT_REFUNDED_KEY);
    }

    @Bean
    public Binding paymentRefundFailedBinding(Queue paymentRefundFailedQueue, TopicExchange paymentExchange) {
        return BindingBuilder.bind(paymentRefundFailedQueue).to(paymentExchange).with(PAYMENT_REFUND_FAILED_KEY);
    }

    @Bean
    public Binding paymentCompletedDlqBinding(
            Queue paymentCompletedDlq,
            DirectExchange paymentDeadLetterExchange) {
        return BindingBuilder.bind(paymentCompletedDlq)
                .to(paymentDeadLetterExchange)
                .with(PAYMENT_COMPLETED_DLQ_KEY);
    }

    @Bean
    public Binding paymentFailedDlqBinding(
            Queue paymentFailedDlq,
            DirectExchange paymentDeadLetterExchange) {
        return BindingBuilder.bind(paymentFailedDlq)
                .to(paymentDeadLetterExchange)
                .with(PAYMENT_FAILED_DLQ_KEY);
    }

    @Bean
    public Binding paymentRefundedDlqBinding(
            Queue paymentRefundedDlq,
            DirectExchange paymentDeadLetterExchange) {
        return BindingBuilder.bind(paymentRefundedDlq)
                .to(paymentDeadLetterExchange)
                .with(PAYMENT_REFUNDED_DLQ_KEY);
    }

    @Bean
    public Binding paymentRefundFailedDlqBinding(
            Queue paymentRefundFailedDlq,
            DirectExchange paymentDeadLetterExchange) {
        return BindingBuilder.bind(paymentRefundFailedDlq)
                .to(paymentDeadLetterExchange)
                .with(PAYMENT_REFUND_FAILED_DLQ_KEY);
    }

    @Bean
    public Queue bookingConfirmedQueue() {
        return QueueBuilder.durable(BOOKING_CONFIRMED_QUEUE).build();
    }

    @Bean
    public Binding bookingConfirmedBinding(Queue bookingConfirmedQueue, TopicExchange bookingExchange) {
        return BindingBuilder.bind(bookingConfirmedQueue).to(bookingExchange).with(BOOKING_CONFIRMED_KEY);
    }

    // Usar la nueva clase no deprecada JacksonJsonMessageConverter
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .maxRetries(3)
                .backOffOptions(1000, 2.0, 10_000)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build());
        return factory;
    }
}