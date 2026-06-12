package com.familyagent.common.exception;

import com.familyagent.common.response.ErrorCode;
import com.familyagent.common.response.Result;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

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
    void handleMaxUploadSizeExceededException_shouldReturnReadableUploadLimitMessage() {
        Result<?> result = handler.handleMaxUploadSizeExceededException(
                new MaxUploadSizeExceededException(40L * 1024 * 1024)
        );

        assertEquals(ErrorCode.BAD_REQUEST.getCode(), result.getCode());
        assertEquals("The selected photos exceed the upload limit of 10 MB per image and 40 MB total.", result.getMessage());
    }

    @Test
    void handleMultipartException_shouldReturnReadableUploadFailureMessage() {
        Result<?> result = handler.handleMultipartException(
                new MultipartException("boundary missing")
        );

        assertEquals(ErrorCode.BAD_REQUEST.getCode(), result.getCode());
        assertEquals("Photo upload request could not be processed. Please reselect the files and try again.", result.getMessage());
    }

    @Test
    void handleUnknownException_shouldReturnInternalError() {
        Result<?> result = handler.handleUnknownException(new RuntimeException("boom"));

        assertEquals(ErrorCode.INTERNAL_ERROR.getCode(), result.getCode());
        assertEquals(ErrorCode.INTERNAL_ERROR.getMessage(), result.getMessage());
    }
}
