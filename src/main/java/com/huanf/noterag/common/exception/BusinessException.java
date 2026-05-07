package com.huanf.noterag.common.exception;

import com.huanf.noterag.common.result.CodeStatus;

/**
 * 基础业务异常，携带统一业务状态码。
 */
public class BusinessException extends RuntimeException {

    private final CodeStatus codeStatus;

    /**
     * 使用状态码默认消息创建业务异常。
     */
    public BusinessException(CodeStatus codeStatus) {
        super(codeStatus.getMsg());
        this.codeStatus = codeStatus;
    }

    /**
     * 使用自定义消息创建业务异常。
     */
    public BusinessException(CodeStatus codeStatus, String message) {
        super(message);
        this.codeStatus = codeStatus;
    }

    public CodeStatus getCodeStatus() {
        return codeStatus;
    }
}
