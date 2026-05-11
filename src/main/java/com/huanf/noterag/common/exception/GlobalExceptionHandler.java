package com.huanf.noterag.common.exception;

import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.huanf.noterag.common.result.ApiBody;
import com.huanf.noterag.common.result.CodeStatus;

import jakarta.validation.ConstraintViolationException;

/**
 * 统一处理接口异常并输出标准响应体。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiBody<Void>> handleBusinessException(BusinessException exception) {
        CodeStatus codeStatus = exception.getCodeStatus();
        return ResponseEntity.status(codeStatus.getHttpStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiBody.fail(codeStatus, exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiBody<Void>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception) {
        return invalidRequest(firstFieldErrorMessage(exception));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiBody<Void>> handleBindException(BindException exception) {
        return invalidRequest(firstFieldErrorMessage(exception));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiBody<Void>> handleConstraintViolationException(
            ConstraintViolationException exception) {
        String message = exception.getConstraintViolations()
                .stream()
                .map(violation -> violation.getMessage())
                .collect(Collectors.joining("; "));
        return invalidRequest(message.isBlank() ? CodeStatus.INVALID_REQUEST.getMessage() : message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiBody<Void>> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException exception) {
        return invalidRequest("请求体格式错误");
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiBody<Void>> handleNotFoundException(Exception exception) {
        return fail(CodeStatus.NOT_FOUND);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiBody<Void>> handleHttpRequestMethodNotSupportedException(
            HttpRequestMethodNotSupportedException exception) {
        return fail(CodeStatus.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiBody<Void>> handleHttpMediaTypeNotSupportedException(
            HttpMediaTypeNotSupportedException exception) {
        return fail(CodeStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiBody<Void>> handleHttpMediaTypeNotAcceptableException(
            HttpMediaTypeNotAcceptableException exception) {
        return fail(CodeStatus.NOT_ACCEPTABLE);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiBody<Void>> handleIllegalArgumentException(IllegalArgumentException exception) {
        return invalidRequest(exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiBody<Void>> handleException(Exception exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiBody.fail(CodeStatus.INTERNAL_ERROR, CodeStatus.INTERNAL_ERROR.getMessage()));
    }

    private ResponseEntity<ApiBody<Void>> invalidRequest(String message) {
        return ResponseEntity.status(CodeStatus.INVALID_REQUEST.getHttpStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiBody.fail(CodeStatus.INVALID_REQUEST, message));
    }

    private ResponseEntity<ApiBody<Void>> fail(CodeStatus codeStatus) {
        return ResponseEntity.status(codeStatus.getHttpStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiBody.fail(codeStatus, codeStatus.getMessage()));
    }

    private String firstFieldErrorMessage(BindException exception) {
        if (exception.getBindingResult().getFieldError() != null) {
            return exception.getBindingResult().getFieldError().getDefaultMessage();
        }
        if (exception.getBindingResult().getGlobalError() != null) {
            return exception.getBindingResult().getGlobalError().getDefaultMessage();
        }
        return CodeStatus.INVALID_REQUEST.getMessage();
    }
}
