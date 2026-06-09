package com.smartparking.backend.repository;

import com.smartparking.backend.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    List<Payment> findByReferenceTypeAndReferenceId(String referenceType, UUID referenceId);
}
