package com.healthdekho.insurance.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.healthdekho.insurance.domain.Formats;
import com.healthdekho.insurance.domain.InstallmentRules;
import com.healthdekho.insurance.domain.Money;
import com.healthdekho.insurance.domain.PaymentMethod;
import com.healthdekho.insurance.domain.PaymentStatus;
import com.healthdekho.insurance.domain.PremiumPaymentStatus;
import com.healthdekho.insurance.dto.payment.PaymentReceiptResponse;
import com.healthdekho.insurance.dto.payment.PaymentRequest;
import com.healthdekho.insurance.entity.CustomerPolicy;
import com.healthdekho.insurance.entity.PremiumInstallment;
import com.healthdekho.insurance.entity.PremiumPayment;
import com.healthdekho.insurance.exception.BusinessRuleException;
import com.healthdekho.insurance.exception.ConflictException;
import com.healthdekho.insurance.repository.CustomerPolicyRepository;
import com.healthdekho.insurance.repository.PremiumInstallmentRepository;
import com.healthdekho.insurance.repository.PremiumPaymentRepository;
import com.healthdekho.insurance.security.AuthenticatedUser;

@Service
public class PremiumPaymentService {

    private final PolicyLookupService lookup;
    private final CustomerPolicyRepository policies;
    private final PremiumInstallmentRepository installments;
    private final PremiumPaymentRepository payments;
    private final Clock clock;

    public PremiumPaymentService(PolicyLookupService lookup, CustomerPolicyRepository policies,
                                 PremiumInstallmentRepository installments,
                                 PremiumPaymentRepository payments, Clock clock) {
        this.lookup = lookup;
        this.policies = policies;
        this.installments = installments;
        this.payments = payments;
        this.clock = clock;
    }

    /** All lines succeed or none do (AC-4.12). */
    @Transactional
    public PaymentReceiptResponse pay(String policyNumber, PaymentRequest request, AuthenticatedUser user) {
        LocalDate today = LocalDate.now(clock);
        CustomerPolicy policy = lookup.findAccessiblePolicy(policyNumber, user);
        // one payment per policy at a time: two requests paying the last two installments in parallel
        // would each see the other's installment unpaid and neither would mark the policy fully paid
        policies.lockForPayment(policy.getId());
        List<PremiumInstallment> schedule = lookup.currentInstallments(policy, today);
        Map<Long, PremiumInstallment> byId = schedule.stream()
                .collect(Collectors.toMap(PremiumInstallment::getId, Function.identity()));

        List<RequestedLine> requested = request.items().stream()
                .map(item -> new RequestedLine(installmentOf(byId, item.installmentId()), Money.normalize(item.amount())))
                .sorted(Comparator.comparing(line -> line.installment().getDueDate()))
                .toList();
        rejectRepeatedInstallments(requested);

        // check every line before changing anything, so a rejected request leaves no partial state behind
        List<CheckedLine> checked = requested.stream()
                .map(line -> check(line, today))
                .toList();
        List<PaymentReceiptResponse.Line> receiptLines = new ArrayList<>();
        for (CheckedLine line : checked) {
            receiptLines.add(apply(line, request.paymentMethod(), today));
        }
        PremiumPaymentStatus premiumPaymentStatus = PolicyLookupService.premiumPaymentStatus(schedule);
        if (premiumPaymentStatus == PremiumPaymentStatus.FULLY_PAID) {
            policy.markFullyPaid(today);
        }
        // flush inside the transaction so an optimistic-lock or constraint failure is translated here
        // and mapped to 409 by the global handler, instead of escaping from the commit
        installments.flush();

        return new PaymentReceiptResponse(
                policy.getPolicyNumber(),
                today,
                request.paymentMethod(),
                sum(receiptLines.stream().map(PaymentReceiptResponse.Line::amountPaid)),
                sum(receiptLines.stream().map(PaymentReceiptResponse.Line::penaltyApplied)),
                premiumPaymentStatus,
                PolicyLookupService.totals(policy, schedule).remainingPremium(),
                receiptLines);
    }

    private static CheckedLine check(RequestedLine line, LocalDate today) {
        PremiumInstallment installment = line.installment();
        int number = installment.getInstallmentNumber();
        if (installment.getPaymentStatus() == PaymentStatus.PAID) {
            throw new ConflictException("INSTALLMENT_ALREADY_PAID", "Installment %d is already paid.".formatted(number));
        }
        if (!InstallmentRules.isPayable(installment.getPaymentStatus(), installment.getDueDate(), today)) {
            throw new BusinessRuleException("INSTALLMENT_NOT_PAYABLE",
                    "Installment %d is due on %s. Installments can be paid at most one month before they are due."
                            .formatted(number, Formats.date(installment.getDueDate())));
        }

        BigDecimal penalty = InstallmentRules.penaltyIfPaidOn(today, installment.getGracePeriodEndDate(),
                installment.getPenaltyAmount(), installment.getOutstandingAmount());
        BigDecimal owed = installment.getOutstandingAmount().add(penalty);
        if (line.amount().compareTo(owed) > 0) {
            throw new BusinessRuleException("AMOUNT_EXCEEDS_OUTSTANDING",
                    "Installment %d has %s outstanding%s. %s is more than that.".formatted(number,
                            Formats.rupees(owed),
                            Money.isPositive(penalty) ? ", including a late penalty of " + Formats.rupees(penalty) : "",
                            Formats.rupees(line.amount())));
        }
        return new CheckedLine(installment, line.amount(), penalty);
    }

    private PaymentReceiptResponse.Line apply(CheckedLine line, PaymentMethod method, LocalDate today) {
        PremiumInstallment installment = line.installment();
        installment.applyPenalty(line.penalty());
        installment.recordPayment(line.amount());
        installment.setPaymentStatus(PolicyLookupService.resolveStatus(installment, today));
        String reference = "TXN-HD-%06d".formatted(payments.nextTransactionSequence());
        payments.save(new PremiumPayment(installment, reference, today, line.amount(), line.penalty(), method));

        return new PaymentReceiptResponse.Line(reference, installment.getInstallmentNumber(), line.amount(),
                line.penalty(), installment.getPaymentStatus(), installment.getOutstandingAmount());
    }

    private static PremiumInstallment installmentOf(Map<Long, PremiumInstallment> schedule, Long installmentId) {
        return Optional.ofNullable(schedule.get(installmentId))
                .orElseThrow(() -> new BusinessRuleException("INSTALLMENT_NOT_IN_POLICY",
                        "Installment %d does not belong to this policy.".formatted(installmentId)));
    }

    private static void rejectRepeatedInstallments(List<RequestedLine> lines) {
        String repeated = lines.stream()
                .collect(Collectors.groupingBy(line -> line.installment().getInstallmentNumber(),
                        Collectors.counting()))
                .entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .map(Map.Entry::getKey)
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(", "));
        if (!repeated.isEmpty()) {
            throw new BusinessRuleException("DUPLICATE_INSTALLMENT_IN_REQUEST",
                    "Each installment can appear only once in a payment. Repeated: installment %s.".formatted(repeated));
        }
    }

    private static BigDecimal sum(Stream<BigDecimal> amounts) {
        return Money.normalize(amounts.reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private record RequestedLine(PremiumInstallment installment, BigDecimal amount) {
    }

    private record CheckedLine(PremiumInstallment installment, BigDecimal amount, BigDecimal penalty) {
    }
}
