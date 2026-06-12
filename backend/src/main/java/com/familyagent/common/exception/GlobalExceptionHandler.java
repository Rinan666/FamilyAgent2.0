package com.familyagent.common.exception;

import cn.dev33.satoken.exception.NotLoginException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.common.response.Result;
import com.familyagent.common.exception.UsernameConflictDetector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Global exception handler.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<?> handleBusinessException(BusinessException e) {
        log.warn("Business exception: code={}, message={}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("Request validation failed: {}", message);
        return Result.error(ErrorCode.PARAM_VALID_FAILED, message);
    }

    @ExceptionHandler(NotLoginException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<?> handleNotLoginException(NotLoginException e) {
        log.warn("Authentication state exception: {}", e.getMessage());
        return Result.error(ErrorCode.UNAUTHORIZED);
    }

    @ExceptionHandler({DuplicateKeyException.class, DataIntegrityViolationException.class})
    @ResponseStatus(HttpStatus.OK)
    public Result<?> handleDataIntegrityException(DataAccessException e) {
        if (isUsernameConflict(e)) {
            log.warn("User registration conflict detected", e);
            return Result.error(ErrorCode.USERNAME_EXISTS);
        }
        log.error("Data integrity violation", e);
        return Result.error(ErrorCode.DATA_PERSIST_FAILED);
    }

    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<?> handleDataAccessException(DataAccessException e) {
        log.error("Database access exception", e);
        return Result.error(ErrorCode.DATABASE_ACCESS_ERROR);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<?> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        log.warn("Upload exceeds configured size limit", e);
        return Result.error(ErrorCode.BAD_REQUEST, "The selected photos exceed the upload limit of 10 MB per image and 40 MB total.");
    }

    @ExceptionHandler(MultipartException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<?> handleMultipartException(MultipartException e) {
        log.warn("Multipart request failed: {}", e.getMessage());
        return Result.error(ErrorCode.BAD_REQUEST, "Photo upload request could not be processed. Please reselect the files and try again.");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<?> handleUnknownException(Exception e) {
        log.error("Unhandled exception", e);
        return Result.error(ErrorCode.INTERNAL_ERROR);
    }

    private boolean isUsernameConflict(Throwable error) {
        return UsernameConflictDetector.isUsernameConflict(error);
    }
}
