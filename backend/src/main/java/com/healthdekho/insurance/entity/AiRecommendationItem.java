package com.healthdekho.insurance.entity;

import com.healthdekho.insurance.domain.Suitability;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "ai_recommendation_item")
public class AiRecommendationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recommendation_item_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recommendation_request_id", nullable = false)
    private AiRecommendationRequest request;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private InsuranceProduct product;

    @Column(name = "rank_order", nullable = false)
    private int rankOrder;

    @Column(name = "recommendation_reason", nullable = false, length = 1000)
    private String recommendationReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Suitability suitability;

    @Column(name = "selected_for_registration", nullable = false)
    private boolean selectedForRegistration;

    protected AiRecommendationItem() {
    }

    public AiRecommendationItem(InsuranceProduct product, int rankOrder, String recommendationReason,
                                Suitability suitability) {
        this.product = product;
        this.rankOrder = rankOrder;
        this.recommendationReason = recommendationReason;
        this.suitability = suitability;
    }

    void attachTo(AiRecommendationRequest request) {
        this.request = request;
    }

    public void markSelectedForRegistration() {
        this.selectedForRegistration = true;
    }

    public Long getId() { return id; }
    public AiRecommendationRequest getRequest() { return request; }
    public InsuranceProduct getProduct() { return product; }
    public int getRankOrder() { return rankOrder; }
    public String getRecommendationReason() { return recommendationReason; }
    public Suitability getSuitability() { return suitability; }
    public boolean isSelectedForRegistration() { return selectedForRegistration; }
}
