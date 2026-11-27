package com.healthdekho.insurance.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;

import com.healthdekho.insurance.ai.AdvisorPick;
import com.healthdekho.insurance.ai.AiProperties;
import com.healthdekho.insurance.ai.CustomerNeeds;
import com.healthdekho.insurance.ai.InsuranceAdvisor;
import com.healthdekho.insurance.ai.RuleBasedRanker;
import com.healthdekho.insurance.domain.Ages;
import com.healthdekho.insurance.domain.Formats;
import com.healthdekho.insurance.domain.GenerationSource;
import com.healthdekho.insurance.domain.Suitability;
import com.healthdekho.insurance.dto.recommendation.RecommendationRequest;
import com.healthdekho.insurance.dto.recommendation.RecommendationResponse;
import com.healthdekho.insurance.dto.recommendation.RecommendedProductResponse;
import com.healthdekho.insurance.entity.AiRecommendationItem;
import com.healthdekho.insurance.entity.AiRecommendationRequest;
import com.healthdekho.insurance.entity.Customer;
import com.healthdekho.insurance.entity.InsuranceProduct;
import com.healthdekho.insurance.exception.BusinessRuleException;
import com.healthdekho.insurance.exception.ResourceNotFoundException;
import com.healthdekho.insurance.repository.AiRecommendationRequestRepository;
import com.healthdekho.insurance.repository.CustomerPolicyRepository;
import com.healthdekho.insurance.repository.CustomerRepository;
import com.healthdekho.insurance.repository.InsuranceProductRepository;
import com.healthdekho.insurance.security.AuthenticatedUser;

/**
 * Recommendation flow (architecture §8): eligibility is decided here in Java, the model only ranks
 * the eligible candidates, and whatever it returns is checked against them before it is saved.
 */
@Service
public class RecommendationService {

