package com.healthdekho.insurance.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.healthdekho.insurance.domain.InstallmentRules;
import com.healthdekho.insurance.domain.PaymentStatus;
import com.healthdekho.insurance.domain.PremiumPaymentStatus;
import com.healthdekho.insurance.domain.PremiumTotals;
import com.healthdekho.insurance.dto.payment.InstallmentResponse;
import com.healthdekho.insurance.dto.payment.PaymentHistoryResponse;
import com.healthdekho.insurance.dto.payment.PolicyDetailResponse;
import com.healthdekho.insurance.entity.CustomerPolicy;
import com.healthdekho.insurance.entity.PremiumInstallment;
import com.healthdekho.insurance.exception.ResourceNotFoundException;
import com.healthdekho.insurance.repository.CustomerPolicyRepository;
import com.healthdekho.insurance.repository.PremiumInstallmentRepository;
import com.healthdekho.insurance.repository.PremiumPaymentRepository;
import com.healthdekho.insurance.security.AuthenticatedUser;

@Service
public class PolicyLookupService {

    private final CustomerPolicyRepository policies;
    private final PremiumInstallmentRepository installments;
    private final PremiumPaymentRepository payments;
    private final Clock clock;

    public PolicyLookupService(CustomerPolicyRepository policies, PremiumInstallmentRepository installments,
                               PremiumPaymentRepository payments, Clock clock) {
        this.policies = policies;
        this.installments = installments;
        this.payments = payments;
        this.clock = clock;
    }

    // not read-only: refreshing statuses for today writes any that changed (ADR-5)
    @Transactional
    public PolicyDetailResponse getPolicy(String policyNumber, AuthenticatedUser user) {
        CustomerPolicy policy = findAccessiblePolicy(policyNumber, user);
        LocalDate today = LocalDate.now(clock);
        List<PremiumInstallment> schedule = currentInstallments(policy, today);
        PremiumTotals totals = totals(policy, schedule);
        int paidCount = (int) schedule.stream()
                .filter(i -> i.getPaymentStatus() == PaymentStatus.PAID)
                .count();
        return new PolicyDetailResponse(
                policy.getPolicyNumber(),
                policy.getCustomer().getFullName(),
                policy.getCustomer().getDateOfBirth(),
                policy.getProduct().getProductCode(),
                policy.getProduct().getPolicyName(),
                policy.getProduct().getPolicyType(),
                policy.getTermMonths(),
                policy.getStartDate(),
                policy.getMaturityDate(),
                policy.getPremiumFrequency(),
                policy.getPremiumAmount(),
                policy.getSumInsured(),
                totals.totalPremiumPayable(),
                totals.latePenalties(),
                totals.totalPremiumPaid(),
                totals.remainingPremium(),
                paidCount,
                schedule.size(),
                policy.effectiveStatus(today),
                premiumPaymentStatus(schedule),
                policy.getFullyPaidOn());
    }

    @Transactional
    public List<InstallmentResponse> getInstallments(String policyNumber, AuthenticatedUser user) {
        CustomerPolicy policy = findAccessiblePolicy(policyNumber, user);
        LocalDate today = LocalDate.now(clock);
        return currentInstallments(policy, today).stream()
                .map(i -> toResponse(i, today))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentHistoryResponse> getPaymentHistory(String policyNumber, AuthenticatedUser user) {
        CustomerPolicy policy = findAccessiblePolicy(policyNumber, user);
        return payments.findHistoryForPolicy(policy.getId()).stream()
                .map(p -> new PaymentHistoryResponse(
                        p.getTransactionReference(),
                        p.getInstallment().getInstallmentNumber(),
                        p.getInstallment().getPremiumPeriod(),
                        p.getPaymentDate(),
                        p.getAmountPaid(),
                        p.getPenaltyAmount(),
                        p.getPaymentMethod()))
                .toList();
    }

    /** Another customer's policy is reported as not found, so policy numbers cannot be probed (ADR-4). */
    public CustomerPolicy findAccessiblePolicy(String policyNumber, AuthenticatedUser user) {
        return policies.findByPolicyNumber(policyNumber.trim().toUpperCase(Locale.ROOT))
                .filter(p -> user.isStaff() || p.getCustomer().getId().equals(user.id()))
                .orElseThrow(() -> new ResourceNotFoundException("Policy " + policyNumber + " was not found."));
    }

    /** The schedule in installment order, with statuses recomputed for {@code today}. Changed rows are saved. */
    public List<PremiumInstallment> currentInstallments(CustomerPolicy policy, LocalDate today) {
        List<PremiumInstallment> schedule = installments.findByPolicyIdOrderByInstallmentNumberAsc(policy.getId());
        boolean changed = false;
        for (PremiumInstallment installment : schedule) {
            PaymentStatus status = resolveStatus(installment, today);
            if (status != installment.getPaymentStatus()) {
                installment.setPaymentStatus(status);
                changed = true;
            }
        }
        if (changed) {
            // flush here so a version clash surfaces as a translated 409, not as a failure at commit
            installments.flush();
        }
        return schedule;
    }

    static PremiumTotals totals(CustomerPolicy policy, Collection<PremiumInstallment> schedule) {
        return PremiumTotals.of(policy.getTotalPremiumPayable(),
                sum(schedule, PremiumInstallment::getPenaltyAmount),
                sum(schedule, PremiumInstallment::getPaidAmount));
    }

    /** {@code schedule} must come from {@link #currentInstallments}, so its statuses are today's. */
    static PremiumPaymentStatus premiumPaymentStatus(Collection<PremiumInstallment> schedule) {
        return PremiumPaymentStatus.of(schedule.stream().map(PremiumInstallment::getPaymentStatus).toList());
    }

    private static BigDecimal sum(Collection<PremiumInstallment> schedule,
                                  Function<PremiumInstallment, BigDecimal> amount) {
        return schedule.stream().map(amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    static PaymentStatus resolveStatus(PremiumInstallment installment, LocalDate today) {
        return InstallmentRules.resolveStatus(installment.getOutstandingAmount(), installment.getPaidAmount(),
                installment.getGracePeriodEndDate(), today);
    }

    private static InstallmentResponse toResponse(PremiumInstallment installment, LocalDate today) {
        return new InstallmentResponse(
                installment.getId(),
                installment.getInstallmentNumber(),
                installment.getPremiumPeriod(),
                installment.getDueDate(),
                installment.getGracePeriodEndDate(),
                installment.getScheduledAmount(),
                installment.getPenaltyAmount(),
                installment.getPaidAmount(),
                installment.getOutstandingAmount(),
                installment.getPaymentStatus(),
                InstallmentRules.isPayable(installment.getPaymentStatus(), installment.getDueDate(), today),
                InstallmentRules.penaltyIfPaidOn(today, installment.getGracePeriodEndDate(),
                        installment.getPenaltyAmount(), installment.getOutstandingAmount()));
    }
}
