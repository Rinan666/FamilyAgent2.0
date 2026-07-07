import { describe, expect, it } from 'vitest';
import { normalizeAssistantMetadata } from './useChatHelpers';

describe('normalizeAssistantMetadata', () => {
  it('keeps server-sent RAG evidence metadata for the answer drawer', () => {
    const metadata = normalizeAssistantMetadata({
      type: 'metadata',
      rag: {
        retrieval_mode: 'VECTOR_WITH_TEXT_FALLBACK',
        embedding_ready_count: 12,
        diary_count: 1,
        memory_count: 2,
        growth_record_count: 1,
        total_reference_count: 4,
        sources: [
          {
            id: 'memory-9',
            source_type: 'FAMILY_EXPERIENCE',
            title: 'Bedtime routine',
            snippet: 'Earlier family memory summary',
            temporal_layer: 'STABLE',
            topics: ['HEALTH'],
            scenes: ['health'],
          },
        ],
      },
      retrievalQuery: 'bedtime health',
    });

    expect(metadata.rag).toMatchObject({
      retrievalMode: 'VECTOR_WITH_TEXT_FALLBACK',
      embeddingReadyCount: 12,
      diaryCount: 1,
      memoryCount: 2,
      growthRecordCount: 1,
      totalReferenceCount: 4,
    });
    expect(metadata.rag?.sources[0]).toMatchObject({
      id: 'memory-9',
      sourceType: 'FAMILY_EXPERIENCE',
      title: 'Bedtime routine',
      snippet: 'Earlier family memory summary',
      temporalLayer: 'STABLE',
      topics: ['HEALTH'],
      scenes: ['health'],
    });
    expect(metadata.retrievalQuery).toBe('bedtime health');
  });
});
