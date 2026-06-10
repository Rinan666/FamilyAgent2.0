package com.familyagent.common.exception;

import com.familyagent.common.response.ErrorCode;
import com.familyagent.common.response.Result;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleDataIntegrityException_shouldReturnUsernameExistsForUserConstraint() {
        Result<?> result = handler.handleDataIntegrityException(
                new DataIntegrityViolationException("duplicate key value violates unique constraint users_username_key")
        );

        assertEquals(ErrorCode.USERNAME_EXISTS.getCode(), result.getCode());
        assertEquals(ErrorCode.USERNAME_EXISTS.getMessage(), result.getMessage());
    }

    @Test
    void handleDataIntegrityException_shouldReturnPersistFailureForOtherConstraint() {
        Result<?> result = handler.handleDataIntegrityException(
                new DataIntegrityViolationException("insert or update on table invite_codes violates check constraint")
        );

        assertEquals(ErrorCode.DATA_PERSIST_FAILED.getCode(), result.getCode());
        assertEquals(ErrorCode.DATA_PERSIST_FAILED.getMessage(), result.getMessage());
    }

    @Test
    void handleDataAccessException_shouldReturnDatabaseAccessError() {
        Result<?> result = handler.handleDataAccessException(
                new DataAccessResourceFailureException("db offline")
        );

        assertEquals(ErrorCode.DATABASE_ACCESS_ERROR.getCode(), result.getCode());
        assertEquals(ErrorCode.DATABASE_ACCESS_ERROR.getMessage(), result.getMessage());
    }

    @Test
    void handleUnknownException_shouldReturnInternalError() {
        Result<?> result = handler.handleUnknownException(new RuntimeException("boom"));

        assertEquals(ErrorCode.INTERNAL_ERROR.getCode(), result.getCode());
        assertEquals(ErrorCode.INTERNAL_ERROR.getMessage(), result.getMessage());
    }
}
