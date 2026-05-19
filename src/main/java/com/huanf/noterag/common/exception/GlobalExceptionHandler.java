package com.huanf.noterag.common.exception;

import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiBody<Void>> handleBusinessException(BusinessException exception) {
        CodeStatus codeStatus = exception.getCodeStatus();
        if (codeStatus.getHttpStatus().is5xxServerError()) {
            log.error("业务异常 code={}, status={}, message={}",
                    codeStatus.getCode(), codeStatus.getHttpStatus().value(), exception.getMessage());
        } else {
            log.warn("业务异常 code={}, status={}, message={}",
                    codeStatus.getCode(), codeStatus.getHttpStatus().value(), exception.getMessage());
        }
        return ResponseEntity.status(codeStatus.getHttpStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiBody.fail(codeStatus, exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiBody<Void>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception) {
        String message = firstFieldErrorMessage(exception);
        log.debug("参数校验失败, message={}", message);
        return invalidRequest(message);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiBody<Void>> handleBindException(BindException exception) {
        String message = firstFieldErrorMessage(exception);
        log.debug("参数绑定失败, message={}", message);
        return invalidRequest(message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiBody<Void>> handleConstraintViolationException(
            ConstraintViolationException exception) {
        String message = exception.getConstraintViolations()
                .stream()
                .map(violation -> violation.getMessage())
                .collect(Collectors.joining("; "));
        String resolved = message.isBlank() ? CodeStatus.INVALID_REQUEST.getMessage() : message;
        log.debug("约束校验失败, message={}", resolved);
        return invalidRequest(resolved);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiBody<Void>> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException exception) {
        log.debug("请求体解析失败, message={}", exception.getMessage());
        return invalidRequest("请求体格式错误");
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiBody<Void>> handleNotFoundException(Exception exception) {
        log.debug("路由不存在, message={}", exception.getMessage());
        return fail(CodeStatus.NOT_FOUND);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiBody<Void>> handleHttpRequestMethodNotSupportedException(
            HttpRequestMethodNotSupportedException exception) {
        log.debug("请求方法不支持, message={}", exception.getMessage());
        return fail(CodeStatus.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiBody<Void>> handleHttpMediaTypeNotSupportedException(
            HttpMediaTypeNotSupportedException exception) {
        log.debug("请求媒体类型不支持, message={}", exception.getMessage());
        return fail(CodeStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiBody<Void>> handleHttpMediaTypeNotAcceptableException(
            HttpMediaTypeNotAcceptableException exception) {
        log.debug("响应媒体类型不支持, message={}", exception.getMessage());
        return fail(CodeStatus.NOT_ACCEPTABLE);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiBody<Void>> handleIllegalArgumentException(IllegalArgumentException exception) {
        log.warn("非法参数, message={}", exception.getMessage());
        return invalidRequest(exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiBody<Void>> handleException(Exception exception) {
        log.error("未处理异常 type={}, message={}",
                exception.getClass().getName(), exception.getMessage(), exception);
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
