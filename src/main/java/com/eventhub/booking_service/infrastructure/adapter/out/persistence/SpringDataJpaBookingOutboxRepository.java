package com.eventhub.booking_service.infrastructure.adapter.out.persistence;

import com.eventhub.booking_service.domain.model.BookingOutboxStatus;
import com.eventhub.booking_service.infrastructure.adapter.out.persistence.entity.BookingOutboxEventEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpringDataJpaBookingOutboxRepository
        extends JpaRepository<BookingOutboxEventEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT event FROM BookingOutboxEventEntity event
            WHERE event.status = :status
            ORDER BY event.createdAt
            """)
    List<BookingOutboxEventEntity> findByStatusForUpdate(
            @Param("status") BookingOutboxStatus status,
            Pageable pageable);

    long countByStatus(BookingOutboxStatus status);
}
