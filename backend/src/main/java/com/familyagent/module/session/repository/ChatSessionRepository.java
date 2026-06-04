package com.familyagent.module.session.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.familyagent.module.session.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

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
}
