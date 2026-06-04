package com.familyagent.module.question.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.familyagent.module.question.entity.KnowledgePoint;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 知识点数据访问
 */
@Mapper
public interface KnowledgePointRepository extends BaseMapper<KnowledgePoint> {

    @Select("SELECT * FROM knowledge_points WHERE parent_id IS NULL ORDER BY sort_order")
    List<KnowledgePoint> findRoots();

    @Select("SELECT * FROM knowledge_points WHERE parent_id = #{parentId} ORDER BY sort_order")
    List<KnowledgePoint> findByParentId(Long parentId);

    @Select("SELECT * FROM knowledge_points WHERE subject = #{subject} ORDER BY level, sort_order")
    List<KnowledgePoint> findBySubject(String subject);
}
