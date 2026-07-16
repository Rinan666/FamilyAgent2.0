package com.familyagent.module.memory.service;

import com.familyagent.module.memory.dto.EmbeddingCallObservation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorizedMemoryRecallRankingServiceTest {

    @Mock private AuthorizedMemoryRecallEmbeddingService embeddingService;
    @Mock private JdbcTemplate jdbcTemplate;

    @Test
    void rank_propagatesEmbeddingObservationWithoutOwningProviderLogic() {
        EmbeddingCallObservation observation = new EmbeddingCallObservation(
                true, true, false, "local", "local/hash-embedding", 1536, 18L, null);
        when(embeddingService.embed(10L, "bedtime reminder"))
                .thenReturn(new AuthorizedMemoryRecallEmbeddingService.RecallQueryEmbedding(
                        java.util.Collections.nCopies(1536, 0.01),
                        observation));
        AuthorizedMemoryRecallRankingService service = new AuthorizedMemoryRecallRankingService(
                embeddingService,
                jdbcTemplate);

        AuthorizedMemoryRecallRankingService.RankedRecall result = service.rank(
                10L,
                "bedtime reminder",
                List.of(),
                List.of(),
                List.of(),
                3,
                3,
                1L);

        assertEquals(observation, result.embeddingObservation());
        verifyNoInteractions(jdbcTemplate);
    }
}
