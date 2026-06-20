package com.familyagent.module.family.service;

import cn.dev33.satoken.stp.StpUtil;
import com.familyagent.common.lifecycle.PersonaScopedResourceCleaner;
import com.familyagent.module.family.dto.CreatePersonaMemberRequest;
import com.familyagent.module.family.dto.DeletePersonaMemberRequest;
import com.familyagent.module.family.entity.FamilyPersonaMember;
import com.familyagent.module.family.repository.FamilyPersonaMemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FamilyPersonaMemberCommandServiceTest {

    @Mock private FamilyPersonaMemberRepository repository;
    @Mock private FamilyPersonaMemberQueryService queryService;
    @Mock private FamilyPersonaMaterialService materialService;
    @Mock private FamilyService familyService;
    @Mock private PersonaScopedResourceCleaner personaResourceCleaner;

    @Test
    void create_trimsTextFieldsBeforePersisting() {
        FamilyPersonaMemberCommandService service = service();
        when(repository.countByFamilyId(10L)).thenReturn(0);

        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(8L);
            service.create(10L, createRequest());
        }

        ArgumentCaptor<FamilyPersonaMember> captor = ArgumentCaptor.forClass(FamilyPersonaMember.class);
        verify(repository).insert(captor.capture());
        FamilyPersonaMember entity = captor.getValue();
        assertEquals("外公", entity.getName());
        assertEquals("重视家风", entity.getDescription());
        assertNull(entity.getEraIdentity());
        assertEquals(8L, entity.getCreatedBy());
    }

    @Test
    void delete_acceptsConfirmationWithAccidentalWhitespace() {
        FamilyPersonaMemberCommandService service = service();
        FamilyPersonaMember entity = new FamilyPersonaMember();
        entity.setId(5L);
        entity.setFamilyId(10L);
        when(queryService.requireEntity(10L, 5L)).thenReturn(entity);

        DeletePersonaMemberRequest request = new DeletePersonaMemberRequest();
        request.setConfirmationWord(" 确认删除 ");

        service.delete(10L, 5L, request);

        InOrder inOrder = inOrder(personaResourceCleaner, materialService, repository);
        inOrder.verify(personaResourceCleaner).cleanPersonaResources(10L, 5L);
        inOrder.verify(materialService).deleteByPersona(10L, 5L);
        inOrder.verify(repository).deleteByIdAndFamilyId(5L, 10L);
    }

    private FamilyPersonaMemberCommandService service() {
        return new FamilyPersonaMemberCommandService(
                repository,
                queryService,
                new FamilyPersonaMemberAssembler(),
                materialService,
                familyService,
                List.of(personaResourceCleaner));
    }

    private static CreatePersonaMemberRequest createRequest() {
        CreatePersonaMemberRequest request = new CreatePersonaMemberRequest();
        request.setName(" 外公 ");
        request.setDescription(" 重视家风 ");
        request.setEraIdentity(" ");
        return request;
    }
}
