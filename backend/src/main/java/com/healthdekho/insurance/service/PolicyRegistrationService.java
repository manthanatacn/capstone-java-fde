package com.healthdekho.insurance.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.healthdekho.insurance.domain.Ages;
import com.healthdekho.insurance.domain.Formats;
import com.healthdekho.insurance.domain.PolicySchedule;
import com.healthdekho.insurance.dto.policy.PolicySummaryResponse;
import com.healthdekho.insurance.dto.policy.ProductResponse;
import com.healthdekho.insurance.dto.policy.RegisterPolicyRequest;
import com.healthdekho.insurance.dto.policy.RegistrationPreviewResponse;
import com.healthdekho.insurance.dto.policy.RegistrationPreviewResponse.CustomerDetails;
import com.healthdekho.insurance.dto.policy.RegistrationResultResponse;
import com.healthdekho.insurance.entity.AiRecommendationItem;
import com.healthdekho.insurance.entity.Customer;
import com.healthdekho.insurance.entity.CustomerPolicy;
import com.healthdekho.insurance.entity.InsuranceProduct;
import com.healthdekho.insurance.entity.PremiumInstallment;
import com.healthdekho.insurance.exception.BusinessRuleException;
import com.healthdekho.insurance.exception.ConflictException;
import com.healthdekho.insurance.exception.ResourceNotFoundException;
import com.healthdekho.insurance.repository.AiRecommendationItemRepository;
import com.healthdekho.insurance.repository.CustomerPolicyRepository;
import com.healthdekho.insurance.repository.CustomerRepository;
import com.healthdekho.insurance.repository.InsuranceProductRepository;
import com.healthdekho.insurance.security.AuthenticatedUser;

@Service
public class PolicyRegistrationService {

    private static final int MAX_START_DAYS_AHEAD = 90;

    private final CustomerRepository customers;
    private final InsuranceProductRepository products;
    private final CustomerPolicyRepository policies;
    private final AiRecommendationItemRepository recommendationItems;
    private final Clock clock;

