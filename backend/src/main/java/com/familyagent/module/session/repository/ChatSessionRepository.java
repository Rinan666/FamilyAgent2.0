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
 * 浼氳瘽鏁版嵁璁块棶
 */
@Mapper
public interface ChatSessionRepository extends BaseMapper<ChatSession> {

    @Select("""
            SELECT id, user_id, family_id, question_id, subject, knowledge_point_id,
                   title, summary, status, visibility, source, metadata,
                   started_at, ended_at, last_message_at, message_count, token_count,
                   archived_before_seq, archive_status, archive_metadata
            FROM chat_sessions
            WHERE user_id = #{userId}
            ORDER BY COALESCE(last_message_at, started_at) DESC, started_at DESC
            LIMIT #{limit}
            """)
    List<ChatSession> findByUserId(@Param("userId") Long userId, @Param("limit") int limit);

    @Select("""
            SELECT id, user_id, family_id, question_id, subject, knowledge_point_id,
                   title, summary, status, visibility, source, metadata,
                   started_at, ended_at, last_message_at, message_count, token_count,
                   archived_before_seq, archive_status, archive_metadata
            FROM chat_sessions
            WHERE user_id = #{userId}
              AND status = 'ACTIVE'
            ORDER BY COALESCE(last_message_at, started_at) DESC, started_at DESC
            """)
    List<ChatSession> findActiveByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT id, user_id, family_id, question_id, subject, knowledge_point_id,
                   title, summary, status, visibility, permission_scope, source, metadata,
                   started_at, ended_at, last_message_at, message_count, token_count,
                   archived_before_seq, archive_status, archive_metadata
            FROM chat_sessions
            WHERE id = #{id}
            """)
    ChatSession findHeaderById(@Param("id") Long id);

    @Select("""
            SELECT *
            FROM chat_sessions
            WHERE COALESCE((metadata->>'storageVersion')::int, 0) < 2
               OR (message_count = 0 AND messages IS NOT NULL AND jsonb_typeof(messages) = 'array' AND jsonb_array_length(messages) > 0)
            ORDER BY id ASC
            """)
    List<ChatSession> findSessionsNeedingBackfill();

    @Update("""
            UPDATE chat_sessions
            SET status = 'ENDED',
                summary = COALESCE(#{summary, jdbcType=VARCHAR}, summary),
                ended_at = #{endedAt}
            WHERE id = #{sessionId}
              AND status = 'ACTIVE'
            """)
    int endActiveSession(@Param("sessionId") Long sessionId,
                         @Param("summary") String summary,
                         @Param("endedAt") LocalDateTime endedAt);

    @Update("""
            UPDATE chat_sessions
            SET ended_at = #{endedAt}
            WHERE id = #{sessionId}
              AND status = 'ENDED'
              AND ended_at IS NULL
            """)
    int fillMissingEndedAt(@Param("sessionId") Long sessionId,
                           @Param("endedAt") LocalDateTime endedAt);

    @Update("""
            UPDATE chat_sessions
            SET metadata = CAST(#{metadata, typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler} AS jsonb),
                archive_metadata = CAST(#{archiveMetadata, typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler} AS jsonb)
            WHERE id = #{sessionId}
            """)
    int updateStorageMetadata(@Param("sessionId") Long sessionId,
                              @Param("metadata") Object metadata,
                              @Param("archiveMetadata") Object archiveMetadata);
}
