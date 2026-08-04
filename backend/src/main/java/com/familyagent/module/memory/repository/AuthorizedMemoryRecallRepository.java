package com.familyagent.module.memory.repository;

import com.familyagent.module.memory.entity.MemoryEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectProvider;

import java.util.List;

@Mapper
public interface AuthorizedMemoryRecallRepository {

    @SelectProvider(type = AuthorizedMemoryRecallSql.class, method = "visibleFamilyEntriesByOrigin")
    List<MemoryEntry> findVisibleFamilyEntriesByOrigin(
            @Param("familyId") Long familyId,
            @Param("viewerUserId") Long viewerUserId,
            @Param("originType") String originType,
            @Param("limit") int limit);

    @SelectProvider(type = AuthorizedMemoryRecallSql.class, method = "visibleCanonicalMemories")
    List<MemoryEntry> findVisibleCanonicalMemories(
            @Param("familyId") Long familyId,
            @Param("viewerUserId") Long viewerUserId,
            @Param("limit") int limit);

    @SelectProvider(type = AuthorizedMemoryRecallSql.class, method = "visibleAuthorizedRecords")
    List<MemoryEntry> findVisibleAuthorizedRecords(
            @Param("familyId") Long familyId,
            @Param("viewerUserId") Long viewerUserId,
            @Param("limit") int limit);

    @SelectProvider(type = AuthorizedMemoryRecallSql.class, method = "visibleAuthorizedRecordsForTarget")
    List<MemoryEntry> findVisibleAuthorizedRecordsForTarget(
            @Param("familyId") Long familyId,
            @Param("targetUserId") Long targetUserId,
            @Param("viewerUserId") Long viewerUserId,
            @Param("limit") int limit);

    @SelectProvider(type = AuthorizedMemoryRecallSql.class, method = "visibleMirrorSelfDiaries")
    List<MemoryEntry> findVisibleMirrorSelfDiaries(
            @Param("familyId") Long familyId,
            @Param("targetUserId") Long targetUserId,
            @Param("viewerUserId") Long viewerUserId,
            @Param("limit") int limit);

    @SelectProvider(type = AuthorizedMemoryRecallSql.class, method = "visibleMirrorRelatedDiaries")
    List<MemoryEntry> findVisibleMirrorRelatedDiaries(
            @Param("familyId") Long familyId,
            @Param("targetUserId") Long targetUserId,
            @Param("viewerUserId") Long viewerUserId,
            @Param("limit") int limit);
}
