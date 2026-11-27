package com.healthdekho.insurance.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.healthdekho.insurance.dto.recommendation.RecommendationRequest;
import com.healthdekho.insurance.dto.recommendation.RecommendationResponse;
import com.healthdekho.insurance.security.AuthenticatedUser;
import com.healthdekho.insurance.service.RecommendationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @PostMapping
    public RecommendationResponse recommend(@Valid @RequestBody RecommendationRequest request,
                                            AuthenticatedUser user) {
        return recommendationService.recommend(user, request);
    }
}
