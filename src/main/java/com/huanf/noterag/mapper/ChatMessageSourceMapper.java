package com.huanf.noterag.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.huanf.noterag.entity.ChatMessageSource;

/**
 * Chat 回答引用来源持久化。
 */
@Mapper
public interface ChatMessageSourceMapper {

    /**
     * 批量插入某条 assistant 消息的引用来源。
     */
    @Insert({
            "<script>",
            "INSERT INTO chat_message_sources (message_id, note_chunk_id, source_order, score)",
            "VALUES",
            "<foreach collection='sources' item='source' separator=','>",
            "(#{source.messageId}, #{source.noteChunkId},",
            "#{source.sourceOrder}, #{source.score})",
            "</foreach>",
            "</script>"
    })
    int batchInsert(@Param("sources") List<ChatMessageSource> sources);
}
