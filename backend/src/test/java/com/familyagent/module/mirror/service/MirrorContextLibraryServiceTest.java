package com.familyagent.module.mirror.service;

import com.familyagent.module.memorylibrary.dto.MemoryLibraryItem;
import com.familyagent.module.memorylibrary.dto.MemoryLibrarySearchRequest;
import com.familyagent.module.memorylibrary.facade.MirrorMemoryLibraryFacade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MirrorContextLibraryServiceTest {

    @Mock private MirrorMemoryLibraryFacade memoryLibrary;

    @Test
    void recallLibraryItemsShouldUseFacadeAndRemoveDuplicates() {
        MemoryLibraryItem memberItem = item("member-1");
        MemoryLibraryItem familyItem = item("family-1");
        when(memoryLibrary.search(any()))
                .thenReturn(List.of(memberItem), List.of(memberItem, familyItem));
        MirrorContextLibraryService service = new MirrorContextLibraryService(memoryLibrary);

        List<MemoryLibraryItem> result = service.recallLibraryItems(3L, 8L, "school choice");

        assertEquals(List.of(memberItem, familyItem), result);
        ArgumentCaptor<MemoryLibrarySearchRequest> requestCaptor =
                ArgumentCaptor.forClass(MemoryLibrarySearchRequest.class);
        verify(memoryLibrary, times(2)).search(requestCaptor.capture());
        assertEquals("ALL", requestCaptor.getAllValues().get(0).getType());
        assertEquals("FAMILY_EXPERIENCE", requestCaptor.getAllValues().get(1).getType());
    }

    private static MemoryLibraryItem item(String id) {
        return MemoryLibraryItem.builder().id(id).build();
    }
}
