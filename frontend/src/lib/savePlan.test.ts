import { describe, expect, it } from 'vitest';
import type { AgentMemorySavePlan, ChatMessage } from '../types';
import {
  applyRequestedMemorySave,
  buildAgentSaveMemoryRequest,
  buildFallbackSavePlan,
  buildRelevantSaveContext,
  buildWriteMemorySaveRequest,
  isExplicitSaveMemoryCommand,
  normalizeMemorySavePlan,
  requestedMemorySave,
  routeAgentSubmission,
  saveMemorySkillMetadata,
  savePlanPersistenceDecision,
} from './savePlan';

function plan(overrides: Partial<AgentMemorySavePlan> = {}): AgentMemorySavePlan {
  return {
    should_save: true,
    memory_library: 'PERSONAL',
    memory_type: 'NOTE',
    content: '今天发生了一件值得记录的事情。',
    title: '一条记录',
    summary: '今天发生了一件值得记录的事情。',
    visibility: 'PRIVATE',
    selected_family_ids: [],
    importance: 3,
    tags: ['记录'],
    reason: '按用户要求整理。',
    confirmation_message: '草稿已准备，请确认后保存。',
    ...overrides,
  };
}

describe('normalizeMemorySavePlan', () => {
  it('keeps the unified memory library and all database memory types', () => {
    const memoryTypes: AgentMemorySavePlan['memory_type'][] = [
      'NOTE',
      'KNOWLEDGE',
      'INSIGHT',
      'EXPERIENCE',
      'OBSERVATION',
      'PREFERENCE',
      'PLAN',
    ];

    expect(memoryTypes.map((memoryType) => normalizeMemorySavePlan(plan({
      memory_library: 'FAMILY',
      memory_type: memoryType,
      visibility: 'FAMILY_VISIBLE',
    })).memory_type)).toEqual(memoryTypes);
  });

  it('uses safe defaults for invalid values without legacy routing', () => {
    const normalized = normalizeMemorySavePlan({
      ...plan(),
      memory_library: 'UNKNOWN' as AgentMemorySavePlan['memory_library'],
      memory_type: 'UNKNOWN' as AgentMemorySavePlan['memory_type'],
      visibility: 'UNKNOWN',
    });

    expect(normalized.memory_library).toBe('PERSONAL');
    expect(normalized.memory_type).toBe('NOTE');
    expect(normalized.visibility).toBe('PRIVATE');
  });

  it('does not persist an empty draft', () => {
    const decision = savePlanPersistenceDecision(plan({ content: '  ' }));

    expect(decision.shouldPersist).toBe(false);
    expect(decision.plan.should_save).toBe(false);
  });
});

describe('memory save request', () => {
  it('builds a family memory request from library and database type only', () => {
    const request = buildWriteMemorySaveRequest(
      10,
      plan({
        memory_library: 'FAMILY',
        memory_type: 'OBSERVATION',
        visibility: 'CARE_VISIBLE',
      }),
      {},
      20,
    );

    expect(request).toMatchObject({
      familyId: 10,
      memoryLibrary: 'FAMILY',
      memoryType: 'OBSERVATION',
      visibility: 'CARE_VISIBLE',
      relatedUserId: 20,
    });
    expect(Object.keys(request).sort()).toEqual([
      'content',
      'familyId',
      'memoryLibrary',
      'memoryType',
      'metadata',
      'relatedUserId',
      'selectedFamilyIds',
      'tags',
      'title',
      'visibility',
    ]);
  });

  it('builds approved Agent memory metadata without a save-type branch', () => {
    const request = buildAgentSaveMemoryRequest(10, plan({
      memory_library: 'PERSONAL',
      memory_type: 'PREFERENCE',
    }), {
      requestId: 'save-1',
      sessionId: 99,
      agentMode: 'persona',
      targetPersonaId: 7,
      targetPersonaName: '外公',
    });

    expect(request.memoryLibrary).toBe('PERSONAL');
    expect(request.memoryType).toBe('PREFERENCE');
    expect(request.metadata).toMatchObject({
      memoryLibrary: 'PERSONAL',
      memoryType: 'PREFERENCE',
      relatedPersonaId: 7,
      relatedPersonaName: '外公',
    });
  });

  it('records only the unified planning fields in skill metadata', () => {
    expect(saveMemorySkillMetadata(plan({
      memory_library: 'FAMILY',
      memory_type: 'KNOWLEDGE',
    }))).toMatchObject({
      skillName: 'save_memory',
      memoryLibrary: 'FAMILY',
      memoryType: 'KNOWLEDGE',
    });
  });
});

describe('explicit save commands', () => {
  it('detects memory library and database type from one command', () => {
    const content = '把这段保存为家庭观察：孩子最近看黑板会眯眼。';

    expect(isExplicitSaveMemoryCommand(content)).toBe(true);
    expect(requestedMemorySave(content)).toEqual({
      memoryLibrary: 'FAMILY',
      memoryType: 'OBSERVATION',
    });
  });

  it('applies an explicit selection exactly to the editable draft', () => {
    const requested = applyRequestedMemorySave(
      plan(),
      { memoryLibrary: 'FAMILY', memoryType: 'EXPERIENCE' },
      '去年全家一起完成了一次长途骑行。',
    );

    expect(requested).toMatchObject({
      should_save: true,
      memory_library: 'FAMILY',
      memory_type: 'EXPERIENCE',
      content: '去年全家一起完成了一次长途骑行。',
      visibility: 'FAMILY_VISIBLE',
    });
  });

  it('routes an inline save command without storing the command text', () => {
    const routed = routeAgentSubmission(
      '保存为个人笔记：我更喜欢早上处理需要专注的任务。',
      [],
    );

    expect(routed.kind).toBe('explicit_save');
    expect(routed.requestedSave).toEqual({
      memoryLibrary: 'PERSONAL',
      memoryType: 'NOTE',
    });
    expect(routed.saveContent).toBe('我更喜欢早上处理需要专注的任务。');
  });

  it('uses recent conversation content for a bare save command', () => {
    const messages: ChatMessage[] = [{
      id: '1',
      role: 'assistant',
      content: '先复述题意，再画线段图。',
      timestamp: '2026-08-05T00:00:00Z',
    }];
    const fallback = buildFallbackSavePlan('保存一下', messages);

    expect(fallback?.content).toBe('先复述题意，再画线段图。');
    expect(fallback?.memory_library).toBe('PERSONAL');
    expect(fallback?.memory_type).toBe('NOTE');
  });
});

describe('save context', () => {
  it('keeps the user turn immediately before an assistant answer', () => {
    const messages: ChatMessage[] = [
      { id: '1', role: 'user', content: '怎么帮助孩子读题？', timestamp: '2026-08-05T00:00:00Z' },
      { id: '2', role: 'assistant', content: '先让他复述题意。', timestamp: '2026-08-05T00:00:01Z' },
    ];

    expect(buildRelevantSaveContext(messages[1], messages)).toEqual(messages);
  });
});
