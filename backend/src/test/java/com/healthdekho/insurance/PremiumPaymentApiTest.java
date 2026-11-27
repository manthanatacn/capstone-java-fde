package com.healthdekho.insurance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import com.healthdekho.insurance.entity.Customer;
import com.healthdekho.insurance.entity.CustomerPolicy;
import com.healthdekho.insurance.entity.InsuranceProduct;
import com.healthdekho.insurance.entity.PremiumInstallment;
import com.healthdekho.insurance.repository.CustomerPolicyRepository;
import com.healthdekho.insurance.repository.CustomerRepository;
import com.healthdekho.insurance.repository.InsuranceProductRepository;
import com.healthdekho.insurance.repository.PremiumInstallmentRepository;
import com.healthdekho.insurance.support.IntegrationTest;
import com.healthdekho.insurance.support.TestClockConfig;

class PremiumPaymentApiTest extends IntegrationTest {

    private static final String AARAV_POLICY = "HDP-2026-0001";   // policy 3001
    private static final String MEERA_HEALTH = "HDP-2026-0002";   // policy 3002, fully paid
    private static final String MEERA_SAVINGS = "HDP-2026-0003";  // policy 3003 (demo data), #2 overdue
    private static final long AARAV_PAID = 4001;
    private static final long AARAV_PARTIAL = 4002;
    private static final long AARAV_THIRD = 4003;
    private static final long MEERA_HEALTH_PAID = 4004;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PremiumInstallmentRepository installments;

    @Autowired
    private CustomerRepository customers;

    @Autowired
    private InsuranceProductRepository products;

    @Autowired
    private CustomerPolicyRepository policies;

    @Test
    void policyDetailShowsTotalsForTheOwner() throws Exception {
        mvc.perform(get("/api/policies/" + AARAV_POLICY).header("Authorization", bearer(AARAV)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerName").value("Aarav Sharma"))
                .andExpect(jsonPath("$.policyName").value("HealthDekho Secure Term"))
                .andExpect(jsonPath("$.termMonths").value(240))
                .andExpect(jsonPath("$.totalPremiumPayable").value(300000.00))
                .andExpect(jsonPath("$.latePenalties").value(0.00))
                .andExpect(jsonPath("$.totalPremiumPaid").value(1750.00))
                .andExpect(jsonPath("$.remainingPremium").value(298250.00))
                .andExpect(jsonPath("$.paidInstallments").value(1))
                .andExpect(jsonPath("$.totalInstallments").value(240))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.premiumPaymentStatus").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.fullyPaidOn").value(nullValue()));
    }

