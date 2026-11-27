package com.healthdekho.insurance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.healthdekho.insurance.ai.AdvisorPick;
import com.healthdekho.insurance.ai.InsuranceAdvisor;
import com.healthdekho.insurance.domain.Suitability;
import com.healthdekho.insurance.support.IntegrationTest;

class RecommendationApiTest extends IntegrationTest {

    @MockitoBean
    InsuranceAdvisor advisor;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void aaravNeverGetsTheTermPlanHeAlreadyHolds() throws Exception {
        modelReturns("HD-TERM-001", "HD-TERM-002");

        recommend(AARAV, "{\"goals\":\"protect my family income\",\"policyType\":\"TERM\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("MODEL"))
                .andExpect(jsonPath("$.calculatedAge").value(36))
                .andExpect(jsonPath("$.items[*].productCode").value(not(hasItem("HD-TERM-001"))))
                .andExpect(jsonPath("$.items[0].productCode").value("HD-TERM-002"))
                .andExpect(jsonPath("$.items[0].rank").value(1))
                .andExpect(jsonPath("$.items[0].premiumAmount").value(6800.00));
    }

    @Test
    void requestAndItemsArePersisted() throws Exception {
        modelReturns("HD-HEALTH-001", "HD-CRIT-001");

        String body = recommend(ROHAN, "{\"goals\":\"health cover for my parents\",\"policyType\":\"HEALTH\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andReturn().getResponse().getContentAsString();
        JsonNode response = json.readTree(body);
        long requestId = response.get("requestId").asLong();

        Map<String, Object> row = jdbc.queryForMap("""
                select customer_id, insurance_goals, preferred_policy_type, calculated_age, generation_source,
                       model_name, model_version, prompt_version, prompt_text
                from ai_recommendation_request where recommendation_request_id = ?""", requestId);
        assertThat(row).containsEntry("customer_id", 1003L)
                .containsEntry("insurance_goals", "health cover for my parents")
                .containsEntry("preferred_policy_type", "HEALTH")
                .containsEntry("calculated_age", 26)
                .containsEntry("generation_source", "MODEL")
                .containsEntry("model_name", "gpt-4o-mini")
                .containsEntry("model_version", "gpt-4o-mini-2024-07-18")
                .containsEntry("prompt_version", "rec-v1")
                .containsEntry("prompt_text", "prompt sent to the model");

        List<Map<String, Object>> items = jdbc.queryForList("""
                select i.recommendation_item_id, p.product_code, i.rank_order, i.suitability, i.selected_for_registration
                from ai_recommendation_item i join insurance_product p on p.product_id = i.product_id
                where i.recommendation_request_id = ? order by i.rank_order""", requestId);
        assertThat(items).extracting(item -> item.get("product_code")).containsExactly("HD-HEALTH-001", "HD-CRIT-001");
        assertThat(items).extracting(item -> item.get("selected_for_registration")).containsOnly(false);
        assertThat(items.getFirst().get("recommendation_item_id"))
                .isEqualTo(response.get("items").get(0).get("itemId").asLong());
    }

    @Test
    void modelFailureFallsBackToRulesWithoutAnError() throws Exception {
        when(advisor.rank(any(), anyList())).thenThrow(new IllegalStateException("AI API key is not configured"));

        String body = recommend(ROHAN, "{\"goals\":\"medical and hospital costs\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("RULE_BASED"))
                .andExpect(jsonPath("$.modelName").value("rule-based"))
                .andExpect(jsonPath("$.notice").isNotEmpty())
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.items[0].policyType").value("HEALTH"))
                .andReturn().getResponse().getContentAsString();

        long requestId = json.readTree(body).get("requestId").asLong();
        assertThat(jdbc.queryForObject(
                "select generation_source from ai_recommendation_request where recommendation_request_id = ?",
                String.class, requestId)).isEqualTo("RULE_BASED");
    }

    @Test
    void noEligibleProductsSkipsTheModelAndExplains() throws Exception {
        recommend(ROHAN, "{\"dateOfBirth\":\"1950-01-01\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calculatedAge").value(76))
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.notice").isNotEmpty());

        verifyNoInteractions(advisor);
    }

    @Test
    void inactiveProductIsNeverRecommended() throws Exception {
        modelReturns("HD-HEALTH-000", "HD-HEALTH-001");

        recommend(ROHAN, "{\"policyType\":\"HEALTH\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].productCode").value(everyItem(not("HD-HEALTH-000"))))
                .andExpect(jsonPath("$.items[0].productCode").value("HD-HEALTH-001"));
    }

    @Test
    void requestWithNoInputsIsRejected() throws Exception {
        recommend(ROHAN, "{\"goals\":\"   \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.recommendationRequest").isNotEmpty());
        verifyNoInteractions(advisor);
    }