    static final String FALLBACK_NOTICE =
            "Our AI advisor is unavailable right now, so these suggestions come from our standard matching rules.";

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);
    private static final int MAX_REASON_LENGTH = 1000;

    private final CustomerRepository customers;
    private final InsuranceProductRepository products;
    private final CustomerPolicyRepository policies;
    private final AiRecommendationRequestRepository requests;
    private final InsuranceAdvisor advisor;
    private final RuleBasedRanker ranker;
    private final AiProperties properties;
    private final Clock clock;

    public RecommendationService(CustomerRepository customers, InsuranceProductRepository products,
                                 CustomerPolicyRepository policies, AiRecommendationRequestRepository requests,
                                 InsuranceAdvisor advisor, RuleBasedRanker ranker, AiProperties properties,
                                 Clock clock) {
        this.customers = customers;
        this.products = products;
        this.policies = policies;
        this.requests = requests;
        this.advisor = advisor;
        this.ranker = ranker;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public RecommendationResponse recommend(AuthenticatedUser user, RecommendationRequest input) {
        Customer customer = customers.findById(user.id())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        LocalDate today = LocalDate.now(clock);
        LocalDate dateOfBirth = input.dateOfBirth() != null ? input.dateOfBirth() : customer.getDateOfBirth();
        if (dateOfBirth.isAfter(today)) {
            // bean validation checks this against the system clock; this is the check against the app clock
            throw new BusinessRuleException("DATE_OF_BIRTH_IN_FUTURE", "Date of birth cannot be in the future.");
        }
        String goals = StringUtils.hasText(input.goals()) ? input.goals().trim() : null;
        CustomerNeeds needs = new CustomerNeeds(Ages.ageOn(dateOfBirth, today), goals,
                input.preferredTermYears(), input.policyType());

        List<InsuranceProduct> candidates = candidatesFor(customer, needs, today);
        Outcome outcome = candidates.isEmpty() ? noCandidates(needs) : rank(needs, candidates);

        String preferredTerm = needs.preferredTermYears() == null ? null : Formats.years(needs.preferredTermYears());
        AiRecommendationRequest request = new AiRecommendationRequest(customer, goals, preferredTerm,
                needs.policyType(), needs.age(), OffsetDateTime.now(clock));
        request.recordGeneration(outcome.source(), outcome.modelName(), outcome.modelVersion(),
                outcome.promptVersion(), outcome.promptText());
        for (int i = 0; i < outcome.picks().size(); i++) {
            ValidPick pick = outcome.picks().get(i);
            request.addItem(new AiRecommendationItem(pick.product(), i + 1, pick.reason(), pick.suitability()));
        }
        AiRecommendationRequest saved = requests.save(request);
        return toResponse(saved, outcome.notice());
    }

    private List<InsuranceProduct> candidatesFor(Customer customer, CustomerNeeds needs, LocalDate today) {
        // registration checks entry age against the profile date of birth, so a product the customer
        // could not register must not be suggested even if the date typed into the form would qualify
        int profileAge = Ages.ageOn(customer.getDateOfBirth(), today);
        Set<Long> held = policies.findByCustomerIdOrderByStartDateDesc(customer.getId()).stream()
                .map(policy -> policy.getProduct().getId())
                .collect(Collectors.toSet());
        return products.findByActiveTrueOrderByPolicyTypeAscPolicyNameAsc().stream()
                .filter(product -> !held.contains(product.getId()))
                .filter(product -> product.acceptsEntryAge(needs.age()) && product.acceptsEntryAge(profileAge))
                .filter(product -> needs.policyType() == null || product.getPolicyType() == needs.policyType())
                .toList();
    }

    private Outcome noCandidates(CustomerNeeds needs) {
        String type = needs.policyType() == null ? "" : needs.policyType().name().toLowerCase(Locale.ROOT) + " ";
        String notice = "There are no %sproducts open to someone aged %d that you don't already hold."
                .formatted(type, needs.age());
        return new Outcome(GenerationSource.RULE_BASED, RuleBasedRanker.NAME, null, RuleBasedRanker.VERSION, null,
                List.of(), notice);
    }

    private Outcome rank(CustomerNeeds needs, List<InsuranceProduct> candidates) {
        try {
            InsuranceAdvisor.Result result = advisor.rank(needs, candidates);
            List<ValidPick> picks = result == null ? List.of() : validate(result.picks(), candidates);
            if (!picks.isEmpty()) {
                return new Outcome(GenerationSource.MODEL, result.modelName(), result.modelVersion(),
                        properties.promptVersion(), result.promptText(), picks, null);
            }
            log.warn("Model answer had no usable picks, using rule-based ranking");
        } catch (RuntimeException ex) {
            // the customer should never see an error because the model is down
            logModelFailure(ex);
        }
        List<AdvisorPick> picks = ranker.rank(needs, candidates, properties.maxRecommendations());
        return new Outcome(GenerationSource.RULE_BASED, RuleBasedRanker.NAME, null, RuleBasedRanker.VERSION, null,
                validate(picks, candidates), FALLBACK_NOTICE);
    }

    /** Keeps picks whose code is a candidate, first occurrence only, capped at the configured maximum. */
    private List<ValidPick> validate(List<AdvisorPick> picks, List<InsuranceProduct> candidates) {
        if (picks == null) {
            return List.of();
        }
        Map<String, InsuranceProduct> byCode = candidates.stream()
                .collect(Collectors.toMap(InsuranceProduct::getProductCode, Function.identity()));
        Set<String> seen = new HashSet<>();
        List<ValidPick> valid = new ArrayList<>();
        for (AdvisorPick pick : picks) {
            if (valid.size() == properties.maxRecommendations()) {
                break;
            }
            if (pick == null || pick.productCode() == null || pick.suitability() == null
                    || !StringUtils.hasText(pick.reason())) {
                continue;
            }
            String code = pick.productCode().trim().toUpperCase(Locale.ROOT);
            InsuranceProduct product = byCode.get(code);
            if (product == null) {
                log.info("Dropped recommended code {}: not an eligible candidate", code);
                continue;
            }
            if (seen.add(code)) {
                valid.add(new ValidPick(product, truncate(pick.reason().trim()), pick.suitability()));
            }
        }
        return valid;
    }

    /** Says which kind of failure it was, so an operator can tell a bad key from a provider outage. */
    private static void logModelFailure(RuntimeException ex) {
        if (ex instanceof ResourceAccessException io) {
            // the root cause's type (ConnectException, SocketTimeoutException) tells an outage from a slow model
            log.warn("Model unreachable, using rule-based ranking: {}", io.getMostSpecificCause().toString());
        } else if (ex instanceof NonTransientAiException) {
            log.warn("Model rejected the request (check AI_API_KEY and AI_MODEL), using rule-based ranking: {}",
                    ex.getMessage());
        } else if (ex instanceof TransientAiException) {
            log.warn("Model temporarily unavailable after retries, using rule-based ranking: {}", ex.getMessage());
        } else {
            log.warn("Model call failed, using rule-based ranking: {}", ex.toString());
        }
    }

    private static String truncate(String reason) {
        return reason.length() <= MAX_REASON_LENGTH ? reason : reason.substring(0, MAX_REASON_LENGTH);
    }

    private static RecommendationResponse toResponse(AiRecommendationRequest request, String notice) {
        List<RecommendedProductResponse> items = request.getItems().stream()
                .map(item -> {
                    InsuranceProduct p = item.getProduct();
                    return new RecommendedProductResponse(item.getId(), item.getRankOrder(), p.getProductCode(),
                            p.getPolicyName(), p.getPolicyType(), p.getTermMonths(), p.getSumInsured(),
                            p.getPremiumFrequency(), p.getPremiumAmount(), item.getRecommendationReason(),
                            item.getSuitability());
                })
                .toList();
        return new RecommendationResponse(request.getId(), request.getCalculatedAge(), request.getGenerationSource(),
                request.getModelName(), request.getRequestedAt(), notice, items);
    }

    private record ValidPick(InsuranceProduct product, String reason, Suitability suitability) {
    }

    private record Outcome(GenerationSource source, String modelName, String modelVersion, String promptVersion,
                           String promptText, List<ValidPick> picks, String notice) {
    }
}
