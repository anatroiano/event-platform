package com.example.certificate_service.repository;

import com.example.certificate_service.domain.Certificate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CertificateRepository extends JpaRepository<Certificate, String> {

    boolean existsBySubscriptionIdAndEventId(String subscriptionId, String eventId);

}
