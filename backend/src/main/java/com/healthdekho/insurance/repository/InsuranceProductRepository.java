package com.healthdekho.insurance.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.healthdekho.insurance.entity.InsuranceProduct;

public interface InsuranceProductRepository extends JpaRepository<InsuranceProduct, Long> {

    Optional<InsuranceProduct> findByProductCode(String productCode);

    List<InsuranceProduct> findByActiveTrueOrderByPolicyTypeAscPolicyNameAsc();
}
