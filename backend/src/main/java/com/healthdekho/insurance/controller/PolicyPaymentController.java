package com.healthdekho.insurance.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.healthdekho.insurance.dto.payment.InstallmentResponse;
import com.healthdekho.insurance.dto.payment.PaymentHistoryResponse;
import com.healthdekho.insurance.dto.payment.PaymentReceiptResponse;
import com.healthdekho.insurance.dto.payment.PaymentRequest;
import com.healthdekho.insurance.dto.payment.PolicyDetailResponse;
import com.healthdekho.insurance.security.AuthenticatedUser;
import com.healthdekho.insurance.service.PolicyLookupService;
import com.healthdekho.insurance.service.PremiumPaymentService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/policies/{policyNumber}")
public class PolicyPaymentController {

    private final PolicyLookupService lookupService;
    private final PremiumPaymentService paymentService;

    public PolicyPaymentController(PolicyLookupService lookupService, PremiumPaymentService paymentService) {
        this.lookupService = lookupService;
        this.paymentService = paymentService;
    }

    @GetMapping
    public PolicyDetailResponse policy(@PathVariable String policyNumber, AuthenticatedUser user) {
        return lookupService.getPolicy(policyNumber, user);
    }

    @GetMapping("/installments")
    public List<InstallmentResponse> installments(@PathVariable String policyNumber, AuthenticatedUser user) {
        return lookupService.getInstallments(policyNumber, user);
    }

    @PostMapping("/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentReceiptResponse pay(@PathVariable String policyNumber, @Valid @RequestBody PaymentRequest request,
                                      AuthenticatedUser user) {
        return paymentService.pay(policyNumber, request, user);
    }

    @GetMapping("/payments")
    public List<PaymentHistoryResponse> payments(@PathVariable String policyNumber, AuthenticatedUser user) {
        return lookupService.getPaymentHistory(policyNumber, user);
    }
}
