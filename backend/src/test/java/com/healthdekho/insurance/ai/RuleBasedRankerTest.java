package com.healthdekho.insurance.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.healthdekho.insurance.domain.PolicyType;
import com.healthdekho.insurance.domain.PremiumFrequency;
import com.healthdekho.insurance.domain.Suitability;
import com.healthdekho.insurance.entity.InsuranceProduct;

class RuleBasedRankerTest {

    private final RuleBasedRanker ranker = new RuleBasedRanker();

    private final InsuranceProduct term20 = product("TERM-20", PolicyType.TERM, 240);
    private final InsuranceProduct term10 = product("TERM-10", PolicyType.TERM, 120);
    private final InsuranceProduct health = product("HEALTH-1", PolicyType.HEALTH, 12);
    private final InsuranceProduct savings = product("SAV-10", PolicyType.SAVINGS, 120);
    private final InsuranceProduct pension = product("RET-20", PolicyType.RETIREMENT, 240);
    private final List<InsuranceProduct> all = List.of(term20, term10, health, savings, pension);

    @Test
    void goalWordsPointAtTheMatchingType() {
        List<AdvisorPick> picks = ranker.rank(needs("Cover for hospitalisation and medical bills", null, null), all, 3);

        assertThat(picks.getFirst().productCode()).isEqualTo("HEALTH-1");
        assertThat(picks.getFirst().suitability()).isEqualTo(Suitability.MEDIUM);
        assertThat(picks.getFirst().reason()).contains("hospitalisation", "medical");
    }

    @Test
    void retirementWordsMatchThePensionPlan() {
        List<AdvisorPick> picks = ranker.rank(needs("Planning my retirement, want a pension", null, null), all, 3);

        assertThat(picks.getFirst().productCode()).isEqualTo("RET-20");
    }

    @Test
    void requestedTypeGoalAndTermTogetherGiveHighSuitability() {
        List<AdvisorPick> picks = ranker.rank(needs("protect my family income", 20, PolicyType.TERM), all, 3);

        assertThat(picks).extracting(AdvisorPick::productCode).startsWith("TERM-20", "TERM-10");
        assertThat(picks.getFirst().suitability()).isEqualTo(Suitability.HIGH);
        assertThat(picks.getFirst().reason()).contains("term cover you asked for", "20-year term is close to the 20 years you prefer");
    }

    @Test
    void preferredTermBreaksTiesBetweenProductsOfTheSameType() {
        List<AdvisorPick> picks = ranker.rank(needs(null, 10, PolicyType.TERM), List.of(term20, term10), 3);

        assertThat(picks).extracting(AdvisorPick::productCode).containsExactly("TERM-10", "TERM-20");
    }

    @Test
    void returnsAtMostTheLimit() {
        assertThat(ranker.rank(needs("savings", null, null), all, 3)).hasSize(3);
        assertThat(ranker.rank(needs("savings", null, null), all, 1)).hasSize(1);
    }

    @Test
    void nothingMatchingStillGivesADeterministicLowSuitabilityList() {
        List<AdvisorPick> picks = ranker.rank(needs("something unrelated", null, null), all, 3);

        assertThat(picks).extracting(AdvisorPick::productCode).containsExactly("HEALTH-1", "RET-20", "SAV-10");
        assertThat(picks).allSatisfy(pick -> {
            assertThat(pick.suitability()).isEqualTo(Suitability.LOW);
            assertThat(pick.reason()).isNotBlank();
        });
    }

    @Test
    void emptyCandidatesGiveEmptyList() {
        assertThat(ranker.rank(needs("health", null, null), List.of(), 3)).isEmpty();
    }

    private static CustomerNeeds needs(String goals, Integer termYears, PolicyType type) {
        return new CustomerNeeds(36, goals, termYears, type);
    }

    private static InsuranceProduct product(String code, PolicyType type, int termMonths) {
        return new InsuranceProduct(code, code + " plan", type, null, termMonths, PremiumFrequency.YEARLY,
                new BigDecimal("1000.00"), new BigDecimal("100000.00"), 18, 65);
    }
}
