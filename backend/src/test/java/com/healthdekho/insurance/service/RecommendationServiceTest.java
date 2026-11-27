package com.healthdekho.insurance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.healthdekho.insurance.ai.AdvisorPick;
import com.healthdekho.insurance.ai.AiProperties;
import com.healthdekho.insurance.ai.CustomerNeeds;
import com.healthdekho.insurance.ai.InsuranceAdvisor;
import com.healthdekho.insurance.ai.RuleBasedRanker;
import com.healthdekho.insurance.domain.GenerationSource;
import com.healthdekho.insurance.domain.PolicyType;
import com.healthdekho.insurance.domain.PremiumFrequency;
import com.healthdekho.insurance.domain.Role;
import com.healthdekho.insurance.domain.Suitability;
import com.healthdekho.insurance.dto.recommendation.RecommendationRequest;
import com.healthdekho.insurance.dto.recommendation.RecommendationResponse;
import com.healthdekho.insurance.dto.recommendation.RecommendedProductResponse;
import com.healthdekho.insurance.entity.AiRecommendationRequest;
import com.healthdekho.insurance.entity.Customer;
import com.healthdekho.insurance.entity.CustomerPolicy;
import com.healthdekho.insurance.entity.InsuranceProduct;
import com.healthdekho.insurance.repository.AiRecommendationRequestRepository;
import com.healthdekho.insurance.repository.CustomerPolicyRepository;
import com.healthdekho.insurance.repository.CustomerRepository;
import com.healthdekho.insurance.repository.InsuranceProductRepository;
import com.healthdekho.insurance.security.AuthenticatedUser;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);
    private static final AuthenticatedUser USER = new AuthenticatedUser(1001L, "a@example.com", "Aarav", Role.CUSTOMER);

    @Mock CustomerRepository customers;
    @Mock InsuranceProductRepository products;
    @Mock CustomerPolicyRepository policies;
    @Mock AiRecommendationRequestRepository requests;
    @Mock InsuranceAdvisor advisor;
    @Captor ArgumentCaptor<List<InsuranceProduct>> candidatesCaptor;

    private final Customer customer = withId(new Customer("a@example.com", "x", "Aarav", LocalDate.of(1990, 4, 15),
            "9876501001"), 1001L);
    private final InsuranceProduct heldTerm = product(201L, "HD-TERM-001", PolicyType.TERM, 18, 60);
    private final InsuranceProduct health = product(202L, "HD-HEALTH-001", PolicyType.HEALTH, 18, 65);
    private final InsuranceProduct savings = product(203L, "HD-SAV-001", PolicyType.SAVINGS, 21, 55);
    private final InsuranceProduct critical = product(204L, "HD-CRIT-001", PolicyType.HEALTH, 25, 60);
    private final InsuranceProduct pension = product(206L, "HD-RET-001", PolicyType.RETIREMENT, 30, 55);
    private final InsuranceProduct seniorsOnly = product(209L, "HD-SENIOR-001", PolicyType.HEALTH, 55, 75);

    private RecommendationService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(TODAY.atTime(10, 0).atZone(ZONE).toInstant(), ZONE);
        service = new RecommendationService(customers, products, policies, requests, advisor, new RuleBasedRanker(),
                new AiProperties("rec-v1", 3), clock);
        when(customers.findById(1001L)).thenReturn(Optional.of(customer));
        when(policies.findByCustomerIdOrderByStartDateDesc(1001L)).thenReturn(List.of(
                new CustomerPolicy("HDP-2026-0001", customer, heldTerm, TODAY, TODAY.plusYears(20),
                        new BigDecimal("300000.00"))));
        when(products.findByActiveTrueOrderByPolicyTypeAscPolicyNameAsc())
                .thenReturn(List.of(heldTerm, health, critical, savings, pension, seniorsOnly));
        when(requests.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void hallucinatedCodesAreDropped() {
        modelReturns(pick("HD-FAKE-999"), pick("HD-HEALTH-001"), pick("hd-sav-001 "));

        RecommendationResponse response = service.recommend(USER, goals("health"));

        assertThat(response.source()).isEqualTo(GenerationSource.MODEL);
        assertThat(response.items()).extracting(RecommendedProductResponse::productCode)
                .containsExactly("HD-HEALTH-001", "HD-SAV-001");
        assertThat(response.items()).extracting(RecommendedProductResponse::rank).containsExactly(1, 2);
        assertThat(response.notice()).isNull();
    }

    @Test
    void duplicatesAreDropped() {
        modelReturns(pick("HD-HEALTH-001"), pick("HD-HEALTH-001"), pick("HD-RET-001"));

        RecommendationResponse response = service.recommend(USER, goals("health"));

        assertThat(response.items()).extracting(RecommendedProductResponse::productCode)
                .containsExactly("HD-HEALTH-001", "HD-RET-001");
    }

    @Test
    void heldAndIneligibleProductsAreNeverSentOrReturned() {
        modelReturns(pick("HD-TERM-001"), pick("HD-SENIOR-001"), pick("HD-CRIT-001"));

        RecommendationResponse response = service.recommend(USER, goals("cover"));

        verify(advisor).rank(any(), candidatesCaptor.capture());
        assertThat(candidatesCaptor.getValue()).extracting(InsuranceProduct::getProductCode)
                .doesNotContain("HD-TERM-001", "HD-SENIOR-001");
        assertThat(response.items()).extracting(RecommendedProductResponse::productCode)
                .containsExactly("HD-CRIT-001");
    }

    @Test
    void typeFilterIsAppliedBeforeTheModelCall() {
        modelReturns(pick("HD-HEALTH-001"));

        service.recommend(USER, new RecommendationRequest(null, null, null, PolicyType.HEALTH));

        verify(advisor).rank(any(), candidatesCaptor.capture());
        assertThat(candidatesCaptor.getValue()).extracting(InsuranceProduct::getProductCode)
                .containsExactly("HD-HEALTH-001", "HD-CRIT-001");
    }

    @Test
    void resultIsCappedAtTheConfiguredMaximum() {
        modelReturns(pick("HD-HEALTH-001"), pick("HD-CRIT-001"), pick("HD-SAV-001"), pick("HD-RET-001"));

        RecommendationResponse response = service.recommend(USER, goals("anything"));

        assertThat(response.items()).hasSize(3);
    }

    @Test
    void modelExceptionFallsBackToRuleBasedRanking() {
        when(advisor.rank(any(), anyList())).thenThrow(new RuntimeException("connection refused"));

        RecommendationResponse response = service.recommend(USER, goals("hospital bills"));

        assertThat(response.source()).isEqualTo(GenerationSource.RULE_BASED);
        assertThat(response.modelName()).isEqualTo(RuleBasedRanker.NAME);
        assertThat(response.notice()).isEqualTo(RecommendationService.FALLBACK_NOTICE);
        assertThat(response.items()).isNotEmpty().first()
                .extracting(RecommendedProductResponse::policyType).isEqualTo(PolicyType.HEALTH);
    }

    @Test
    void emptyModelAnswerFallsBack() {
        modelReturns();

        assertThat(service.recommend(USER, goals("health")).source()).isEqualTo(GenerationSource.RULE_BASED);
    }

    @Test
    void answerWithOnlyInvalidCodesFallsBack() {
        modelReturns(pick("HD-TERM-001"), pick("NOPE"));

        RecommendationResponse response = service.recommend(USER, goals("health"));

        assertThat(response.source()).isEqualTo(GenerationSource.RULE_BASED);
        assertThat(response.items()).isNotEmpty()
                .extracting(RecommendedProductResponse::productCode).doesNotContain("HD-TERM-001", "NOPE");
    }

    @Test
    void picksWithoutReasonOrSuitabilityAreDropped() {
        modelReturns(new AdvisorPick("HD-HEALTH-001", " ", Suitability.HIGH),
                new AdvisorPick("HD-CRIT-001", "Good fit.", null), pick("HD-SAV-001"));

        RecommendationResponse response = service.recommend(USER, goals("health"));

        assertThat(response.items()).extracting(RecommendedProductResponse::productCode).containsExactly("HD-SAV-001");
    }

    @Test
    void noCandidatesMeansNoModelCallAndAnExplanation() {
        RecommendationRequest eightyYearsOld = new RecommendationRequest(LocalDate.of(1946, 1, 1), null, null, null);

        RecommendationResponse response = service.recommend(USER, eightyYearsOld);

        verifyNoInteractions(advisor);
        assertThat(response.calculatedAge()).isEqualTo(80);
        assertThat(response.source()).isEqualTo(GenerationSource.RULE_BASED);
        assertThat(response.items()).isEmpty();
        assertThat(response.notice()).contains("no products open to someone aged 80");
        verify(requests).save(any(AiRecommendationRequest.class));
    }

    @Test
    void ageComesFromTheProfileUnlessADateOfBirthIsGiven() {
        modelReturns(pick("HD-HEALTH-001"));

        assertThat(service.recommend(USER, goals("health")).calculatedAge()).isEqualTo(36);
        assertThat(service.recommend(USER, new RecommendationRequest(LocalDate.of(2000, 11, 26), null, null, null))
                .calculatedAge()).isEqualTo(25);
    }

    @Test
    void modelMetadataAndPromptAreRecorded() {
        when(advisor.rank(any(), anyList())).thenReturn(new InsuranceAdvisor.Result(List.of(pick("HD-HEALTH-001")),
                "gpt-4o-mini", "gpt-4o-mini-2024-07-18", "the prompt"));
        ArgumentCaptor<AiRecommendationRequest> saved = ArgumentCaptor.forClass(AiRecommendationRequest.class);

        service.recommend(USER, new RecommendationRequest(null, "  health  ", 1, null));

        verify(requests).save(saved.capture());
        AiRecommendationRequest request = saved.getValue();
        assertThat(request.getInsuranceGoals()).isEqualTo("health");
        assertThat(request.getModelName()).isEqualTo("gpt-4o-mini");
        assertThat(request.getModelVersion()).isEqualTo("gpt-4o-mini-2024-07-18");
        assertThat(request.getPromptVersion()).isEqualTo("rec-v1");
        assertThat(request.getPromptText()).isEqualTo("the prompt");
        assertThat(request.getItems()).hasSize(1);
    }

    private void modelReturns(AdvisorPick... picks) {
        when(advisor.rank(any(CustomerNeeds.class), anyList()))
                .thenReturn(new InsuranceAdvisor.Result(List.of(picks), "gpt-4o-mini", null, "prompt"));
    }

    private static AdvisorPick pick(String code) {
        return new AdvisorPick(code, "Fits your goals.", Suitability.MEDIUM);
    }

    private static RecommendationRequest goals(String goals) {
        return new RecommendationRequest(null, goals, null, null);
    }

    private static InsuranceProduct product(Long id, String code, PolicyType type, int minAge, int maxAge) {
        InsuranceProduct product = new InsuranceProduct(code, code + " plan", type, null, 120,
                PremiumFrequency.YEARLY, new BigDecimal("5000.00"), new BigDecimal("500000.00"), minAge, maxAge);
        return withId(product, id);
    }

    private static <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
