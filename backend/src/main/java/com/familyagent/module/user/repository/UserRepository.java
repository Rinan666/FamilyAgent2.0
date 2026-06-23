package com.familyagent.module.user.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.familyagent.common.handler.PgJsonbTypeHandler;
import com.familyagent.module.user.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.type.JdbcType;

/**
 * User data access.
 */
@Mapper
public interface UserRepository extends BaseMapper<User> {

    @ResultMap("userWithPassword")
    @Select("""
        SELECT id, username, wechat_open_id, password_hash, nickname, avatar_url, email, phone, role, status, metadata,
               last_login_at, created_at, updated_at
        FROM users
        WHERE username = #{username}
        """)
    User findByUsername(String username);

    @ResultMap("userWithPassword")
    @Select("""
        SELECT id, username, wechat_open_id, password_hash, nickname, avatar_url, email, phone, role, status, metadata,
               last_login_at, created_at, updated_at
        FROM users
        WHERE id = #{id}
        """)
    User findByIdWithPassword(Long id);

    @ResultMap("userWithPassword")
    @Select("""
        SELECT id, username, wechat_open_id, password_hash, nickname, avatar_url, email, phone, role, status, metadata,
               last_login_at, created_at, updated_at
        FROM users
        WHERE wechat_open_id = #{wechatOpenId}
        """)
    User findByWechatOpenId(String wechatOpenId);

    @Results(id = "userWithPassword", value = {
        @Result(column = "metadata", property = "metadata",
                typeHandler = PgJsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    })
    @Select("""
        SELECT id, username, wechat_open_id, nickname, avatar_url, email, phone, role, status, metadata,
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
