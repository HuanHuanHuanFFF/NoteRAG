package com.huanf.noterag.common.result;

import org.springframework.http.ResponseEntity;

/**
 * 统一响应构造工具，集中封装成功和失败返回。
 */
public final class Responses {

    private Responses() {
    }

    /**
     * 返回默认成功响应。
     */
    public static ResponseEntity<Body<Void>> ok() {
        return ok(CodeStatus.SUCCESS.getMsg());
    }

    /**
     * 返回自定义消息的成功响应。
     */
    public static ResponseEntity<Body<Void>> ok(String msg) {
        return ResponseEntity.status(CodeStatus.SUCCESS.getHttpStatus())
                .body(new Body<>(CodeStatus.SUCCESS.getCode(), msg, null));
    }

    /**
     * 返回携带数据的成功响应。
     */
    public static <T> ResponseEntity<Body<T>> ok(T data) {
        return ok(CodeStatus.SUCCESS.getMsg(), data);
    }

    /**
     * 返回自定义消息和数据的成功响应。
     */
    public static <T> ResponseEntity<Body<T>> ok(String msg, T data) {
        return ResponseEntity.status(CodeStatus.SUCCESS.getHttpStatus())
                .body(new Body<>(CodeStatus.SUCCESS.getCode(), msg, data));
    }

    /**
     * 按状态码返回失败响应。
     */
    public static ResponseEntity<Body<Void>> fail(CodeStatus codeStatus) {
        return fail(codeStatus, codeStatus.getMsg());
    }

    /**
     * 按状态码和消息返回失败响应，data 固定为 null。
     */
    public static ResponseEntity<Body<Void>> fail(CodeStatus codeStatus, String msg) {
        return ResponseEntity.status(codeStatus.getHttpStatus())
                .body(new Body<>(codeStatus.getCode(), msg, null));
    }

    /**
     * 统一响应体结构。
     */
    public record Body<T>(int code, String msg, T data) {
    }
}
