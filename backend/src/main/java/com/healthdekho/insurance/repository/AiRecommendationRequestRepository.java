package com.healthdekho.insurance.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.healthdekho.insurance.entity.AiRecommendationRequest;

public interface AiRecommendationRequestRepository extends JpaRepository<AiRecommendationRequest, Long> {
}
