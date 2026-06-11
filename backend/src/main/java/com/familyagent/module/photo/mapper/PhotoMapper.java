package com.familyagent.module.photo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.familyagent.module.photo.entity.Photo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PhotoMapper extends BaseMapper<Photo> {

    @Select("SELECT * FROM photos WHERE family_id = #{familyId} ORDER BY created_at DESC LIMIT #{limit}")
    List<Photo> selectByFamilyId(@Param("familyId") Long familyId, @Param("limit") int limit);
}
