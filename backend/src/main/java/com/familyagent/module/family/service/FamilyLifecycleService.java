package com.familyagent.module.family.service;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.module.family.entity.Family;
import com.familyagent.module.family.entity.FamilyMember;
import com.familyagent.module.family.repository.FamilyMemberRepository;
import com.familyagent.module.family.repository.FamilyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class FamilyLifecycleService {

    private static final List<TableColumn> FAMILY_SCOPED_DELETE_TARGETS = List.of(
            new TableColumn("family_relationships", "family_id"),
            new TableColumn("care_authorizations", "family_id"),
            new TableColumn("growth_guard_staleness_votes", "family_id"),
            new TableColumn("memory_entry_votes", "family_id"),
            new TableColumn("heritage_tasks", "family_id"),
            new TableColumn("growth_guard_records", "family_id"),
            new TableColumn("memory_embeddings", "family_id"),
            new TableColumn("skill_runs", "family_id"),
            new TableColumn("diary_entries", "family_id"),
            new TableColumn("memory_entries", "family_id"),
            new TableColumn("chat_sessions", "family_id"),
            new TableColumn("mirror_agent_data", "primary_family_id")
    );

    private final JdbcTemplate jdbcTemplate;
    private final FamilyRepository familyRepository;
    private final FamilyMemberRepository familyMemberRepository;

    @Transactional
    public void prepareFamiliesForUserDeletion(Long userId) {
        List<FamilyMember> memberships = familyMemberRepository.findByUserId(userId);
        if (memberships.isEmpty()) {
            return;
        }

        List<String> blockingFamilies = new ArrayList<>();
        Set<Long> familiesToDissolve = new LinkedHashSet<>();

        for (FamilyMember membership : memberships) {
            List<FamilyMember> familyMembers = familyMemberRepository.findByFamilyId(membership.getFamilyId());
            long remainingCount = familyMembers.stream()
                    .filter(member -> !Objects.equals(member.getUserId(), userId))
                    .count();

            if (isOwner(membership.getRole())) {
                if (remainingCount > 0) {
                    Family family = familyRepository.selectById(membership.getFamilyId());
                    String familyName = family != null && family.getName() != null && !family.getName().isBlank()
                            ? family.getName()
                            : "Family";
                    blockingFamilies.add("#" + membership.getFamilyId() + " " + familyName);
                    continue;
                }
                familiesToDissolve.add(membership.getFamilyId());
                continue;
            }

            if (remainingCount == 0) {
                familiesToDissolve.add(membership.getFamilyId());
            }
        }

        if (!blockingFamilies.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "Transfer family owner before deleting this user: " + String.join(", ", blockingFamilies));
        }

        for (Long familyId : familiesToDissolve) {
            dissolveFamily(familyId, "USER_DELETE_LAST_MEMBER");
        }
    }

    @Transactional
    public void transferOwner(Long familyId, Long targetUserId, Long operatorUserId) {
        if (familyId == null || familyId <= 0 || targetUserId == null || targetUserId <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "familyId and targetUserId are required");
        }

        Family family = familyRepository.selectById(familyId);
        if (family == null) {
            throw new BusinessException(ErrorCode.FAMILY_NOT_FOUND);
        }

        FamilyMember targetMember = familyMemberRepository.findByFamilyAndUser(familyId, targetUserId);
        if (targetMember == null) {
            throw new BusinessException(ErrorCode.NOT_FAMILY_MEMBER, "Target user is not a member of this family");
        }
        if (isOwner(targetMember.getRole())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Target user is already the family owner");
        }

        List<FamilyMember> owners = familyMemberRepository.findByFamilyId(familyId).stream()
                .filter(member -> isOwner(member.getRole()))
                .toList();
        if (owners.size() != 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Family owner state is invalid; expected exactly one owner");
        }

        FamilyMember currentOwner = owners.get(0);
        familyMemberRepository.updateRole(familyId, currentOwner.getUserId(), "MEMBER");
        familyMemberRepository.updateRole(familyId, targetUserId, "OWNER");
        familyRepository.updateCreatedBy(familyId, targetUserId);

        log.info("Family owner transferred: familyId={}, fromUserId={}, toUserId={}, operatorUserId={}",
                familyId, currentOwner.getUserId(), targetUserId, operatorUserId);
    }

    public List<FamilyOwnerState> auditHistoricalFamilyStates() {
        if (!tableExists("families") || !tableExists("family_members")) {
            return List.of();
        }

        List<FamilyOwnerState> familyStates = jdbcTemplate.query("""
                SELECT
                    f.id AS family_id,
                    f.name AS family_name,
                    COUNT(fm.id) AS member_count,
                    COUNT(*) FILTER (WHERE UPPER(COALESCE(fm.role, '')) = 'OWNER') AS owner_count
                FROM families f
                LEFT JOIN family_members fm ON fm.family_id = f.id
                GROUP BY f.id, f.name
                ORDER BY f.id ASC
                """, (rs, rowNum) -> new FamilyOwnerState(
                rs.getLong("family_id"),
                rs.getString("family_name"),
                rs.getLong("member_count"),
                rs.getLong("owner_count")));

        for (FamilyOwnerState state : familyStates) {
            if (state.memberCount() == 0) {
                log.warn("Family lifecycle audit detected empty family: familyId={}, familyName={}, memberCount={}, ownerCount={}",
                        state.familyId(), state.familyName(), state.memberCount(), state.ownerCount());
                continue;
            }
            if (state.ownerCount() == 0) {
                log.warn("Family lifecycle audit detected ownerless family: familyId={}, familyName={}, memberCount={}, ownerCount={}",
                        state.familyId(), state.familyName(), state.memberCount(), state.ownerCount());
                continue;
            }
            if (state.ownerCount() > 1) {
                log.warn("Family lifecycle audit detected multiple owners: familyId={}, familyName={}, memberCount={}, ownerCount={}",
                        state.familyId(), state.familyName(), state.memberCount(), state.ownerCount());
            }
        }
        return familyStates;
    }

    @Transactional
    public void dissolveFamily(Long familyId, String reason) {
        Family family = familyRepository.selectById(familyId);
        if (family == null) {
            return;
        }

        familyMemberRepository.removeByFamilyId(familyId);
        for (TableColumn target : FAMILY_SCOPED_DELETE_TARGETS) {
            deleteByLongColumnIfTableExists(target.tableName(), target.columnName(), familyId);
        }
        familyRepository.deleteById(familyId);

        log.info("Family dissolved: familyId={}, familyName={}, reason={}", familyId, family.getName(), reason);
    }

    private void deleteByLongColumnIfTableExists(String tableName, String columnName, Long value) {
        if (!tableExists(tableName)) {
            return;
        }
        jdbcTemplate.update("DELETE FROM " + tableName + " WHERE " + columnName + " = ?", value);
    }

    private boolean tableExists(String tableName) {
        Boolean exists = jdbcTemplate.queryForObject("SELECT to_regclass(?) IS NOT NULL", Boolean.class, "public." + tableName);
        return Boolean.TRUE.equals(exists);
    }

    private boolean isOwner(String role) {
        return "OWNER".equalsIgnoreCase(role);
    }

    private record TableColumn(String tableName, String columnName) {
    }

    record FamilyOwnerState(Long familyId, String familyName, long memberCount, long ownerCount) {
    }
}
