package com.healthdekho.insurance.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.healthdekho.insurance.entity.PremiumPayment;

public interface PremiumPaymentRepository extends JpaRepository<PremiumPayment, Long> {

    @Query(value = "select nextval('transaction_ref_seq')", nativeQuery = true)
    long nextTransactionSequence();

    @Query("""
            select p from PremiumPayment p join fetch p.installment i
            where i.policy.id = :policyId
            order by p.paymentDate desc, p.id desc
            """)
    List<PremiumPayment> findHistoryForPolicy(Long policyId);
}
