package com.familyagent.module.memorylibrary.facade;

import com.familyagent.module.memorylibrary.dto.MemoryLibraryItem;
import com.familyagent.module.memorylibrary.dto.MemoryLibrarySearchRequest;
import com.familyagent.module.memorylibrary.service.MemoryLibraryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class MirrorMemoryLibraryFacade {

    private final MemoryLibraryService memoryLibraryService;

    public List<MemoryLibraryItem> search(MemoryLibrarySearchRequest request) {
        var page = memoryLibraryService.search(request);
        if (page == null || page.getItems() == null) {
            return List.of();
        }
        return page.getItems();
    }
}
