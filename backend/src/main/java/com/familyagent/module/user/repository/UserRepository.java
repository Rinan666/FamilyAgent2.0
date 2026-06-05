package com.familyagent.module.user.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.familyagent.module.user.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 用户数据访问
 */
@Mapper
public interface UserRepository extends BaseMapper<User> {

    @Select("""
        SELECT id, username, password_hash, nickname, avatar_url, email, phone, role, status,
               last_login_at, created_at, updated_at
        FROM users
        WHERE username = #{username}
        """)
    User findByUsername(String username);

    @Select("""
        SELECT id, username, nickname, avatar_url, email, phone, role, status,
               last_login_at, created_at, updated_at
        FROM users
        WHERE id = #{id}
        """)
    User findBasicById(Long id);

    @Select("SELECT COUNT(*) FROM users WHERE username = #{username}")
    int countByUsername(String username);
}
