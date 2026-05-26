package com.huanf.noterag.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.huanf.noterag.entity.ChatMessageSource;
import com.huanf.noterag.model.ChatMessageSourceChunk;

/**
 * Chat 回答引用来源持久化。
 */
@Mapper
public interface ChatMessageSourceMapper {

    /**
     * 批量插入某条 assistant 消息的引用来源。
     */
    @Insert("""
            <script>
            INSERT INTO chat_message_sources (message_id, note_chunk_id, source_order, score)
            VALUES
            <foreach collection='sources' item='source' separator=','>
            (#{source.messageId}, #{source.noteChunkId}, #{source.sourceOrder}, #{source.score})
            </foreach>
            </script>
            """)
    int batchInsert(@Param("sources") List<ChatMessageSource> sources);

    /**
     * 查询一组 assistant 消息实际引用的来源 chunk，按消息和引用顺序返回。
     */
    @Select("""
            <script>
            SELECT cms.message_id AS messageId,
                   n.id AS noteId,
                   nc.id AS chunkId,
                   n.title AS title,
                   nc.heading_path AS headingPath,
                   nc.content AS content,
                   cms.score AS score
            FROM chat_message_sources cms
            JOIN note_chunks nc ON nc.id = cms.note_chunk_id
            JOIN notes n ON n.id = nc.note_id
            WHERE cms.message_id IN
            <foreach collection='messageIds' item='messageId' open='(' separator=',' close=')'>
            #{messageId}
            </foreach>
            ORDER BY cms.message_id, cms.source_order
            </script>
            """)
    List<ChatMessageSourceChunk> findSourceChunksByMessageIds(@Param("messageIds") List<Long> messageIds);
}
