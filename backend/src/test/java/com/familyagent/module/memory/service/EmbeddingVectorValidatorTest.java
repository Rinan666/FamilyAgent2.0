package com.familyagent.module.memory.service;

import com.familyagent.infra.ai.dto.EmbeddingResponse;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbeddingVectorValidatorTest {

    @Test
    void validateShouldAcceptOnlyCompleteFiniteNonZeroVector() {
        EmbeddingVectorValidator.Result result = EmbeddingVectorValidator.validate(
                response(Collections.nCopies(4, 0.1), 4),
                4);

        assertTrue(result.valid());
        assertEquals(4, result.values().size());
    }

    @Test
    void validateShouldRejectDimensionAndReportedDimensionMismatch() {
        assertEquals(
                "embedding dimension mismatch: 2",
                EmbeddingVectorValidator.validate(response(List.of(0.1, 0.2), 2), 4).error());
        assertEquals(
                "embedding reported dimension mismatch: 2",
                EmbeddingVectorValidator.validate(response(Collections.nCopies(4, 0.1), 2), 4).error());
    }

    @Test
    void validateShouldRejectNonFiniteAndZeroVectors() {
        List<Double> nonFinite = new ArrayList<>(Collections.nCopies(4, 0.1));
        nonFinite.set(0, Double.NaN);

        EmbeddingVectorValidator.Result nonFiniteResult = EmbeddingVectorValidator.validate(
                response(nonFinite, 4),
                4);
        EmbeddingVectorValidator.Result zeroResult = EmbeddingVectorValidator.validate(
                response(Collections.nCopies(4, 0.0), 4),
                4);

        assertFalse(nonFiniteResult.valid());
        assertEquals("embedding contains non-finite values", nonFiniteResult.error());
        assertFalse(zeroResult.valid());
        assertEquals("embedding vector is zero", zeroResult.error());
    }

    private static EmbeddingResponse response(List<Double> values, int dimensions) {
        EmbeddingResponse response = new EmbeddingResponse();
        response.setSuccess(true);
        response.setEmbedding(values);
        response.setDimensions(dimensions);
        return response;
    }
}
