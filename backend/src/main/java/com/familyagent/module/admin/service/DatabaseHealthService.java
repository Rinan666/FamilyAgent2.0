package com.familyagent.module.admin.service;

import com.familyagent.common.response.PageResult;
import com.familyagent.module.admin.dto.AdminUserSummary;
import com.familyagent.module.admin.dto.DatabaseHealthResponse;
import com.familyagent.module.admin.dto.FamilyDatabaseSummary;
import com.familyagent.module.admin.dto.MemoryRecallDiagnosticRequest;
import com.familyagent.module.admin.dto.MemoryRecallDiagnosticResponse;
import com.familyagent.module.family.dto.FamilyMemberVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DatabaseHealthService {

    private final DatabaseHealthQuerySupport querySupport;
    private final AdminUserMaintenanceSupport userMaintenanceSupport;
    private final MemoryRecallDiagnosticSupport diagnosticSupport;

    public void deleteUser(Long userId) {
        userMaintenanceSupport.deleteUser(userId);
    }

    public List<FamilyMemberVO> listFamilyMembers(Long familyId) {
        return querySupport.listFamilyMembers(familyId);
    }

    public DatabaseHealthResponse getHealth() {
        return querySupport.getHealth();
    }

    public List<AdminUserSummary> listUsers() {
        return querySupport.listUsers();
    }

    public PageResult<AdminUserSummary> searchUsers(String keyword, int page, int pageSize) {
        return querySupport.searchUsers(keyword, page, pageSize);
    }

    public PageResult<FamilyDatabaseSummary> searchFamilies(String keyword, int page, int pageSize) {
        return querySupport.searchFamilies(keyword, page, pageSize);
    }

    public MemoryRecallDiagnosticResponse diagnoseMemoryRecall(MemoryRecallDiagnosticRequest request) {
        return diagnosticSupport.diagnoseMemoryRecall(request);
    }
}
