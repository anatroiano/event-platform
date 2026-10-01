package com.example.events_service.repository;

import com.example.events_service.domain.OutboxMessage;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxMessage, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2")) // SKIP LOCKED
    @Query("""
            SELECT o FROM OutboxMessage o 
            WHERE o.status = 'PENDING'
            ORDER BY o.createdAt ASC
            """)
    List<OutboxMessage> findPendingForUpdate(Pageable pageable);
}