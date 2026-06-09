package com.familyagent.module.session.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.familyagent.module.session.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

import java.util.List;

/**
 * 会话数据访问
 */
@Mapper
public interface ChatSessionRepository extends BaseMapper<ChatSession> {

    @Select("SELECT * FROM chat_sessions WHERE user_id = #{userId} ORDER BY started_at DESC LIMIT #{limit}")
    List<ChatSession> findByUserId(Long userId, int limit);

    @Select("SELECT * FROM chat_sessions WHERE user_id = #{userId} AND status = 'ACTIVE' ORDER BY started_at DESC")
    List<ChatSession> findActiveByUserId(Long userId);

    @Select("""
            SELECT id, user_id, family_id, summary, status, visibility, source, started_at, ended_at
            FROM chat_sessions
            WHERE id = #{id}
            """)
    ChatSession findStatusById(Long id);

    @Update("""
            UPDATE chat_sessions
            SET status = 'ENDED',
                summary = CASE WHEN #{summary} IS NULL THEN summary ELSE #{summary} END,
                ended_at = #{endedAt}
            WHERE id = #{sessionId}
              AND status = 'ACTIVE'
            """)
    int endActiveSession(@Param("sessionId") Long sessionId,
                         @Param("summary") String summary,
                         @Param("endedAt") LocalDateTime endedAt);

    @Update("""
            UPDATE chat_sessions
            SET messages = CAST(#{messages, typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler} AS jsonb)
            WHERE id = #{sessionId}
            """)
    int updateSessionMessages(@Param("sessionId") Long sessionId,
                              @Param("messages") Object messages);

    @Update("""
            UPDATE chat_sessions
            SET ended_at = #{endedAt}
            WHERE id = #{sessionId}
              AND status = 'ENDED'
              AND ended_at IS NULL
            """)
    int fillMissingEndedAt(@Param("sessionId") Long sessionId,
                           @Param("endedAt") LocalDateTime endedAt);
}
