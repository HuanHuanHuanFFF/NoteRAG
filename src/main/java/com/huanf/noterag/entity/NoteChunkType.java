package com.huanf.noterag.entity;

/**
 * Note chunk 类型。
 */
public enum NoteChunkType {

    /**
     * Markdown 正文切块。
     */
    CONTENT,

    /**
     * 面向全文召回补充的摘要切块。
     */
    SUMMARY
}
