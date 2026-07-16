package com.familyagent.infra.ai.dto;

public interface DraftGenerationResponse<T> {

    boolean isSuccess();

    T getData();

    String getErrorCode();
}
