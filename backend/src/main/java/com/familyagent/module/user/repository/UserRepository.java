package com.familyagent.module.user.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.familyagent.module.user.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 用户数据访问
 */
@Mapper
public interface UserRepository extends BaseMapper<User> {

    @Select("""
        SELECT id, username, password_hash, nickname, avatar_url, email, phone, role, status, metadata,
               last_login_at, created_at, updated_at
        FROM users
        WHERE username = #{username}
        """)
    User findByUsername(String username);

    @Select("""
        SELECT id, username, password_hash, nickname, avatar_url, email, phone, role, status, metadata,
               last_login_at, created_at, updated_at
        FROM users
        WHERE id = #{id}
        """)
    User findByIdWithPassword(Long id);

    @Select("""
        SELECT id, username, nickname, avatar_url, email, phone, role, status, metadata,
               last_login_at, created_at, updated_at
        FROM users
        WHERE id = #{id}
        """)
    User findBasicById(Long id);

    @Select("SELECT COUNT(*) FROM users WHERE username = #{username}")
    int countByUsername(String username);

    @Update("""
        UPDATE users
        SET metadata = CAST(#{metadataJson} AS jsonb),
            updated_at = NOW()
        WHERE id = #{id}
        """)
    int updateMetadata(@Param("id") Long id, @Param("metadataJson") String metadataJson);
}
