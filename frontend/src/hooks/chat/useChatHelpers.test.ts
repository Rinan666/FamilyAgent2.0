import { describe, expect, it } from 'vitest';
import { formatMemoryContext, normalizeAssistantMetadata } from './useChatHelpers';
import type { DiaryEntry, GrowthGuardRecord, MemoryEntry } from '@/types';

describe('formatMemoryContext', () => {
  it('preserves recalled author and subject attribution', () => {
    const context = formatMemoryContext({
      libraryItems: [],
      familyMemories: [{
        userId: 202,
        type: 'KNOWLEDGE',
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
            id: 'personal-memory-9',
            source_type: 'PERSONAL_MEMORY',
            title: 'Bedtime routine',
            snippet: 'Earlier family memory summary',
            temporal_layer: 'STABLE',
            topics: ['HEALTH'],
            scenes: ['health'],
            author: {
              user_id: 202,
              name: '哥哥',
              relationship_to_viewer: '哥哥',
              current_viewer: false,
              current_target: false,
            },
          },
        ],
      },
      retrievalQuery: 'bedtime health',
      requestId: 'chat-request-1',
      runId: 91,
      effectiveContext: 'MIRROR',
      targetUserId: 202,
      targetLabel: '哥哥',
      contextChanged: true,
      contextSwitchAcknowledged: true,
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
      id: 'personal-memory-9',
      sourceType: 'PERSONAL_MEMORY',
      title: 'Bedtime routine',
      snippet: 'Earlier family memory summary',
      temporalLayer: 'STABLE',
      topics: ['HEALTH'],
      scenes: ['health'],
      author: {
        userId: 202,
        name: '哥哥',
        relationshipToViewer: '哥哥',
        currentViewer: false,
        currentTarget: false,
      },
    });
    expect(metadata.retrievalQuery).toBe('bedtime health');
    expect(metadata.requestId).toBe('chat-request-1');
    expect(metadata.runId).toBe(91);
    expect(metadata.contextSwitchAcknowledged).toBe(true);
    expect(metadata.sessionContextPatch).toMatchObject({
      agentMode: 'mirror',
      targetUserId: 202,
      targetMemberName: '哥哥',
      hasTargetSwitches: true,
    });
  });

  it('keeps persisted camel-case evidence and identity metadata after refresh', () => {
    const metadata = normalizeAssistantMetadata({
      agentMode: 'mirror',
      targetUserId: 202,
      targetMemberName: '大儿子',
      sourceRefs: [{
        code: 'D1',
        title: '周末打球',
        sourceLabel: '本人记录',
        temporalLabel: '近期',
        toneClass: 'bg-sky-50 text-sky-700',
      }],
      rag: {
        diaryCount: 1,
        memoryCount: 0,
        totalReferenceCount: 1,
        sources: [],
      },
    });

    expect(metadata).toMatchObject({
      agentMode: 'mirror',
      targetUserId: 202,
      targetMemberName: '大儿子',
      sourceRefs: [{ code: 'D1', title: '周末打球' }],
    });
    expect(metadata.rag?.totalReferenceCount).toBe(1);
  });
});
