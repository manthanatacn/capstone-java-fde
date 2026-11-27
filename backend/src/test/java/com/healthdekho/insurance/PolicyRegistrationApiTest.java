package com.healthdekho.insurance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import com.healthdekho.insurance.repository.AiRecommendationItemRepository;
import com.healthdekho.insurance.support.IntegrationTest;
import com.healthdekho.insurance.support.TestClockConfig;

class PolicyRegistrationApiTest extends IntegrationTest {

    private static final LocalDate TODAY = TestClockConfig.TODAY;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AiRecommendationItemRepository recommendationItems;

    @Test
    void dashboardListsTheSignedInCustomersPolicies() throws Exception {
        mvc.perform(get("/api/customers/me/policies").header("Authorization", bearer(AARAV)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].policyNumber").value("HDP-2026-0001"))
                .andExpect(jsonPath("$[0].productCode").value("HD-TERM-001"))
                .andExpect(jsonPath("$[0].policyName").value("HealthDekho Secure Term"))
                .andExpect(jsonPath("$[0].policyType").value("TERM"))
                .andExpect(jsonPath("$[0].termMonths").value(240))
                .andExpect(jsonPath("$[0].startDate").value("2026-11-20"))
                .andExpect(jsonPath("$[0].maturityDate").value("2046-11-19"))
                .andExpect(jsonPath("$[0].premiumFrequency").value("MONTHLY"))
                .andExpect(jsonPath("$[0].premiumAmount").value(1250.00))
                .andExpect(jsonPath("$[0].sumInsured").value(5000000.00))
                .andExpect(jsonPath("$[0].totalPremiumPayable").value(300000.00))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    void customerWithoutPoliciesGetsAnEmptyList() throws Exception {
        mvc.perform(get("/api/customers/me/policies").header("Authorization", bearer(ROHAN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void availableProductsExcludeHeldAndInactiveProducts() throws Exception {
        mvc.perform(get("/api/products/available").header("Authorization", bearer(AARAV)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[*].productCode", not(hasItem("HD-TERM-001"))))
                .andExpect(jsonPath("$[*].productCode", not(hasItem("HD-HEALTH-000"))));

        mvc.perform(get("/api/products/available").header("Authorization", bearer(ROHAN)))
                .andExpect(jsonPath("$", hasSize(7)))
                .andExpect(jsonPath("$[*].productCode", hasItem("HD-TERM-001")));
    }

    @Test
    void singleProductIsReturnedOnlyWhileActive() throws Exception {
        String token = bearer(ROHAN);
        mvc.perform(get("/api/products/HD-SAV-001").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policyName").value("HealthDekho Smart Savings"))
                .andExpect(jsonPath("$.minEntryAge").value(21));
        mvc.perform(get("/api/products/HD-HEALTH-000").header("Authorization", token))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/products/NO-SUCH").header("Authorization", token))
                .andExpect(status().isNotFound());
    }

    @Test
    void previewFillsCustomerAndProductAndCalculatesFromToday() throws Exception {
        mvc.perform(get("/api/policies/registration-preview").param("productCode", "HD-TERM-001")
                        .header("Authorization", bearer(ROHAN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customer.fullName").value("Rohan Verma"))
                .andExpect(jsonPath("$.customer.dateOfBirth").value("2000-01-10"))
                .andExpect(jsonPath("$.customer.email").value(ROHAN))
                .andExpect(jsonPath("$.customer.phone").value("9876501003"))
                .andExpect(jsonPath("$.product.productCode").value("HD-TERM-001"))
                .andExpect(jsonPath("$.product.premiumAmount").value(1250.00))
                .andExpect(jsonPath("$.startDate").value("2026-11-25"))
                .andExpect(jsonPath("$.maturityDate").value("2046-11-24"))
                .andExpect(jsonPath("$.installmentCount").value(240))
                .andExpect(jsonPath("$.totalPremiumPayable").value(300000.00))
                .andExpect(jsonPath("$.ageAtStart").value(26))
                .andExpect(jsonPath("$.eligible").value(true))
                .andExpect(jsonPath("$.ineligibilityReason").value(nullValue()));
    }

    @Test
    void previewReportsIneligibilityInsteadOfFailing() throws Exception {
        mvc.perform(get("/api/policies/registration-preview").param("productCode", "HD-RET-001")
                        .param("startDate", "2026-12-01")
                        .header("Authorization", bearer(ROHAN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startDate").value("2026-12-01"))
                .andExpect(jsonPath("$.maturityDate").value("2046-11-30"))
                .andExpect(jsonPath("$.eligible").value(false))
                .andExpect(jsonPath("$.ineligibilityReason")
                        .value("Customer age 26 is outside the entry age 30–55 for this product."));

        mvc.perform(get("/api/policies/registration-preview").param("productCode", "HD-TERM-001")
                        .header("Authorization", bearer(AARAV)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(false))
                .andExpect(jsonPath("$.ineligibilityReason").value(matchesPattern("You already hold .*")));
    }

    @Test
    void previewRejectsStartDateOutsideTheWindow() throws Exception {
        mvc.perform(get("/api/policies/registration-preview").param("productCode", "HD-TERM-001")
                        .param("startDate", TODAY.plusDays(91).toString())
                        .header("Authorization", bearer(ROHAN)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("START_DATE_OUT_OF_RANGE"));
    }

    @Test
    void registrationCreatesPolicyWithFullSchedule() throws Exception {
        String token = bearer(ROHAN);
        String body = register(token, Map.of("productCode", "HD-SAV-001", "startDate", TODAY.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.policyNumber", matchesPattern("HDP-2026-\\d{4}")))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.policy.productCode").value("HD-SAV-001"))
                .andExpect(jsonPath("$.policy.startDate").value("2026-11-25"))
                .andExpect(jsonPath("$.policy.maturityDate").value("2036-11-24"))
                .andExpect(jsonPath("$.policy.premiumFrequency").value("QUARTERLY"))
                .andExpect(jsonPath("$.policy.totalPremiumPayable").value(300000.00))
                .andExpect(jsonPath("$.policy.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();
        String policyNumber = json.readTree(body).get("policyNumber").asText();

        Map<String, Object> schedule = jdbc.queryForMap("""
                select count(*) as installments, min(i.due_date) as first_due, max(i.due_date) as last_due,
                       sum(i.scheduled_amount) as total
                from premium_installment i join customer_policy p on p.policy_id = i.policy_id
                where p.policy_number = ?""", policyNumber);
        assertThat(schedule.get("installments")).isEqualTo(40L);
        assertThat(schedule.get("first_due").toString()).isEqualTo("2026-11-25");
        assertThat(schedule.get("last_due").toString()).isEqualTo("2036-08-25");
        assertThat(schedule.get("total").toString()).isEqualTo("300000.00");

        mvc.perform(get("/api/customers/me/policies").header("Authorization", token))
                .andExpect(jsonPath("$[0].policyNumber").value(policyNumber));
        mvc.perform(get("/api/products/available").header("Authorization", token))
                .andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[*].productCode", not(hasItem("HD-SAV-001"))));
    }

    @Test
    void registeringFromARecommendationMarksTheItemSelected() throws Exception {
        assertThat(recommendationItems.findById(7003L).orElseThrow().isSelectedForRegistration()).isFalse();

        register(bearer(ROHAN), Map.of("productCode", "HD-HEALTH-001", "startDate", TODAY.toString(),
                "recommendationItemId", 7003))
                .andExpect(status().isCreated());

        assertThat(recommendationItems.findById(7003L).orElseThrow().isSelectedForRegistration()).isTrue();
    }

    @Test
    void secondRegistrationOfTheSameProductIsAConflict() throws Exception {
        register(bearer(AARAV), Map.of("productCode", "HD-TERM-001", "startDate", TODAY.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_POLICY"));
    }

    @Test
    void ageOutsideTheEntryBandIsRejected() throws Exception {
        register(bearer(ROHAN), Map.of("productCode", "HD-RET-001", "startDate", TODAY.toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ENTRY_AGE_NOT_ELIGIBLE"))
                .andExpect(jsonPath("$.detail").value("Customer age 26 is outside the entry age 30–55 for this product."));
    }

    @Test
    void startDateInThePastOrTooFarAheadIsRejected() throws Exception {
        String token = bearer(ROHAN);
        register(token, Map.of("productCode", "HD-TERM-001", "startDate", TODAY.minusDays(1).toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("START_DATE_OUT_OF_RANGE"));
        register(token, Map.of("productCode", "HD-TERM-001", "startDate", TODAY.plusDays(91).toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("START_DATE_OUT_OF_RANGE"));
        register(token, Map.of("productCode", "HD-TERM-001", "startDate", TODAY.plusDays(90).toString()))
                .andExpect(status().isCreated());
    }

    @Test
    void inactiveProductCannotBeRegistered() throws Exception {
        register(bearer(ROHAN), Map.of("productCode", "HD-HEALTH-000", "startDate", TODAY.toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_AVAILABLE"));
    }

    @Test
    void missingStartDateIsAFieldError() throws Exception {
        register(bearer(ROHAN), Map.of("productCode", "HD-TERM-001"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.startDate").value("Start date is required"));
    }

    @Test
    void recommendationItemOfAnotherCustomerIsRejected() throws Exception {
        // 7001 is Aarav's recommendation for HD-TERM-001, a product Rohan could otherwise register
        register(bearer(ROHAN), Map.of("productCode", "HD-TERM-001", "startDate", TODAY.toString(),
                "recommendationItemId", 7001))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_RECOMMENDATION_ITEM"));
    }

    @Test
    void recommendationItemForADifferentProductIsRejected() throws Exception {
        // 7003 is Rohan's own item, but for HD-HEALTH-001
        register(bearer(ROHAN), Map.of("productCode", "HD-TERM-001", "startDate", TODAY.toString(),
                "recommendationItemId", 7003))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_RECOMMENDATION_ITEM"));
    }

    @Test
    void staffCannotUseRegistrationEndpoints() throws Exception {
        String token = bearer(OPS);
        register(token, Map.of("productCode", "HD-TERM-001", "startDate", TODAY.toString()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/policies/registration-preview").param("productCode", "HD-TERM-001")
                        .header("Authorization", token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/products/available").header("Authorization", token))
                .andExpect(status().isForbidden());
    }

    @Test
    void badlyFormattedStartDateIsAFieldError() throws Exception {
        register(bearer(ROHAN), Map.of("productCode", "HD-HEALTH-001", "startDate", "01-12-2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.startDate").value("Enter a date as yyyy-mm-dd."));
    }

    private ResultActions register(String token, Map<String, Object> body) throws Exception {
        return mvc.perform(post("/api/policies")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }
}
