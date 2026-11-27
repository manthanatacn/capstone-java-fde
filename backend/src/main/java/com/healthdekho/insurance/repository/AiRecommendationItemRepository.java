package com.healthdekho.insurance.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.healthdekho.insurance.entity.AiRecommendationItem;

public interface AiRecommendationItemRepository extends JpaRepository<AiRecommendationItem, Long> {
}
