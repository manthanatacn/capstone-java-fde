package com.healthdekho.insurance.ai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.healthdekho.insurance.domain.Formats;
import com.healthdekho.insurance.domain.PolicyType;
import com.healthdekho.insurance.domain.Suitability;
import com.healthdekho.insurance.entity.InsuranceProduct;

/**
 * Deterministic ranking used when the model is unavailable or gives nothing usable.
 * Points: requested type +3, a goal word that fits the product's type +2, term within 2 years of
 * the preferred term +2 (within 5 years +1).
 */
@Component
public class RuleBasedRanker {

    public static final String NAME = "rule-based";
    public static final String VERSION = "rules-v1";

    // matched as word prefixes so "retirement", "hospitalisation" and "children" count
    private static final Map<PolicyType, List<String>> GOAL_STEMS = Map.of(
            PolicyType.HEALTH, List.of("health", "medic", "hospital", "illness", "disease", "cancer", "critical",
                    "surgery"),
            PolicyType.TERM, List.of("family", "income", "protect", "loan", "mortgage", "depend", "life"),
            PolicyType.SAVINGS, List.of("sav", "wealth", "child", "educat", "invest", "corpus"),
            PolicyType.RETIREMENT, List.of("retir", "pension", "annuit"));

    private static final int MAX_WORDS_IN_REASON = 3;

    public List<AdvisorPick> rank(CustomerNeeds needs, List<InsuranceProduct> candidates, int limit) {
        Set<String> goalWords = words(needs.goals());
        return candidates.stream()
                .map(product -> score(product, needs, goalWords))
                .sorted(Comparator.comparingInt(Scored::points).reversed()
                        .thenComparing(s -> s.product().getProductCode()))
                .limit(limit)
                .map(Scored::toPick)
                .toList();
    }

    private static Scored score(InsuranceProduct product, CustomerNeeds needs, Set<String> goalWords) {
        int points = 0;
        List<String> reasons = new ArrayList<>();
        String type = product.getPolicyType().name().toLowerCase(Locale.ROOT);

        if (product.getPolicyType() == needs.policyType()) {
            points += 3;
            reasons.add("It is the %s cover you asked for.".formatted(type));
        }

        List<String> stems = GOAL_STEMS.get(product.getPolicyType());
        List<String> matched = goalWords.stream()
                .filter(word -> stems.stream().anyMatch(word::startsWith))
                .limit(MAX_WORDS_IN_REASON)
                .toList();
        if (!matched.isEmpty()) {
            points += 2;
            reasons.add("Your goals mention %s, which %s insurance is meant for."
                    .formatted(String.join(", ", matched), type));
        }

        if (needs.preferredTermYears() != null) {
            int gapMonths = Math.abs(product.getTermMonths() - needs.preferredTermYears() * 12);
            if (gapMonths <= 24) {
                points += 2;
                reasons.add(termReason(product, needs, "close to"));
            } else if (gapMonths <= 60) {
                points += 1;
                reasons.add(termReason(product, needs, "within five years of"));
            }
        }

        if (reasons.isEmpty()) {
            reasons.add("Open to you at age %d; a general option to consider.".formatted(needs.age()));
        }
        return new Scored(product, points, String.join(" ", reasons));
    }

    private static String termReason(InsuranceProduct product, CustomerNeeds needs, String closeness) {
        return "Its %s term is %s the %s you prefer."
                .formatted(duration(product.getTermMonths()), closeness, Formats.years(needs.preferredTermYears()));
    }

    private static String duration(int months) {
        return months % 12 == 0 ? (months / 12) + "-year" : months + "-month";
    }

    private static Set<String> words(String goals) {
        if (goals == null || goals.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(goals.toLowerCase(Locale.ROOT).split("[^a-z]+"))
                .filter(word -> !word.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private record Scored(InsuranceProduct product, int points, String reason) {

        AdvisorPick toPick() {
            Suitability suitability = points >= 4 ? Suitability.HIGH
                    : points >= 2 ? Suitability.MEDIUM
                    : Suitability.LOW;
            return new AdvisorPick(product.getProductCode(), reason, suitability);
        }
    }
}
