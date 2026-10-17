package com.eventhub.booking_service.infrastructure.adapter.out.messaging;

import com.eventhub.booking_service.domain.model.BookingOutboxEvent;
import com.eventhub.booking_service.domain.model.BookingOutboxStatus;
import com.eventhub.booking_service.domain.port.out.BookingOutboxRepositoryPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookingOutboxEventRelayTest {

    @Test
    void publishesOutboxIdAsMessageIdAndMarksEventPublished() throws Exception {
        BookingOutboxRepositoryPort outboxRepository = mock(BookingOutboxRepositoryPort.class);
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        String eventId = "booking-outbox-123";
        BookingOutboxEvent event = new BookingOutboxEvent(
                eventId,
                "Booking",
                "booking-123",
                "BookingConfirmedEvent",
                "booking.exchange",
                "booking.confirmed",
                "{\"bookingId\":\"booking-123\"}",
                BookingOutboxStatus.PENDING,
                0,
                LocalDateTime.now(),
                null);
        when(outboxRepository.findPendingForUpdate(50)).thenReturn(List.of(event));
        doAnswer(invocation -> {
            MessagePostProcessor postProcessor = invocation.getArgument(3);
            Message message = postProcessor.postProcessMessage(MessageBuilder.withBody(new byte[0]).build());
            assertThat(message.getMessageProperties().getMessageId()).isEqualTo(eventId);
            CorrelationData correlationData = invocation.getArgument(4);
            correlationData.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).convertAndSend(
                anyString(), anyString(), any(), any(MessagePostProcessor.class), any(CorrelationData.class));

        new BookingOutboxEventRelay(outboxRepository, rabbitTemplate, new ObjectMapper())
                .publishPendingEvents();

        ArgumentCaptor<BookingOutboxEvent> savedCaptor = ArgumentCaptor.forClass(BookingOutboxEvent.class);
        verify(outboxRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().status()).isEqualTo(BookingOutboxStatus.PUBLISHED);
        assertThat(savedCaptor.getValue().publishAttempts()).isEqualTo(1);
    }
}
