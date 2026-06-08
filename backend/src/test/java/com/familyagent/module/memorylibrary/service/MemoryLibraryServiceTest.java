package com.familyagent.module.memorylibrary.service;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.common.response.PageResult;
import com.familyagent.module.family.service.FamilyService;
import com.familyagent.module.memorylibrary.dto.MemoryLibraryItem;
import com.familyagent.module.memorylibrary.dto.MemoryLibrarySearchRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemoryLibraryServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private FamilyService familyService;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks private MemoryLibraryService memoryLibraryService;

    @Test
    void search_checksMembershipAndUsesCurrentViewerForEveryPermissionSection() {
        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(101L);
            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
            when(jdbcTemplate.query(anyString(), any(Object[].class), any(org.springframework.jdbc.core.RowMapper.class)))
                    .thenReturn(List.of());

            MemoryLibrarySearchRequest request = new MemoryLibrarySearchRequest();
            request.setFamilyId(10L);
            request.setKeyword("牙齿");
            request.setType("ALL");
            request.setPage(2);
            request.setPageSize(3);

            PageResult<MemoryLibraryItem> result = memoryLibraryService.search(request);

            verify(familyService).checkMembership(10L);
            assertEquals(2, result.getPage());
            assertEquals(3, result.getPageSize());

            ArgumentCaptor<Object[]> countArgs = ArgumentCaptor.forClass(Object[].class);
            ArgumentCaptor<Object[]> listArgs = ArgumentCaptor.forClass(Object[].class);
            verify(jdbcTemplate).queryForObject(anyString(), eq(Long.class), countArgs.capture());
            verify(jdbcTemplate).query(anyString(), listArgs.capture(), any(org.springframework.jdbc.core.RowMapper.class));

            assertPermissionSectionArgs(countArgs.getValue(), false);
            assertPermissionSectionArgs(listArgs.getValue(), true);
        }
    }

    @Test
    void search_rejectsUnsupportedTypeBeforeQueryingDatabase() {
        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(101L);
            MemoryLibrarySearchRequest request = new MemoryLibrarySearchRequest();
            request.setFamilyId(10L);
            request.setType("PRIVATE_RAW");

            BusinessException exception = assertThrows(BusinessException.class, () -> memoryLibraryService.search(request));

            assertEquals(ErrorCode.BAD_REQUEST.getCode(), exception.getCode());
            verify(familyService).checkMembership(10L);
        }
    }

    @Test
    void search_requiresFamilyId() {
        MemoryLibrarySearchRequest request = new MemoryLibrarySearchRequest();

        BusinessException exception = assertThrows(BusinessException.class, () -> memoryLibraryService.search(request));

        assertEquals(ErrorCode.BAD_REQUEST.getCode(), exception.getCode());
    }

    private static void assertPermissionSectionArgs(Object[] args, boolean includesPagination) {
        int expectedLength = includesPagination ? 52 : 50;
        assertEquals(expectedLength, args.length);
        assertSection(args, 0, 12);
        assertSection(args, 12, 12);
        assertSection(args, 24, 13);
        assertSection(args, 37, 13);
        if (includesPagination) {
            assertEquals(3, args[50]);
            assertEquals(3, args[51]);
        }
    }

    private static void assertSection(Object[] args, int offset, int length) {
        assertEquals(10L, args[offset]);
        assertEquals(101L, args[offset + 1]);
        assertEquals(101L, args[offset + 2]);
        assertEquals(101L, args[offset + 3]);
        if (length == 13) {
            assertEquals(101L, args[offset + 4]);
        }
    }
}
