package com.healthdekho.insurance.service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.healthdekho.insurance.dto.policy.ProductResponse;
import com.healthdekho.insurance.entity.InsuranceProduct;
import com.healthdekho.insurance.exception.ResourceNotFoundException;
import com.healthdekho.insurance.repository.CustomerPolicyRepository;
import com.healthdekho.insurance.repository.InsuranceProductRepository;
import com.healthdekho.insurance.security.AuthenticatedUser;

@Service
public class ProductService {

    private final InsuranceProductRepository products;
    private final CustomerPolicyRepository policies;

    public ProductService(InsuranceProductRepository products, CustomerPolicyRepository policies) {
        this.products = products;
        this.policies = policies;
    }

    /** Active products the customer does not hold yet (one policy per product, ever). */
    @Transactional(readOnly = true)
    public List<ProductResponse> availableProducts(AuthenticatedUser user) {
        Set<String> heldCodes = policies.findByCustomerIdOrderByStartDateDesc(user.id()).stream()
                .map(policy -> policy.getProduct().getProductCode())
                .collect(Collectors.toSet());
        return products.findByActiveTrueOrderByPolicyTypeAscPolicyNameAsc().stream()
                .filter(product -> !heldCodes.contains(product.getProductCode()))
                .map(ProductResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse product(String productCode) {
        return products.findByProductCode(productCode)
                .filter(InsuranceProduct::isActive)
                .map(ProductResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }
}
