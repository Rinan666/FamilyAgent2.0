package com.familyagent.module.admin.service;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.family.facade.FamilyAdministrationFacade;
import com.familyagent.module.user.facade.UserAccountAccess;
import com.familyagent.module.user.facade.UserAccountAccessFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class AdminUserMaintenanceSupport {

    private final PlatformAdminAccessSupport adminAccessSupport;
    private final JdbcTemplate jdbcTemplate;
    private final UserAccountAccessFacade userAccountAccessFacade;
    private final FamilyAdministrationFacade familyAdministrationFacade;

    @Transactional
    void deleteUser(Long userId) {
        adminAccessSupport.requirePlatformAdmin();
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "userId is required");
        }

        Long operatorUserId = CurrentUserGuard.currentUserId();
        if (userId.equals(operatorUserId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Platform admin cannot delete the current account");
        }

        UserAccountAccess target = userAccountAccessFacade.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (target.platformAdmin()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Platform admin accounts cannot be deleted");
        }

        familyAdministrationFacade.prepareForUserDeletion(userId);

        updateNullIfTableExists("families", "created_by", userId);
        updateNullIfTableExists("invite_codes", "created_by", userId);
        updateNullIfTableExists("family_relationships", "created_by", userId);
        updateNullIfTableExists("family_relationships", "updated_by", userId);
        updateNullIfTableExists("care_authorizations", "created_by", userId);
        updateNullIfTableExists("care_authorizations", "updated_by", userId);

        deleteIfTableExists("family_relationships", "from_user_id", userId);
        deleteIfTableExists("family_relationships", "to_user_id", userId);
        deleteIfTableExists("care_authorizations", "subject_user_id", userId);
        deleteIfTableExists("care_authorizations", "caregiver_user_id", userId);
        deleteIfTableExists("growth_guard_staleness_votes", "user_id", userId);
        deleteIfTableExists("memory_entry_votes", "user_id", userId);
        deleteIfTableExists("memory_embeddings", "user_id", userId);
        deleteIfTableExists("skill_runs", "triggered_by", userId);
        deleteIfTableExists("chat_sessions", "user_id", userId);
        deleteIfTableExists("memory_entries", "user_id", userId);
        deleteIfTableExists("mirror_agent_data", "user_id", userId);
        deleteIfTableExists("family_members", "user_id", userId);

        int deleted = jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
        if (deleted == 0) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
    }

    private void deleteIfTableExists(String tableName, String columnName, Long userId) {
        if (!tableExists(tableName)) {
            return;
        }
        jdbcTemplate.update("DELETE FROM " + tableName + " WHERE " + columnName + " = ?", userId);
    }

    private void updateNullIfTableExists(String tableName, String columnName, Long userId) {
        if (!tableExists(tableName)) {
            return;
        }
        jdbcTemplate.update("UPDATE " + tableName + " SET " + columnName + " = NULL WHERE " + columnName + " = ?", userId);
    }

    private boolean tableExists(String tableName) {
        Boolean exists = jdbcTemplate.queryForObject("SELECT to_regclass(?) IS NOT NULL", Boolean.class, "public." + tableName);
        return Boolean.TRUE.equals(exists);
    }
}
