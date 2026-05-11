package com.huanf.noterag.common.result;

/**
 * 统一 API 响应体。
 */
public record ApiBody<T>(int code, String message, T data) {

    /**
     * 构造默认成功响应体。
     */
    public static <T> ApiBody<T> success(T data) {
        return new ApiBody<>(CodeStatus.SUCCESS.getCode(), CodeStatus.SUCCESS.getMessage(), data);
    }

    /**
     * 按状态码和消息构造失败响应体。
     */
    public static ApiBody<Void> fail(CodeStatus codeStatus, String message) {
        return new ApiBody<>(codeStatus.getCode(), message, null);
    }
}
