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

    @Select("SELECT * FROM users WHERE username = #{username}")
    User findByUsername(String username);

    @Select("SELECT COUNT(*) FROM users WHERE username = #{username}")
    int countByUsername(String username);
}