    @Test
    void futureDateOfBirthIsRejected() throws Exception {
        recommend(ROHAN, "{\"dateOfBirth\":\"2030-01-01\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.dateOfBirth").value("Date of birth cannot be in the future"));
    }

    @Test
    void outOfRangeTermAndLongGoalsAreRejected() throws Exception {
        String goals = "a".repeat(501);
        recommend(ROHAN, "{\"goals\":\"" + goals + "\",\"preferredTermYears\":41}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.goals").isNotEmpty())
                .andExpect(jsonPath("$.errors.preferredTermYears").isNotEmpty());
    }

    @Test
    void staffCannotAskForRecommendations() throws Exception {
        recommend(OPS, "{\"goals\":\"health\"}").andExpect(status().isForbidden());
    }

    @Test
    void anonymousCallerIsRejected() throws Exception {
        mvc.perform(post("/api/recommendations").contentType(MediaType.APPLICATION_JSON).content("{\"goals\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void productsTheProfileAgeCannotRegisterAreNotSuggestedEvenForAnOlderTypedDateOfBirth() throws Exception {
        // Rohan is 26 on his profile; HD-RET-001 needs 30-55, and registration checks the profile age
        modelReturns("HD-RET-001", "HD-HEALTH-001");

        recommend(ROHAN, "{\"dateOfBirth\":\"1980-05-05\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calculatedAge").value(46))
                .andExpect(jsonPath("$.items[*].productCode", not(hasItem("HD-RET-001"))))
                .andExpect(jsonPath("$.items[*].productCode", hasItem("HD-HEALTH-001")));
    }

    @Test
    void fractionalTermIsRejectedNotTruncated() throws Exception {
        recommend(ROHAN, "{\"preferredTermYears\":2.7}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.preferredTermYears").value("Enter a whole number."));
    }

    @Test
    void preferredTermIsStoredAsTextLikeTheSpecSample() throws Exception {
        modelReturns("HD-TERM-001");

        String body = recommend(ROHAN, "{\"preferredTermYears\":20}")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        long requestId = json.readTree(body).get("requestId").asLong();
        assertThat(jdbc.queryForObject(
                "select preferred_term from ai_recommendation_request where recommendation_request_id = ?",
                String.class, requestId)).isEqualTo("20 years");
    }

    @Test
    void unknownPolicyTypeListsTheAllowedValues() throws Exception {
        recommend(ROHAN, "{\"policyType\":\"CAR\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.policyType").value("Choose one of: TERM, HEALTH, SAVINGS, RETIREMENT."));
    }

    private ResultActions recommend(String email, String body) throws Exception {
        return mvc.perform(post("/api/recommendations")
                .header("Authorization", bearer(email))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private void modelReturns(String... codes) {
        List<AdvisorPick> picks = Arrays.stream(codes)
                .map(code -> new AdvisorPick(code, "Matches what you described.", Suitability.HIGH))
                .toList();
        when(advisor.rank(any(), anyList())).thenReturn(new InsuranceAdvisor.Result(picks, "gpt-4o-mini",
                "gpt-4o-mini-2024-07-18", "prompt sent to the model"));
    }
}
