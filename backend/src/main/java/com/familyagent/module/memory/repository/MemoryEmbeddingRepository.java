package com.familyagent.module.memory.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.familyagent.module.memory.entity.MemoryEmbedding;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MemoryEmbeddingRepository extends BaseMapper<MemoryEmbedding> {

    @Select("""
        SELECT COUNT(*) FROM memory_embeddings
        WHERE family_id = #{familyId}
          AND status = 'READY'
        """)
    long countReadyByFamilyId(@Param("familyId") Long familyId);

    @Select("""
        <script>
        SELECT COUNT(*) FROM memory_embeddings
        WHERE status = 'READY'
          AND (
            family_id = #{familyId}
            <if test="personalMemoryIds != null and personalMemoryIds.size() > 0">
              OR (
                family_id IS NULL
                AND source_type = 'MEMORY'
                AND source_id IN
                <foreach collection="personalMemoryIds" item="sourceId" open="(" separator="," close=")">
                  #{sourceId}
                </foreach>
              )
            </if>
          )
        </script>
        """)
    long countReadyForRecall(
            @Param("familyId") Long familyId,
            @Param("personalMemoryIds") List<Long> personalMemoryIds);
}
