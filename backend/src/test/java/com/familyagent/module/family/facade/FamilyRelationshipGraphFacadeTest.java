package com.familyagent.module.family.facade;

import com.familyagent.module.family.dto.FamilyMemberVO;
import com.familyagent.module.family.entity.FamilyRelationship;
import com.familyagent.module.family.repository.FamilyMemberRepository;
import com.familyagent.module.family.repository.FamilyRelationshipRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FamilyRelationshipGraphFacadeTest {

    private final FamilyMemberRepository memberRepository = mock(FamilyMemberRepository.class);
    private final FamilyRelationshipRepository relationshipRepository = mock(FamilyRelationshipRepository.class);
    private final FamilyRelationshipGraphFacade facade = new FamilyRelationshipGraphFacade(
            memberRepository,
            relationshipRepository);

    @Test
    void resolve_prefersViewerLabelAndUsesReverseLabelOnlyAsFallback() {
        when(memberRepository.findMemberViewsByFamilyId(10L)).thenReturn(List.of(
                member(101L, "Xiao Ming", "ming"),
                member(202L, "Uncle Zhang", "zhang"),
                member(303L, null, "aunt-li")));
        when(relationshipRepository.findByFamilyId(10L)).thenReturn(List.of(
                relationship(101L, 202L, "二叔", "侄子"),
                relationship(202L, 303L, "表妹", "表哥"),
                relationship(303L, 101L, "外甥", "小姨")));

        FamilyRelationshipGraphView graph = facade.resolve(
                10L,
                101L,
                202L,
                Set.of(101L, 202L, 303L));

        assertEquals("本人", graph.member(101L).relationshipToViewer());
        assertEquals("二叔", graph.member(202L).relationshipToViewer());
        assertEquals("小姨", graph.member(303L).relationshipToViewer());
        assertEquals("本人", graph.member(202L).relationshipToTarget());
        assertEquals("表妹", graph.member(303L).relationshipToTarget());
    }

    @Test
    void resolve_fallsBackToMemberNameWithoutInferringKinship() {
        when(memberRepository.findMemberViewsByFamilyId(10L)).thenReturn(List.of(
                member(202L, "张三", "zhang")));
        when(relationshipRepository.findByFamilyId(10L)).thenReturn(List.of());

        FamilyRelationshipNode node = facade.resolve(10L, 101L, null, Set.of(202L)).member(202L);

        assertEquals("张三", node.displayName());
        assertEquals("张三", node.relationshipToViewer());
    }

    private static FamilyMemberVO member(Long userId, String nickname, String username) {
        return FamilyMemberVO.builder()
                .userId(userId)
                .nickname(nickname)
                .username(username)
                .build();
    }

    private static FamilyRelationship relationship(
            Long fromUserId,
            Long toUserId,
            String label,
            String reverseLabel) {
        FamilyRelationship relationship = new FamilyRelationship();
        relationship.setFromUserId(fromUserId);
        relationship.setToUserId(toUserId);
        relationship.setLabel(label);
        relationship.setReverseLabel(reverseLabel);
        return relationship;
    }
}
