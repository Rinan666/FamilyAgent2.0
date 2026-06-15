package com.familyagent.module.session.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.familyagent.module.session.entity.ChatSessionMessage;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ChatSessionMessageRepository extends BaseMapper<ChatSessionMessage> {

    @Select("""
            SELECT id, session_id, seq, client_message_id, role, content, tool_name, metadata, created_at, token_count
            FROM chat_session_messages
            WHERE session_id = #{sessionId}
            ORDER BY seq ASC
            """)
    List<ChatSessionMessage> findBySessionId(@Param("sessionId") Long sessionId);

    @Select("""
            SELECT id, session_id, seq, client_message_id, role, content, tool_name, metadata, created_at, token_count
            FROM chat_session_messages
            WHERE session_id = #{sessionId}
              AND seq <= #{endSeq}
            ORDER BY seq ASC
            """)
    List<ChatSessionMessage> findBySessionIdUpToSeq(@Param("sessionId") Long sessionId, @Param("endSeq") int endSeq);

    @Select("""
            SELECT id, session_id, seq, client_message_id, role, content, tool_name, metadata, created_at, token_count
            FROM chat_session_messages
            WHERE session_id = #{sessionId}
              AND seq BETWEEN #{startSeq} AND #{endSeq}
            ORDER BY seq ASC
            """)
    List<ChatSessionMessage> findBySessionIdAndSeqRange(@Param("sessionId") Long sessionId,
                                                        @Param("startSeq") int startSeq,
                                                        @Param("endSeq") int endSeq);

    @Select("""
            SELECT id, session_id, seq, client_message_id, role, content, tool_name, metadata, created_at, token_count
            FROM chat_session_messages
            WHERE session_id = #{sessionId}
              AND (#{beforeSeq} IS NULL OR seq < #{beforeSeq})
            ORDER BY seq DESC
            LIMIT #{limit}
            """)
    List<ChatSessionMessage> findPageBeforeSeq(@Param("sessionId") Long sessionId,
                                               @Param("beforeSeq") Long beforeSeq,
                                               @Param("limit") int limit);

    @Select("""
            SELECT COALESCE(MAX(seq), 0)
            FROM chat_session_messages
            WHERE session_id = #{sessionId}
            """)
    Integer findMaxSeqBySessionId(@Param("sessionId") Long sessionId);

    @Select("""
            SELECT COUNT(*)
            FROM chat_session_messages
            WHERE session_id = #{sessionId}
            """)
    int countBySessionId(@Param("sessionId") Long sessionId);

    @Delete("""
            DELETE FROM chat_session_messages
            WHERE session_id = #{sessionId}
              AND seq BETWEEN #{startSeq} AND #{endSeq}
            """)
    int deleteSeqRange(@Param("sessionId") Long sessionId,
                       @Param("startSeq") int startSeq,
                       @Param("endSeq") int endSeq);

    @Delete("""
            DELETE FROM chat_session_messages
            WHERE session_id = #{sessionId}
            """)
    int deleteBySessionId(@Param("sessionId") Long sessionId);
}
