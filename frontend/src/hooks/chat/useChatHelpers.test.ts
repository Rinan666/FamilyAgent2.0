import { describe, expect, it } from 'vitest';
import { formatMemoryContext, normalizeAssistantMetadata } from './useChatHelpers';
import type { DiaryEntry, GrowthGuardRecord, MemoryEntry } from '@/types';

describe('formatMemoryContext', () => {
  it('preserves recalled author and subject attribution', () => {
    const context = formatMemoryContext({
      libraryItems: [],
      familyMemories: [{
        userId: 202,
        type: 'ELDER_ADVICE',
        content: 'Keep promises made to children.',
        status: 'ACTIVE',
      } as MemoryEntry],
      diaryEntries: [{
        userId: 303,
        rawText: 'I learned to pause before responding.',
        visibility: 'FAMILY_VISIBLE',
      } as DiaryEntry],
      growthRecords: [{
        createdBy: 404,
        targetUserId: 505,
        category: 'EMOTION',
        severity: 2,
        content: 'Became calmer after the routine changed.',
        status: 'ACTIVE',
      } as GrowthGuardRecord],
      sessionSavedMemories: [],
      activationScene: null,
    });

    expect(context).toContain('author=family_user_202');
    expect(context).toContain('author=family_user_303');
    expect(context).toContain('observer=family_user_404 subject=family_user_505');
  });
});

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
