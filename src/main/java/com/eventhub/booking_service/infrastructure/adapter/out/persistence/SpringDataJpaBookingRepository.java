package com.eventhub.booking_service.infrastructure.adapter.out.persistence;

import com.eventhub.booking_service.infrastructure.adapter.out.persistence.entity.BookingEntity;
import com.eventhub.booking_service.domain.model.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SpringDataJpaBookingRepository extends JpaRepository<BookingEntity, String> {
    List<BookingEntity> findByUserId(String userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT booking FROM BookingEntity booking WHERE booking.id = :id")
    Optional<BookingEntity> findByIdForUpdate(@Param("id") String id);

    @Query("""
            SELECT booking.id FROM BookingEntity booking
            WHERE booking.status = :status AND booking.expiresAt <= :now
            ORDER BY booking.expiresAt
            """)
    List<String> findExpiredBookingIds(
            @Param("status") BookingStatus status,
            @Param("now") LocalDateTime now,
            Pageable pageable);
}