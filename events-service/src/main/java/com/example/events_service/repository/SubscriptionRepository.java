package com.example.events_service.repository;

import com.example.events_service.domain.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    long countByEventId(String eventId);

    boolean existsByEventIdAndParticipantEmail(String eventId, String participantEmail);

    Optional<Subscription> findByCheckinToken(String checkinToken);
}