    public PolicyRegistrationService(CustomerRepository customers, InsuranceProductRepository products,
                                     CustomerPolicyRepository policies,
                                     AiRecommendationItemRepository recommendationItems, Clock clock) {
        this.customers = customers;
        this.products = products;
        this.policies = policies;
        this.recommendationItems = recommendationItems;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PolicySummaryResponse> myPolicies(AuthenticatedUser user) {
        LocalDate today = LocalDate.now(clock);
        return policies.findByCustomerIdOrderByStartDateDesc(user.id()).stream()
                .map(policy -> PolicySummaryResponse.from(policy, today))
                .toList();
    }

    /**
     * Same checks as {@link #register}, but an age or duplicate problem is reported in the response so
     * the form can still show the figures. A start date outside the window is an input error either way.
     */
    @Transactional(readOnly = true)
    public RegistrationPreviewResponse preview(AuthenticatedUser user, String productCode, LocalDate startDate) {
        Customer customer = currentCustomer(user);
        InsuranceProduct product = availableProduct(productCode);
        LocalDate start = startDate != null ? startDate : LocalDate.now(clock);
        requireStartDateInWindow(start);

        int age = Ages.ageOn(customer.getDateOfBirth(), start);
        Optional<String> ineligibilityReason = ineligibility(customer, product, age).map(Throwable::getMessage);
        PolicySchedule schedule = scheduleFor(product, start);

        return new RegistrationPreviewResponse(
                new CustomerDetails(customer.getFullName(), customer.getDateOfBirth(), customer.getEmail(),
                        customer.getPhone()),
                ProductResponse.from(product), start, schedule.maturityDate(), schedule.installmentCount(),
                schedule.totalPremiumPayable(), age, ineligibilityReason.isEmpty(), ineligibilityReason.orElse(null));
    }

    @Transactional
    public RegistrationResultResponse register(AuthenticatedUser user, RegisterPolicyRequest request) {
        Customer customer = currentCustomer(user);
        InsuranceProduct product = availableProduct(request.productCode());
        LocalDate start = request.startDate();
        requireStartDateInWindow(start);
        ineligibility(customer, product, Ages.ageOn(customer.getDateOfBirth(), start))
                .ifPresent(problem -> { throw problem; });

        Optional<AiRecommendationItem> recommendation = Optional.ofNullable(request.recommendationItemId())
                .map(itemId -> recommendationItemFor(itemId, customer, product));

        PolicySchedule schedule = scheduleFor(product, start);
        String policyNumber = "HDP-%d-%04d".formatted(start.getYear(), policies.nextPolicyNumberSequence());
        CustomerPolicy policy = new CustomerPolicy(policyNumber, customer, product, start,
                schedule.maturityDate(), schedule.totalPremiumPayable());
        schedule.installments().forEach(row -> policy.addInstallment(new PremiumInstallment(
                row.installmentNumber(), row.dueDate(), row.gracePeriodEndDate(), row.amount())));

        try {
            policies.saveAndFlush(policy);
        } catch (DataIntegrityViolationException e) {
            // another request registered the same product between the pre-check and this insert
            if (violates(e, "uq_policy_customer_product")) {
                throw duplicatePolicy(product);
            }
            throw e;
        }
        recommendation.ifPresent(AiRecommendationItem::markSelectedForRegistration);

        return new RegistrationResultResponse(policyNumber,
                "Policy %s registered successfully.".formatted(policyNumber),
                PolicySummaryResponse.from(policy, LocalDate.now(clock)));
    }

    private Customer currentCustomer(AuthenticatedUser user) {
        return customers.findById(user.id())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
    }

    private InsuranceProduct availableProduct(String productCode) {
        return products.findByProductCode(productCode)
                .filter(InsuranceProduct::isActive)
                .orElseThrow(() -> new BusinessRuleException("PRODUCT_NOT_AVAILABLE",
                        "Product %s is not available for registration.".formatted(productCode)));
    }

    private void requireStartDateInWindow(LocalDate start) {
        LocalDate today = LocalDate.now(clock);
        LocalDate latest = today.plusDays(MAX_START_DAYS_AHEAD);
        if (start.isBefore(today) || start.isAfter(latest)) {
            throw new BusinessRuleException("START_DATE_OUT_OF_RANGE",
                    "Start date must be between %s and %s.".formatted(Formats.date(today), Formats.date(latest)));
        }
    }

    private Optional<BusinessRuleException> ineligibility(Customer customer, InsuranceProduct product, int age) {
        if (!product.acceptsEntryAge(age)) {
            return Optional.of(new BusinessRuleException("ENTRY_AGE_NOT_ELIGIBLE",
                    "Customer age %d is outside the entry age %d–%d for this product."
                            .formatted(age, product.getMinEntryAge(), product.getMaxEntryAge())));
        }
        if (policies.existsByCustomerIdAndProductId(customer.getId(), product.getId())) {
            return Optional.of(duplicatePolicy(product));
        }
        return Optional.empty();
    }

    private AiRecommendationItem recommendationItemFor(Long itemId, Customer customer, InsuranceProduct product) {
        return recommendationItems.findById(itemId)
                .filter(item -> item.getRequest().getCustomer().getId().equals(customer.getId()))
                .filter(item -> item.getProduct().getId().equals(product.getId()))
                .orElseThrow(() -> new BusinessRuleException("INVALID_RECOMMENDATION_ITEM",
                        "The recommendation does not match this customer and product."));
    }

    private static PolicySchedule scheduleFor(InsuranceProduct product, LocalDate start) {
        return PolicySchedule.of(start, product.getTermMonths(), product.getPremiumFrequency(),
                product.getPremiumAmount());
    }

    private static ConflictException duplicatePolicy(InsuranceProduct product) {
        return new ConflictException("DUPLICATE_POLICY",
                "You already hold %s. Each product can be registered only once.".formatted(product.getPolicyName()));
    }

    private static boolean violates(DataIntegrityViolationException e, String constraint) {
        String detail = e.getMostSpecificCause().getMessage();
        return detail != null && detail.contains(constraint);
    }
}
