package com.healthdekho.insurance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.healthdekho.insurance.support.IntegrationTest;

class PolicySearchApiTest extends IntegrationTest {

    private static final String SEARCH = "/api/policies/search";

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void staffFindsBothOfMeerasPoliciesByPartialName() throws Exception {
        mvc.perform(get(SEARCH).param("customerName", "meera").header("Authorization", bearer(OPS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].policyNumber").value("HDP-2026-0002"))
                .andExpect(jsonPath("$.content[0].premiumPaymentStatus").value("FULLY_PAID"))
                .andExpect(jsonPath("$.content[0].totalPremiumPayable").value(18000.00))
                .andExpect(jsonPath("$.content[0].totalPremiumPaid").value(18000.00))
                .andExpect(jsonPath("$.content[0].remainingPremium").value(0.00))
                .andExpect(jsonPath("$.content[1].policyNumber").value("HDP-2026-0003"))
                .andExpect(jsonPath("$.content[1].customerName").value("Meera Iyer"))
                .andExpect(jsonPath("$.content[1].dateOfBirth").value("1985-09-22"))
                .andExpect(jsonPath("$.content[1].policyName").value("HealthDekho Smart Savings"))
                .andExpect(jsonPath("$.content[1].policyType").value("SAVINGS"))
                .andExpect(jsonPath("$.content[1].startDate").value("2026-06-15"))
                .andExpect(jsonPath("$.content[1].maturityDate").value("2036-06-14"))
                .andExpect(jsonPath("$.content[1].premiumPaymentStatus").value("OVERDUE"))
                .andExpect(jsonPath("$.content[1].totalPremiumPayable").value(300000.00))
                .andExpect(jsonPath("$.content[1].totalPremiumPaid").value(7500.00))
                .andExpect(jsonPath("$.content[1].remainingPremium").value(292500.00))
                .andExpect(jsonPath("$.content[1].latePenalties").value(0.00));
    }

    @Test
    void resultsCarryExactlyTheV2Fields() throws Exception {
        String body = mvc.perform(get(SEARCH).param("policyNumber", "HDP-2026-0001")
                        .header("Authorization", bearer(OPS)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> fields = new ArrayList<>();
        json.readTree(body).get("content").get(0).fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactly("policyNumber", "customerName", "dateOfBirth", "policyName", "policyType",
                "startDate", "maturityDate", "totalPremiumPayable", "premiumPaymentStatus", "totalPremiumPaid",
                "remainingPremium", "latePenalties");
    }

    @Test
    void premiumPaymentStatusIsWorkedOutFromDatesNotFromTheStoredStatus() throws Exception {
        // a stored status is only refreshed when the policy is opened, so it can be stale
        jdbc.update("update premium_installment set payment_status = 'PENDING'"
                + " where policy_id = 3003 and installment_number = 2");

        mvc.perform(get(SEARCH).param("policyNumber", "HDP-2026-0003").header("Authorization", bearer(OPS)))
                .andExpect(jsonPath("$.content[0].premiumPaymentStatus").value("OVERDUE"));
    }

    @Test
    void wildcardCharactersInANameAreMatchedLiterally() throws Exception {
        mvc.perform(get(SEARCH).param("customerName", "%%").header("Authorization", bearer(OPS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void absurdPageNumberIsABadRequestNotAServerError() throws Exception {
        mvc.perform(get(SEARCH).param("customerName", "ar").param("page", "50000000").param("size", "50")
                        .header("Authorization", bearer(OPS)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SEARCH_CRITERIA"));
    }

    @Test
    void badlyFormattedDateOfBirthIsAFieldError() throws Exception {
        mvc.perform(get(SEARCH).param("policyNumber", "HDP-2026-0001").param("dateOfBirth", "15-04-1990")
                        .header("Authorization", bearer(OPS)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.dateOfBirth").value("Enter a date as yyyy-mm-dd."));
    }

    @Test
    void searchByPolicyNumberShowsPremiumTotals() throws Exception {
        mvc.perform(get(SEARCH).param("policyNumber", "HDP-2026-0001").header("Authorization", bearer(OPS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].customerName").value("Aarav Sharma"))
                .andExpect(jsonPath("$.content[0].startDate").value("2026-11-20"))
                .andExpect(jsonPath("$.content[0].maturityDate").value("2046-11-19"))
                .andExpect(jsonPath("$.content[0].premiumPaymentStatus").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.content[0].totalPremiumPayable").value(300000.00))
                .andExpect(jsonPath("$.content[0].totalPremiumPaid").value(1750.00))
                .andExpect(jsonPath("$.content[0].remainingPremium").value(298250.00));
    }

    @Test
    void policyNumberWithMatchingDateOfBirthFindsThePolicy() throws Exception {
        mvc.perform(get(SEARCH).param("policyNumber", "HDP-2026-0001").param("dateOfBirth", "1990-04-15")
                        .header("Authorization", bearer(OPS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void policyNumberWithWrongDateOfBirthIsAnEmptyPage() throws Exception {
        mvc.perform(get(SEARCH).param("policyNumber", "HDP-2026-0001").param("dateOfBirth", "1990-04-16")
                        .header("Authorization", bearer(OPS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void dateOfBirthAloneIsRejected() throws Exception {
        mvc.perform(get(SEARCH).param("dateOfBirth", "1990-04-15").header("Authorization", bearer(OPS)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SEARCH_CRITERIA"))
                .andExpect(jsonPath("$.detail").value(containsString("Policy Identifier")));
    }

    @Test
    void malformedDateOfBirthIsABadRequest() throws Exception {
        mvc.perform(get(SEARCH).param("policyNumber", "HDP-2026-0001").param("dateOfBirth", "15/04/1990")
                        .header("Authorization", bearer(OPS)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void customerOnlyGetsTheirOwnPoliciesBack() throws Exception {
        String aarav = bearer(AARAV);
        mvc.perform(get(SEARCH).param("customerName", "meera").header("Authorization", aarav))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
        mvc.perform(get(SEARCH).param("policyNumber", "HDP-2026-0003").header("Authorization", aarav))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
        mvc.perform(get(SEARCH).param("customerName", "aarav").header("Authorization", aarav))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].policyNumber").value("HDP-2026-0001"));
    }

    @Test
    void resultsArePaged() throws Exception {
        mvc.perform(get(SEARCH).param("customerName", "meera").param("page", "1").param("size", "1")
                        .header("Authorization", bearer(OPS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].policyNumber").value("HDP-2026-0003"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void defaultPageSizeIsTenAndLargerSizesAreCapped() throws Exception {
        String ops = bearer(OPS);
        mvc.perform(get(SEARCH).param("customerName", "meera").header("Authorization", ops))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10));
        mvc.perform(get(SEARCH).param("customerName", "meera").param("size", "500").header("Authorization", ops))
                .andExpect(jsonPath("$.size").value(50));
    }

    @Test
    void searchPathIsNotTreatedAsAPolicyNumber() throws Exception {
        // with no criteria, the search endpoint answers (400), not the policy lookup (which would 404)
        mvc.perform(get(SEARCH).header("Authorization", bearer(OPS)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SEARCH_CRITERIA"));
    }

    @Test
    void searchNeedsAToken() throws Exception {
        mvc.perform(get(SEARCH).param("customerName", "meera"))
                .andExpect(status().isUnauthorized());
    }
}
