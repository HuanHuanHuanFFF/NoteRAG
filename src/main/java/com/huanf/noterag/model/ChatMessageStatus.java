package com.huanf.noterag.model;

/**
 * Chat 消息状态。
 */
public enum ChatMessageStatus {

    /**
     * 消息已创建，尚未完成最终内容写入。
     */
    PENDING,

    /**
     * 消息已完成并可正常展示。
     */
    COMPLETED,

    /**
     * 消息处理失败，需要结合 errorCode 判断原因。
     */
    FAILED
}
