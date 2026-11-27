package com.healthdekho.insurance.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.healthdekho.insurance.entity.PremiumInstallment;

public interface PremiumInstallmentRepository extends JpaRepository<PremiumInstallment, Long> {

    List<PremiumInstallment> findByPolicyIdOrderByInstallmentNumberAsc(Long policyId);
}
