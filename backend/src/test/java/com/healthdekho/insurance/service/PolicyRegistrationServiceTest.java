package com.healthdekho.insurance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.healthdekho.insurance.domain.PolicyType;
import com.healthdekho.insurance.domain.PremiumFrequency;
import com.healthdekho.insurance.domain.Role;
import com.healthdekho.insurance.dto.policy.RegisterPolicyRequest;
import com.healthdekho.insurance.entity.Customer;
import com.healthdekho.insurance.entity.InsuranceProduct;
import com.healthdekho.insurance.exception.ConflictException;
import com.healthdekho.insurance.repository.AiRecommendationItemRepository;
import com.healthdekho.insurance.repository.CustomerPolicyRepository;
import com.healthdekho.insurance.repository.CustomerRepository;
import com.healthdekho.insurance.repository.InsuranceProductRepository;
import com.healthdekho.insurance.security.AuthenticatedUser;
import com.healthdekho.insurance.support.TestClockConfig;

class PolicyRegistrationServiceTest {

    private static final AuthenticatedUser ROHAN =
            new AuthenticatedUser(1003L, "rohan.verma@example.com", "Rohan Verma", Role.CUSTOMER);

    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final InsuranceProductRepository products = mock(InsuranceProductRepository.class);
    private final CustomerPolicyRepository policies = mock(CustomerPolicyRepository.class);

    private PolicyRegistrationService service;

    @BeforeEach
    void setUp() {
        service = new PolicyRegistrationService(customers, products, policies,
                mock(AiRecommendationItemRepository.class),
                Clock.fixed(TestClockConfig.TODAY.atTime(10, 0).atZone(TestClockConfig.ZONE).toInstant(),
                        TestClockConfig.ZONE));

        when(customers.findById(ROHAN.id())).thenReturn(Optional.of(new Customer(ROHAN.email(), "hash",
                ROHAN.fullName(), LocalDate.of(2000, 1, 10), "9876501003")));
        when(products.findByProductCode("HD-HEALTH-001")).thenReturn(Optional.of(new InsuranceProduct(
                "HD-HEALTH-001", "HealthDekho Family Health", PolicyType.HEALTH, null, 12,
                PremiumFrequency.YEARLY, new BigDecimal("18000.00"), new BigDecimal("1000000.00"), 18, 65)));
    }

    @Test
    void concurrentDuplicateCaughtByTheUniqueConstraintBecomesAConflict() {
        // the pre-check passes because the other request has not committed yet
        when(policies.existsByCustomerIdAndProductId(any(), any())).thenReturn(false);
        when(policies.nextPolicyNumberSequence()).thenReturn(42L);
        when(policies.saveAndFlush(any())).thenThrow(
                new DataIntegrityViolationException("uq_policy_customer_product"));

        RegisterPolicyRequest request = new RegisterPolicyRequest("HD-HEALTH-001", TestClockConfig.TODAY, null);

        assertThatThrownBy(() -> service.register(ROHAN, request))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.getCode()).isEqualTo("DUPLICATE_POLICY"));
    }
}
