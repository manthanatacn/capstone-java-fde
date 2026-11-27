package com.healthdekho.insurance.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.healthdekho.insurance.domain.PremiumPaymentStatus;
import com.healthdekho.insurance.domain.PremiumTotals;
import com.healthdekho.insurance.dto.common.PageResponse;
import com.healthdekho.insurance.dto.search.PolicySearchCriteria;
import com.healthdekho.insurance.dto.search.PolicySearchResult;
import com.healthdekho.insurance.entity.CustomerPolicy;
import com.healthdekho.insurance.exception.InvalidSearchCriteriaException;
import com.healthdekho.insurance.repository.InstallmentTally;
import com.healthdekho.insurance.repository.PolicySearchRepository;
import com.healthdekho.insurance.security.AuthenticatedUser;

@Service
public class PolicySearchService {

    static final int MAX_PAGE_SIZE = 50;
    static final int MIN_NAME_LENGTH = 2;
    static final long MAX_OFFSET = 100_000;
    static final String VALID_COMBINATIONS = "Search by Policy Identifier, by Customer Name (at least 2 characters), "
            + "or by Policy Identifier together with Date of Birth.";

    private final PolicySearchRepository repository;
    private final Clock clock;

    public PolicySearchService(PolicySearchRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<PolicySearchResult> search(PolicySearchCriteria criteria, int page, int size,
                                                   AuthenticatedUser user) {
        SearchMode mode = SearchMode.of(criteria);
        int pageSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        int pageNumber = Math.max(page, 0);
        if ((long) pageNumber * pageSize > MAX_OFFSET) {
            throw new InvalidSearchCriteriaException("That page is beyond the end of any result. Go back to the first page.");
        }
        Pageable pageable = PageRequest.of(pageNumber, pageSize, Sort.by("policyNumber"));
        Long customerId = user.isStaff() ? null : user.id();

        Page<CustomerPolicy> policies = switch (mode) {
            case ByPolicyNumber(String number) ->
                    asPage(repository.findOneByPolicyNumber(number, customerId), pageable);
            case ByCustomerName(String name) -> repository.findByCustomerName(escapeLike(name), customerId, pageable);
            case ByPolicyNumberAndDateOfBirth(String number, LocalDate dob) ->
                    asPage(repository.findOneByPolicyNumberAndDateOfBirth(number, dob, customerId), pageable);
        };

        Map<Long, List<InstallmentTally>> talliesByPolicy = tallies(policies.getContent());
        return PageResponse.of(policies, p -> toResult(p, talliesByPolicy.getOrDefault(p.getId(), List.of())));
    }

    // a policy identifier is unique, so these modes are single-record lookups shown as a one-row page
    private static Page<CustomerPolicy> asPage(Optional<CustomerPolicy> policy, Pageable pageable) {
        List<CustomerPolicy> rows = policy.filter(p -> pageable.getPageNumber() == 0).map(List::of).orElse(List.of());
        return new PageImpl<>(rows, pageable, policy.isPresent() ? 1 : 0);
    }

    /** One query for the whole page (AC-5C.3), split up per policy here. */
    private Map<Long, List<InstallmentTally>> tallies(List<CustomerPolicy> policies) {
        if (policies.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = policies.stream().map(CustomerPolicy::getId).toList();
        return repository.tallyInstallments(ids, LocalDate.now(clock)).stream()
                .collect(Collectors.groupingBy(InstallmentTally::policyId));
    }

    private static PolicySearchResult toResult(CustomerPolicy policy, List<InstallmentTally> tallies) {
        PremiumTotals totals = PremiumTotals.of(policy.getTotalPremiumPayable(),
                sum(tallies, InstallmentTally::latePenalties), sum(tallies, InstallmentTally::paid));
        return new PolicySearchResult(
                policy.getPolicyNumber(),
                policy.getCustomer().getFullName(),
                policy.getCustomer().getDateOfBirth(),
                policy.getProduct().getPolicyName(),
                policy.getProduct().getPolicyType(),
                policy.getStartDate(),
                policy.getMaturityDate(),
                totals.totalPremiumPayable(),
                PremiumPaymentStatus.of(tallies.stream().map(InstallmentTally::status).toList()),
                totals.totalPremiumPaid(),
                totals.remainingPremium(),
                totals.latePenalties());
    }

    private static BigDecimal sum(List<InstallmentTally> tallies, Function<InstallmentTally, BigDecimal> amount) {
        return tallies.stream().map(amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** The three supported field combinations (AC-5.2). Blank fields count as not filled in. */
    sealed interface SearchMode {

        static SearchMode of(PolicySearchCriteria criteria) {
            String number = trimToNull(criteria.policyNumber());
            String name = trimToNull(criteria.customerName());
            LocalDate dob = criteria.dateOfBirth();

            if (number != null && name == null) {
                String normalized = number.toUpperCase(Locale.ROOT);
                return dob == null ? new ByPolicyNumber(normalized) : new ByPolicyNumberAndDateOfBirth(normalized, dob);
            }
            if (name != null && number == null && dob == null) {
                if (name.length() < MIN_NAME_LENGTH) {
                    throw new InvalidSearchCriteriaException(
                            "Enter at least %d characters of the customer name.".formatted(MIN_NAME_LENGTH));
                }
                return new ByCustomerName(name);
            }
            throw new InvalidSearchCriteriaException(VALID_COMBINATIONS);
        }

        private static String trimToNull(String value) {
            return value == null || value.isBlank() ? null : value.trim();
        }
    }

    record ByPolicyNumber(String policyNumber) implements SearchMode {
    }

    record ByCustomerName(String name) implements SearchMode {
    }

    record ByPolicyNumberAndDateOfBirth(String policyNumber, LocalDate dateOfBirth) implements SearchMode {
    }

    // a name like "50%" should match literally, not act as a wildcard
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
