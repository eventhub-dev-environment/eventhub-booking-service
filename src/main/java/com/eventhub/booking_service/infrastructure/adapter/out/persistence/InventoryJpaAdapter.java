package com.eventhub.booking_service.infrastructure.adapter.out.persistence;

import com.eventhub.booking_service.domain.exception.InventoryConfigurationConflictException;
import com.eventhub.booking_service.domain.exception.InventoryUnavailableException;
import com.eventhub.booking_service.domain.model.Booking;
import com.eventhub.booking_service.domain.model.BookingInventoryDefinition;
import com.eventhub.booking_service.domain.model.InventoryMode;
import com.eventhub.booking_service.domain.model.InventorySeatDefinition;
import com.eventhub.booking_service.domain.port.out.InventoryRepositoryPort;
import com.eventhub.booking_service.infrastructure.adapter.out.persistence.entity.InventoryPoolEntity;
import com.eventhub.booking_service.infrastructure.adapter.out.persistence.entity.InventorySeatEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class InventoryJpaAdapter implements InventoryRepositoryPort {

    private final SpringDataInventoryPoolRepository poolRepository;
    private final SpringDataInventorySeatRepository seatRepository;

    @Override
    @Transactional
    public void initialize(BookingInventoryDefinition definition) {
        if (definition.inventoryMode() == InventoryMode.GENERAL_ADMISSION) {
            if (!seatRepository.findByEventIdAndSessionIdAndTicketTierId(
                    definition.eventId(), definition.sessionId(), definition.ticketTierId()).isEmpty()) {
                throw new InventoryConfigurationConflictException(
                        "La configuración de inventario existente no coincide con el evento");
            }
            poolRepository.insertIfAbsent(
                    inventoryId(definition),
                    definition.eventId(),
                    definition.sessionId(),
                    definition.ticketTierId(),
                    definition.capacity());
            InventoryPoolEntity pool = poolRepository.findByEventIdAndSessionIdAndTicketTierId(
                            definition.eventId(), definition.sessionId(), definition.ticketTierId())
                    .orElseThrow(() -> new InventoryConfigurationConflictException(
                            "No se pudo inicializar el inventario del evento"));
            if (!definition.capacity().equals(pool.getTotalCapacity())) {
                throw new InventoryConfigurationConflictException(
                        "La capacidad de la tarifa cambió después de inicializarse el inventario");
            }
            return;
        }

        if (poolRepository.findByEventIdAndSessionIdAndTicketTierId(
                definition.eventId(), definition.sessionId(), definition.ticketTierId()).isPresent()) {
            throw new InventoryConfigurationConflictException(
                    "La configuración de inventario existente no coincide con el evento");
        }
        for (InventorySeatDefinition seat : definition.seats()) {
            seatRepository.insertIfAbsent(
                    UUID.randomUUID().toString(),
                    definition.eventId(),
                    definition.sessionId(),
                    definition.ticketTierId(),
                    seat.seatId(),
                    seat.section(),
                    seat.row(),
                    seat.number(),
                    seat.accessible());
        }
        List<InventorySeatEntity> persistedSeats = seatRepository.findByEventIdAndSessionIdAndTicketTierId(
                definition.eventId(), definition.sessionId(), definition.ticketTierId());
        if (persistedSeats.size() != definition.seats().size()
                || definition.seats().stream().anyMatch(seat -> !matches(persistedSeats, seat))) {
            throw new InventoryConfigurationConflictException(
                    "El mapa de asientos cambió después de inicializarse el inventario");
        }
    }

    @Override
    @Transactional
    public void hold(Booking booking, BookingInventoryDefinition definition) {
        if (definition.inventoryMode() == InventoryMode.GENERAL_ADMISSION) {
            int updated = poolRepository.holdAvailable(
                    booking.getEventId(),
                    booking.getSessionId(),
                    booking.getTicketTierId(),
                    booking.getQuantity());
            if (updated != 1) {
                throw new InventoryUnavailableException("No hay suficientes entradas disponibles");
            }
            return;
        }

        List<String> orderedSeatIds = booking.getSeatIds().stream()
                .sorted(Comparator.naturalOrder())
                .toList();
        for (String seatId : orderedSeatIds) {
            int updated = seatRepository.holdSeat(
                    booking.getEventId(),
                    booking.getSessionId(),
                    booking.getTicketTierId(),
                    seatId,
                    booking.getId(),
                    booking.getExpiresAt(),
                    booking.getCreatedAt());
            if (updated != 1) {
                throw new InventoryUnavailableException("Uno o más asientos ya no están disponibles");
            }
        }
    }

    @Override
    @Transactional
    public void confirm(Booking booking) {
        if (booking.getSeatIds() != null && !booking.getSeatIds().isEmpty()) {
            int updated = seatRepository.confirmHeldSeats(
                    booking.getEventId(), booking.getSessionId(), booking.getTicketTierId(), booking.getId());
            if (updated != booking.getSeatIds().size()) {
                throw new InventoryConfigurationConflictException(
                        "No se pudo confirmar de forma íntegra la retención de asientos");
            }
            return;
        }
        int updated = poolRepository.confirmHeld(
                booking.getEventId(), booking.getSessionId(), booking.getTicketTierId(), booking.getQuantity());
        if (updated != 1) {
            throw new InventoryConfigurationConflictException(
                    "No se pudo confirmar de forma íntegra la retención de entradas");
        }
    }

    @Override
    @Transactional
    public void release(Booking booking) {
        if (booking.getSeatIds() != null && !booking.getSeatIds().isEmpty()) {
            int updated = seatRepository.releaseHeldSeats(
                    booking.getEventId(), booking.getSessionId(), booking.getTicketTierId(), booking.getId());
            if (updated != 0 && updated != booking.getSeatIds().size()) {
                throw new InventoryConfigurationConflictException(
                        "No se pudo liberar de forma íntegra la retención de asientos");
            }
            return;
        }
        int updated = poolRepository.releaseHeld(
                booking.getEventId(), booking.getSessionId(), booking.getTicketTierId(), booking.getQuantity());
        if (updated != 1) {
            throw new InventoryConfigurationConflictException(
                    "No se pudo liberar de forma íntegra la retención de entradas");
        }
    }

    @Override
    @Transactional
    public void releaseConfirmed(Booking booking) {
        if (booking.getSeatIds() != null && !booking.getSeatIds().isEmpty()) {
            int released = seatRepository.releaseSoldSeats(
                    booking.getEventId(), booking.getSessionId(), booking.getTicketTierId(), booking.getId());
            if (released != booking.getSeatIds().size()) {
                throw new InventoryConfigurationConflictException(
                        "No se pudo devolver de forma íntegra el inventario vendido");
            }
            return;
        }
        int released = poolRepository.releaseSold(
                booking.getEventId(), booking.getSessionId(), booking.getTicketTierId(), booking.getQuantity());
        if (released != 1) {
            throw new InventoryConfigurationConflictException(
                    "No se pudo devolver de forma íntegra el inventario vendido");
        }
    }

    private boolean matches(List<InventorySeatEntity> persistedSeats, InventorySeatDefinition definition) {
        return persistedSeats.stream()
                .filter(seat -> seat.getSeatId().equals(definition.seatId()))
                .anyMatch(seat -> seat.getSection().equals(definition.section())
                        && seat.getSeatRow().equals(definition.row())
                        && seat.getSeatNumber().equals(definition.number())
                        && seat.isAccessible() == definition.accessible());
    }

    private String inventoryId(BookingInventoryDefinition definition) {
        String key = definition.eventId() + "\u0000" + definition.sessionId() + "\u0000" + definition.ticketTierId();
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
