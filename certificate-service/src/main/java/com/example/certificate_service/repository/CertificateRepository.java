package com.example.certificate_service.repository;

import com.example.certificate_service.domain.Certificate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CertificateRepository extends JpaRepository<Certificate, String> {

    Optional<Certificate> findBySubscriptionIdAndEventId(String subscriptionId, String eventId);

    @Modifying
    @Query(value = """
            INSERT INTO certificates
                (id, event_id, subscription_id, participant_name, participant_email, event_name, issued_at)
            VALUES (:#{#certificate.id}, :#{#certificate.eventId}, :#{#certificate.subscriptionId},
                    :#{#certificate.participantName}, :#{#certificate.participantEmail},
                    :#{#certificate.eventName}, :#{#certificate.issuedAt})
            ON CONFLICT (subscription_id, event_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("certificate") Certificate certificate);

}
