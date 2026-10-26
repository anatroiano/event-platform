package com.example.certificate_service.repository;

import com.example.certificate_service.domain.OutboxMessage;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxMessage, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            SELECT o FROM OutboxMessage o
            WHERE o.processedAt IS NULL AND o.attempts < :maxAttempts
            ORDER BY o.createdAt, o.id
            """)
    List<OutboxMessage> findPendingForUpdate(@Param("maxAttempts") int maxAttempts, Pageable pageable);
}