    @Test
    void seededFullyPaidPolicyStaysActive() throws Exception {
        // spec sample 5.5.3: policy 3002's only installment is paid and its status is ACTIVE
        mvc.perform(get("/api/policies/" + MEERA_HEALTH).header("Authorization", bearer(MEERA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.premiumPaymentStatus").value("FULLY_PAID"))
                .andExpect(jsonPath("$.fullyPaidOn").value("2026-11-20"))
                .andExpect(jsonPath("$.totalPremiumPayable").value(18000.00))
                .andExpect(jsonPath("$.totalPremiumPaid").value(18000.00))
                .andExpect(jsonPath("$.remainingPremium").value(0.00));
    }

    @Test
    void scheduleShowsOverdueInstallmentWithPenaltyPreview() throws Exception {
        mvc.perform(get("/api/policies/" + MEERA_SAVINGS + "/installments").header("Authorization", bearer(MEERA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(40)))
                .andExpect(jsonPath("$[1].installmentNumber").value(2))
                .andExpect(jsonPath("$[1].premiumPeriod").value("2026-09"))
                .andExpect(jsonPath("$[1].dueDate").value("2026-09-15"))
                .andExpect(jsonPath("$[1].gracePeriodEndDate").value("2026-10-15"))
                .andExpect(jsonPath("$[1].status").value("OVERDUE"))
                .andExpect(jsonPath("$[1].payable").value(true))
                .andExpect(jsonPath("$[1].penaltyIfPaidToday").value(750.00))
                .andExpect(jsonPath("$[2].dueDate").value("2026-12-15"))
                .andExpect(jsonPath("$[2].payable").value(true))
                .andExpect(jsonPath("$[2].penaltyIfPaidToday").value(0.00))
                .andExpect(jsonPath("$[3].payable").value(false));
    }

    @Test
    void payingOverdueInstallmentAddsPenaltyOnceAndMarksItPaid() throws Exception {
        String meera = bearer(MEERA);
        long overdue = installmentId(3003, 2);

        pay(meera, MEERA_SAVINGS, Map.of(overdue, "8250.00"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentDate").value("2026-11-25"))
                .andExpect(jsonPath("$.paymentMethod").value("UPI"))
                .andExpect(jsonPath("$.totalPaid").value(8250.00))
                .andExpect(jsonPath("$.totalPenalty").value(750.00))
                .andExpect(jsonPath("$.premiumPaymentStatus").value("PENDING"))
                .andExpect(jsonPath("$.remainingPremium").value(285000.00))
                .andExpect(jsonPath("$.lines", hasSize(1)))
                .andExpect(jsonPath("$.lines[0].transactionReference").value(matchesPattern("TXN-HD-\\d{6}")))
                .andExpect(jsonPath("$.lines[0].installmentNumber").value(2))
                .andExpect(jsonPath("$.lines[0].penaltyApplied").value(750.00))
                .andExpect(jsonPath("$.lines[0].installmentStatus").value("PAID"))
                .andExpect(jsonPath("$.lines[0].installmentOutstanding").value(0.00));

        assertThat(jdbc.queryForObject(
                "select penalty_amount from premium_payment where installment_id = ? and payment_date = ?",
                BigDecimal.class, overdue, TestClockConfig.TODAY)).isEqualByComparingTo("750.00");

        mvc.perform(get("/api/policies/" + MEERA_SAVINGS + "/payments").header("Authorization", meera))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].installmentNumber").value(2))
                .andExpect(jsonPath("$[0].premiumPeriod").value("2026-09"))
                .andExpect(jsonPath("$[0].amountPaid").value(8250.00))
                .andExpect(jsonPath("$[0].penaltyAmount").value(750.00))
                .andExpect(jsonPath("$[1].installmentNumber").value(1));
    }

    @Test
    void policyTotalsReconcileAfterALatePayment() throws Exception {
        String meera = bearer(MEERA);
        pay(meera, MEERA_SAVINGS, Map.of(installmentId(3003, 2), "8250.00"))
                .andExpect(status().isCreated());

        // payable is the contract total; the 750.00 penalty is its own line: payable + penalties - paid = remaining (AC-4A.2)
        mvc.perform(get("/api/policies/" + MEERA_SAVINGS).header("Authorization", meera))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPremiumPayable").value(300000.00))
                .andExpect(jsonPath("$.latePenalties").value(750.00))
                .andExpect(jsonPath("$.totalPremiumPaid").value(15750.00))
                .andExpect(jsonPath("$.remainingPremium").value(285000.00))
                .andExpect(jsonPath("$.premiumPaymentStatus").value("PENDING"));
    }

    @Test
    void amountAbovePenaltyInclusiveOutstandingIsRejected() throws Exception {
        pay(bearer(MEERA), MEERA_SAVINGS, Map.of(installmentId(3003, 2), "8250.01"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("AMOUNT_EXCEEDS_OUTSTANDING"));
    }

    @Test
    void partialPaymentLeavesInstallmentPartiallyPaid() throws Exception {
        pay(bearer(AARAV), AARAV_POLICY, Map.of(AARAV_PARTIAL, "250"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lines[0].amountPaid").value(250.00))
                .andExpect(jsonPath("$.lines[0].penaltyApplied").value(0.00))
                .andExpect(jsonPath("$.lines[0].installmentStatus").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.lines[0].installmentOutstanding").value(500.00))
                .andExpect(jsonPath("$.premiumPaymentStatus").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.remainingPremium").value(298000.00));
    }

    @Test
    void alreadyPaidInstallmentIsAConflict() throws Exception {
        pay(bearer(AARAV), AARAV_POLICY, Map.of(AARAV_PAID, "1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSTALLMENT_ALREADY_PAID"));
    }

    @Test
    void sameInstallmentTwiceInOneRequestIsRejected() throws Exception {
        String body = """
                {"paymentMethod":"UPI","items":[{"installmentId":%d,"amount":100},{"installmentId":%d,"amount":100}]}
                """.formatted(AARAV_PARTIAL, AARAV_PARTIAL);
        mvc.perform(post("/api/policies/" + AARAV_POLICY + "/payments").header("Authorization", bearer(AARAV))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DUPLICATE_INSTALLMENT_IN_REQUEST"));
    }

    @Test
    void installmentDueMoreThanAMonthAheadIsNotPayable() throws Exception {
        pay(bearer(AARAV), AARAV_POLICY, Map.of(AARAV_THIRD, "1250.00"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSTALLMENT_NOT_PAYABLE"));
    }

    @Test
    void installmentFromAnotherPolicyIsRejected() throws Exception {
        pay(bearer(AARAV), AARAV_POLICY, Map.of(MEERA_HEALTH_PAID, "100"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSTALLMENT_NOT_IN_POLICY"));
    }

    @Test
    void zeroAmountAndMissingMethodFailValidation() throws Exception {
        String body = """
                {"items":[{"installmentId":%d,"amount":0}]}
                """.formatted(AARAV_PARTIAL);
        mvc.perform(post("/api/policies/" + AARAV_POLICY + "/payments").header("Authorization", bearer(AARAV))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.paymentMethod").exists())
                .andExpect(jsonPath("$.errors['items[0].amount']").exists());
    }

    @Test
    void oneBadLineRejectsTheWholeRequest() throws Exception {
        String aarav = bearer(AARAV);
        pay(aarav, AARAV_POLICY, Map.of(AARAV_PARTIAL, "750.00", AARAV_THIRD, "1250.00"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSTALLMENT_NOT_PAYABLE"));

        mvc.perform(get("/api/policies/" + AARAV_POLICY + "/installments").header("Authorization", aarav))
                .andExpect(jsonPath("$[1].paidAmount").value(500.00))
                .andExpect(jsonPath("$[1].status").value("PARTIALLY_PAID"));
        assertThat(jdbc.queryForObject("select count(*) from premium_payment where installment_id = ?",
                Integer.class, AARAV_PARTIAL)).isEqualTo(1);
    }

    @Test
    void anotherCustomersPolicyIsNotFound() throws Exception {
        String aarav = bearer(AARAV);
        mvc.perform(get("/api/policies/" + MEERA_SAVINGS).header("Authorization", aarav))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/policies/" + MEERA_SAVINGS + "/installments").header("Authorization", aarav))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/policies/" + MEERA_SAVINGS + "/payments").header("Authorization", aarav))
                .andExpect(status().isNotFound());
        pay(aarav, MEERA_SAVINGS, Map.of(installmentId(3003, 2), "100"))
                .andExpect(status().isNotFound());
    }

    @Test
    void unknownPolicyIsNotFound() throws Exception {
        mvc.perform(get("/api/policies/HDP-2099-9999").header("Authorization", bearer(OPS)))
                .andExpect(status().isNotFound());
    }

    @Test
    void staffCanOpenAndPayAnyPolicy() throws Exception {
        String ops = bearer(OPS);
        mvc.perform(get("/api/policies/" + MEERA_SAVINGS).header("Authorization", ops))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerName").value("Meera Iyer"));
        pay(ops, AARAV_POLICY, Map.of(AARAV_PARTIAL, "750.00"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lines[0].installmentStatus").value("PAID"));
    }

    @Test
    void policyBecomesFullyPaidOnlyWhenItsLastInstallmentIsPaid() throws Exception {
        long installmentId = createSingleInstallmentPolicyForRohan("HDP-TEST-0001");
        String rohan = bearer(ROHAN);

        pay(rohan, "HDP-TEST-0001", Map.of(installmentId, "10000.00"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.premiumPaymentStatus").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.remainingPremium").value(8000.00));
        assertThat(fullyPaidOn("HDP-TEST-0001")).isNull();

        pay(rohan, "HDP-TEST-0001", Map.of(installmentId, "8000.00"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.premiumPaymentStatus").value("FULLY_PAID"))
                .andExpect(jsonPath("$.remainingPremium").value(0.00));

        mvc.perform(get("/api/policies/HDP-TEST-0001").header("Authorization", rohan))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.premiumPaymentStatus").value("FULLY_PAID"))
                .andExpect(jsonPath("$.fullyPaidOn").value("2026-11-25"))
                .andExpect(jsonPath("$.paidInstallments").value(1))
                .andExpect(jsonPath("$.totalPremiumPaid").value(18000.00));
        assertThat(fullyPaidOn("HDP-TEST-0001")).isEqualTo(TestClockConfig.TODAY);
    }

    @Test
    void payingAnInstallmentOtherThanTheLastDoesNotMarkThePolicyFullyPaid() throws Exception {
        pay(bearer(AARAV), AARAV_POLICY, Map.of(AARAV_PARTIAL, "750.00"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lines[0].installmentStatus").value("PAID"))
                .andExpect(jsonPath("$.premiumPaymentStatus").value("PENDING"));
        assertThat(fullyPaidOn(AARAV_POLICY)).isNull();
    }

    @Test
    void concurrentChangeToTheSameInstallmentIsAConflict() throws Exception {
        String aarav = bearer(AARAV);
        // hold a copy in the persistence context, then let "another transaction" move the version on
        installments.findById(AARAV_PARTIAL).orElseThrow();
        jdbc.update("update premium_installment set version = version + 1 where installment_id = ?", AARAV_PARTIAL);

        pay(aarav, AARAV_POLICY, Map.of(AARAV_PARTIAL, "100"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_UPDATE"));
    }

    @Test
    void unknownPaymentMethodIsAFieldError() throws Exception {
        mvc.perform(post("/api/policies/" + AARAV_POLICY + "/payments")
                        .header("Authorization", bearer(AARAV))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"BITCOIN\",\"items\":[{\"installmentId\":4002,\"amount\":100}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.paymentMethod").value("Choose one of: UPI, NET_BANKING, CARD, CASH, CHEQUE."));
    }

    @Test
    void overLongAmountExplainsTheLimit() throws Exception {
        pay(bearer(AARAV), AARAV_POLICY, Map.of(AARAV_PARTIAL, "1E+20"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['items[0].amount']")
                        .value("Enter an amount up to 12 digits with at most 2 decimal places"));
    }

    @Test
    void overpaymentMessageUsesRupeeFormatting() throws Exception {
        pay(bearer(MEERA), MEERA_SAVINGS, Map.of(installmentId(3003, 2), "8250.01"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(
                        "Installment 2 has ₹8,250.00 outstanding, including a late penalty of ₹750.00. ₹8,250.01 is more than that."));
    }

    private ResultActions pay(String token, String policyNumber, Map<Long, String> amounts) throws Exception {
        List<Map<String, Object>> items = amounts.entrySet().stream()
                .map(e -> Map.<String, Object>of("installmentId", e.getKey(), "amount", new BigDecimal(e.getValue())))
                .toList();
        return mvc.perform(post("/api/policies/" + policyNumber + "/payments")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("paymentMethod", "UPI", "items", items))));
    }

    private long installmentId(long policyId, int installmentNumber) {
        return jdbc.queryForObject(
                "select installment_id from premium_installment where policy_id = ? and installment_number = ?",
                Long.class, policyId, installmentNumber);
    }

    private LocalDate fullyPaidOn(String policyNumber) {
        return jdbc.queryForObject("select fully_paid_on from customer_policy where policy_number = ?",
                LocalDate.class, policyNumber);
    }

    /** One yearly installment of 18000.00 due today, so a single payment can settle the whole policy. */
    private long createSingleInstallmentPolicyForRohan(String policyNumber) {
        Customer rohan = customers.findByEmailIgnoreCase(ROHAN).orElseThrow();
        InsuranceProduct health = products.findByProductCode("HD-HEALTH-001").orElseThrow();
        LocalDate start = TestClockConfig.TODAY;
        CustomerPolicy policy = new CustomerPolicy(policyNumber, rohan, health, start,
                start.plusMonths(12).minusDays(1), health.getPremiumAmount());
        PremiumInstallment only = new PremiumInstallment(1, start, start.plusMonths(1), health.getPremiumAmount());
        policy.addInstallment(only);
        policies.saveAndFlush(policy);
        return only.getId();
    }
}
