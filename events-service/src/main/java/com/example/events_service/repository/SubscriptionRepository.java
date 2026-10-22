package com.example.events_service.repository;

import com.example.events_service.domain.Subscription;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    long countByEventId(String eventId);

    boolean existsByEventIdAndParticipantEmail(String eventId, String participantEmail);

    Optional<Subscription> findByCheckinToken(String checkinToken);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("SELECT s FROM Subscription s WHERE s.checkinToken = :token")
    Optional<Subscription> findByCheckinTokenForUpdate(@Param("token") String token);

    List<Subscription> findByEventIdAndCheckedInAtIsNotNull(String eventId);
}
