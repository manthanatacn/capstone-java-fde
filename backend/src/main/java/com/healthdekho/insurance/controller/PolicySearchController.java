package com.healthdekho.insurance.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.healthdekho.insurance.dto.common.PageResponse;
import com.healthdekho.insurance.dto.search.PolicySearchCriteria;
import com.healthdekho.insurance.dto.search.PolicySearchResult;
import com.healthdekho.insurance.security.AuthenticatedUser;
import com.healthdekho.insurance.service.PolicySearchService;

@RestController
public class PolicySearchController {

    private final PolicySearchService searchService;

    public PolicySearchController(PolicySearchService searchService) {
        this.searchService = searchService;
    }

    // a literal path, so Spring prefers it over /api/policies/{policyNumber}
    @GetMapping("/api/policies/search")
    public PageResponse<PolicySearchResult> search(PolicySearchCriteria criteria,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "10") int size,
                                                   AuthenticatedUser user) {
        return searchService.search(criteria, page, size, user);
    }
}
