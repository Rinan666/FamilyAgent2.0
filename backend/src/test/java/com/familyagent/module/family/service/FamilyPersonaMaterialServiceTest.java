package com.familyagent.module.family.service;

import cn.dev33.satoken.stp.StpUtil;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.module.family.dto.PersonaMaterialVO;
import com.familyagent.module.family.dto.UpsertPersonaMaterialRequest;
import com.familyagent.module.family.entity.FamilyPersonaMaterial;
import com.familyagent.module.family.entity.FamilyPersonaMember;
import com.familyagent.module.family.repository.FamilyPersonaMaterialRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FamilyPersonaMaterialServiceTest {

    @Mock private FamilyPersonaMaterialRepository repository;
    @Mock private FamilyPersonaMemberQueryService queryService;
    @Mock private FamilyService familyService;

    @Test
    void list_allowsFamilyMembersAndReturnsMaterialCards() {
        FamilyPersonaMaterialService service = service();
        when(queryService.requireEntity(10L, 5L)).thenReturn(persona());
        when(repository.findByPersonaId(10L, 5L)).thenReturn(List.of(material()));

        List<PersonaMaterialVO> result = service.list(10L, 5L);

        verify(familyService).checkMembership(10L);
        assertEquals(1, result.size());
        assertEquals("处事提醒", result.get(0).getTitle());
        assertEquals(List.of("家风", "处事"), result.get(0).getTags());
    }

    @Test
    void create_requiresOwnerAndNormalizesBeforePersisting() {
        FamilyPersonaMaterialService service = service();
        when(queryService.requireEntity(10L, 5L)).thenReturn(persona());

        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(8L);
            service.create(10L, 5L, request());
        }

        ArgumentCaptor<FamilyPersonaMaterial> captor = ArgumentCaptor.forClass(FamilyPersonaMaterial.class);
        verify(familyService).checkOwner(10L);
        verify(repository).insert(captor.capture());
        FamilyPersonaMaterial entity = captor.getValue();
        assertEquals(10L, entity.getFamilyId());
        assertEquals(5L, entity.getPersonaId());
        assertEquals("处事提醒", entity.getTitle());
        assertEquals("先看事实，再下判断。", entity.getContent());
        assertArrayEquals(new String[] {"家风", "处事"}, entity.getTags());
        assertEquals(8L, entity.getCreatedBy());
    }

    @Test
    void delete_removesExistingMaterialAfterOwnerCheck() {
        FamilyPersonaMaterialService service = service();
        when(queryService.requireEntity(10L, 5L)).thenReturn(persona());
        when(repository.findByIdAndPersonaId(20L, 10L, 5L)).thenReturn(material());

        service.delete(10L, 5L, 20L);

        verify(familyService).checkOwner(10L);
        verify(repository).deleteById(20L);
    }

    @Test
    void delete_rejectsUnknownMaterial() {
        FamilyPersonaMaterialService service = service();
        when(queryService.requireEntity(10L, 5L)).thenReturn(persona());

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.delete(10L, 5L, 20L));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), error.getCode());
    }

    private FamilyPersonaMaterialService service() {
        return new FamilyPersonaMaterialService(
                repository,
                queryService,
                new FamilyPersonaMaterialAssembler(),
                familyService);
    }

    private static FamilyPersonaMember persona() {
        FamilyPersonaMember persona = new FamilyPersonaMember();
        persona.setId(5L);
        persona.setFamilyId(10L);
        return persona;
    }

    private static FamilyPersonaMaterial material() {
        FamilyPersonaMaterial material = new FamilyPersonaMaterial();
        material.setId(20L);
        material.setFamilyId(10L);
        material.setPersonaId(5L);
        material.setTitle("处事提醒");
        material.setContent("先看事实，再下判断。");
        material.setTags(new String[] {"家风", "处事"});
        material.setCreatedBy(8L);
        return material;
    }

    private static UpsertPersonaMaterialRequest request() {
        UpsertPersonaMaterialRequest request = new UpsertPersonaMaterialRequest();
        request.setTitle(" 处事提醒 ");
        request.setContent(" 先看事实，再下判断。 ");
        request.setTags(Arrays.asList(" 家风 ", "", "家风", "处事", null));
        return request;
    }
}
