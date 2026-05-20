package com.huanf.noterag.mapper;

import java.time.Instant;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.huanf.noterag.model.ChatSession;

/**
 * Chat 会话持久化。
 */
@Mapper
public interface ChatSessionMapper {

    /**
     * 插入 chat 会话并返回主键。
     */
    @Select("""
            INSERT INTO chat_sessions (title, status)
            VALUES (COALESCE(#{title}, ''), #{status})
            RETURNING id
            """)
    Long insert(ChatSession chatSession);

    /**
     * 按主键查询 chat 会话。
     */
    @Select("""
            SELECT id,
                   title,
                   status,
                   created_at AS createdAt,
                   updated_at AS updatedAt,
                   last_message_at AS lastMessageAt
            FROM chat_sessions
            WHERE id = #{id}
            """)
    ChatSession findById(@Param("id") Long id);

    /**
     * 更新会话标题。
     */
    @Update("""
            UPDATE chat_sessions
            SET title = #{title}
            WHERE id = #{id}
            """)
    int updateTitle(@Param("id") Long id, @Param("title") String title);

    /**
     * 更新会话最后一条消息时间。
     */
    @Update("""
            UPDATE chat_sessions
            SET last_message_at = #{lastMessageAt}
            WHERE id = #{id}
            """)
    int updateLastMessageAt(@Param("id") Long id, @Param("lastMessageAt") Instant lastMessageAt);
}
