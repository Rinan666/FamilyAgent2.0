package com.familyagent.module.memory.service;

import com.familyagent.infra.ai.dto.EmbeddingResponse;

import java.util.List;

public final class EmbeddingVectorValidator {

    private EmbeddingVectorValidator() {
    }

    public static Result validate(EmbeddingResponse response, int expectedDimensions) {
        if (response == null || !response.isSuccess()) {
            return Result.failed(error(response, "embedding failed"));
        }
        if (response.isDegraded()) {
            return Result.failed(error(response, "embedding degraded"));
        }

        List<Double> values = response.getEmbedding();
        if (values == null || values.isEmpty()) {
            return Result.failed("embedding response is empty");
        }
        if (values.size() != expectedDimensions) {
            return Result.failed("embedding dimension mismatch: " + values.size());
        }
        if (response.getDimensions() != null && response.getDimensions() != expectedDimensions) {
            return Result.failed("embedding reported dimension mismatch: " + response.getDimensions());
        }

        boolean hasNonZeroValue = false;
        for (Double value : values) {
            if (value == null || !Double.isFinite(value)) {
                return Result.failed("embedding contains non-finite values");
            }
            if (value != 0.0d) {
                hasNonZeroValue = true;
            }
        }
        return hasNonZeroValue
                ? Result.valid(values)
                : Result.failed("embedding vector is zero");
    }

    private static String error(EmbeddingResponse response, String fallback) {
        if (response == null || response.getError() == null || response.getError().isBlank()) {
            return fallback;
        }
        return response.getError().trim();
    }

    public record Result(List<Double> values, String error) {

        public Result {
            values = values == null ? List.of() : List.copyOf(values);
            error = error == null || error.isBlank() ? null : error.trim();
        }

        public static Result valid(List<Double> values) {
            return new Result(values, null);
        }

        public static Result failed(String error) {
            return new Result(List.of(), error);
        }

        public boolean valid() {
            return error == null;
        }
    }
}
