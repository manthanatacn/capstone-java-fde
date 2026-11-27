package com.healthdekho.insurance.dto.recommendation;

import java.time.OffsetDateTime;
import java.util.List;

import com.healthdekho.insurance.domain.GenerationSource;

/** notice is null on a normal model answer; set when the rules were used or nothing was eligible. */
public record RecommendationResponse(Long requestId, int calculatedAge, GenerationSource source, String modelName,
                                     OffsetDateTime generatedAt, String notice,
                                     List<RecommendedProductResponse> items) {
}
