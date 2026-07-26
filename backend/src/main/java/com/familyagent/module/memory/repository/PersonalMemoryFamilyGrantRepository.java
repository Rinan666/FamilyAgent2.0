package com.familyagent.module.memory.repository;

import com.familyagent.module.memory.entity.PersonalMemoryFamilyGrant;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PersonalMemoryFamilyGrantRepository {

    @Insert("""
        INSERT INTO personal_memory_family_grants (memory_id, family_id, granted_by)
        VALUES (#{memoryId}, #{familyId}, #{grantedBy})
        ON CONFLICT (memory_id, family_id) DO NOTHING
        """)
    int insertGrant(
            @Param("memoryId") Long memoryId,
            @Param("familyId") Long familyId,
            @Param("grantedBy") Long grantedBy);

    @Delete("DELETE FROM personal_memory_family_grants WHERE memory_id = #{memoryId}")
    int deleteByMemoryId(@Param("memoryId") Long memoryId);

    @Select("""
        SELECT family_id
        FROM personal_memory_family_grants
        WHERE memory_id = #{memoryId}
        ORDER BY family_id
        """)
    List<Long> findFamilyIdsByMemoryId(@Param("memoryId") Long memoryId);

    @Select("""
        <script>
        SELECT memory_id, family_id
        FROM personal_memory_family_grants
        WHERE memory_id IN
        <foreach collection="memoryIds" item="memoryId" open="(" separator="," close=")">
            #{memoryId}
        </foreach>
        ORDER BY memory_id, family_id
        </script>
        """)
    List<PersonalMemoryFamilyGrant> findByMemoryIds(@Param("memoryIds") List<Long> memoryIds);
}
