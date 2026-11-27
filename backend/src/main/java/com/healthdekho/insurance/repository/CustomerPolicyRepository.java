package com.healthdekho.insurance.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.healthdekho.insurance.entity.CustomerPolicy;

import jakarta.persistence.LockModeType;

public interface CustomerPolicyRepository extends JpaRepository<CustomerPolicy, Long> {

    @EntityGraph(attributePaths = "product")
    List<CustomerPolicy> findByCustomerIdOrderByStartDateDesc(Long customerId);

    @EntityGraph(attributePaths = {"product", "customer"})
    Optional<CustomerPolicy> findByPolicyNumber(String policyNumber);

    boolean existsByCustomerIdAndProductId(Long customerId, Long productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p.id from CustomerPolicy p where p.id = :policyId")
    Optional<Long> lockForPayment(Long policyId);

    @Query(value = "select nextval('policy_number_seq')", nativeQuery = true)
    long nextPolicyNumberSequence();
}
