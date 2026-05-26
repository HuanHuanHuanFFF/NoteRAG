package com.huanf.noterag.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.huanf.noterag.entity.ChatSession;

/**
 * Chat 会话持久化。
 */
@Mapper
public interface ChatSessionMapper {

    /**
     * 插入 chat 会话并返回主键。
     */
    @Select("""
            INSERT INTO chat_sessions (title)
            VALUES (COALESCE(#{title}, ''))
            RETURNING id
            """)
    Long insert(ChatSession chatSession);

    /**
     * 按主键查询 ACTIVE chat 会话；归档会话对业务读取表现为不存在。
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
              AND status = 'ACTIVE'
            """)
    ChatSession findById(@Param("id") Long id);

    /**
     * 查询所有 ACTIVE chat 会话，按最近活跃时间倒序排列。
     */
    @Select("""
            SELECT id,
                   title,
                   status,
                   created_at AS createdAt,
                   updated_at AS updatedAt,
                   last_message_at AS lastMessageAt
            FROM chat_sessions
            WHERE status = 'ACTIVE'
            ORDER BY last_message_at DESC NULLS LAST,
                     updated_at DESC,
                     id DESC
            """)
    List<ChatSession> findAll();

    /**
     * 更新 ACTIVE 会话标题。
     */
    @Update("""
            UPDATE chat_sessions
            SET title = #{title}
            WHERE id = #{id}
              AND status = 'ACTIVE'
            """)
    int updateTitle(@Param("id") Long id, @Param("title") String title);

    /**
     * 将 ACTIVE 会话归档，返回受影响行数。
     */
    @Update("""
            UPDATE chat_sessions
            SET status = 'ARCHIVED'
            WHERE id = #{id}
              AND status = 'ACTIVE'
            """)
    int archiveById(@Param("id") Long id);

    /**
     * 更新会话最后一条消息时间。
     */
    @Update("""
            UPDATE chat_sessions
            SET last_message_at = #{lastMessageAt}
            WHERE id = #{id}
              AND status = 'ACTIVE'
            """)
    int updateLastMessageAt(@Param("id") Long id, @Param("lastMessageAt") Instant lastMessageAt);
}
