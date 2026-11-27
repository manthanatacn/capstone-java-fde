package com.healthdekho.insurance.entity;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import com.healthdekho.insurance.domain.GenerationSource;
import com.healthdekho.insurance.domain.PolicyType;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/** Audit record of one recommendation interaction: inputs, model metadata and the prompt sent. */
@Entity
@Table(name = "ai_recommendation_request")
public class AiRecommendationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recommendation_request_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "insurance_goals", length = 500)
    private String insuranceGoals;

    /** Free text as in the spec sample, e.g. "20 years". */
    @Column(name = "preferred_term", length = 20)
    private String preferredTerm;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_policy_type", length = 20)
    private PolicyType preferredPolicyType;

    @Column(name = "calculated_age", nullable = false)
    private int calculatedAge;

    @Enumerated(EnumType.STRING)
    @Column(name = "generation_source", nullable = false, length = 20)
    private GenerationSource generationSource;

    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    @Column(name = "model_version", length = 100)
    private String modelVersion;

    @Column(name = "prompt_version", nullable = false, length = 20)
    private String promptVersion;

    @Column(name = "prompt_text", columnDefinition = "text")
    private String promptText;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("rankOrder ASC")
    private List<AiRecommendationItem> items = new ArrayList<>();

    protected AiRecommendationRequest() {
    }

    public AiRecommendationRequest(Customer customer, String insuranceGoals, String preferredTerm,
                                   PolicyType preferredPolicyType, int calculatedAge, OffsetDateTime requestedAt) {
        this.customer = customer;
        this.insuranceGoals = insuranceGoals;
        this.preferredTerm = preferredTerm;
        this.preferredPolicyType = preferredPolicyType;
        this.calculatedAge = calculatedAge;
        this.requestedAt = requestedAt;
    }

    public void recordGeneration(GenerationSource source, String modelName, String modelVersion,
                                 String promptVersion, String promptText) {
        this.generationSource = source;
        this.modelName = modelName;
        this.modelVersion = modelVersion;
        this.promptVersion = promptVersion;
        this.promptText = promptText;
    }

    public void addItem(AiRecommendationItem item) {
        items.add(item);
        item.attachTo(this);
    }

    public Long getId() { return id; }
    public Customer getCustomer() { return customer; }
    public String getInsuranceGoals() { return insuranceGoals; }
    public String getPreferredTerm() { return preferredTerm; }
    public PolicyType getPreferredPolicyType() { return preferredPolicyType; }
    public int getCalculatedAge() { return calculatedAge; }
    public GenerationSource getGenerationSource() { return generationSource; }
    public String getModelName() { return modelName; }
    public String getModelVersion() { return modelVersion; }
    public String getPromptVersion() { return promptVersion; }
    public String getPromptText() { return promptText; }
    public OffsetDateTime getRequestedAt() { return requestedAt; }
    public List<AiRecommendationItem> getItems() { return items; }
}
