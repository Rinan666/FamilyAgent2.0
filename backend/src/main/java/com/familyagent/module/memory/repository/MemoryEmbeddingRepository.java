package com.familyagent.module.memory.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.familyagent.module.memory.entity.MemoryEmbedding;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MemoryEmbeddingRepository extends BaseMapper<MemoryEmbedding> {

    @Select("""
        SELECT COUNT(*) FROM memory_embeddings
        WHERE family_id = #{familyId}
          AND status = 'READY'
        """)
    long countReadyByFamilyId(@Param("familyId") Long familyId);
}
