import { describe, expect, it } from 'vitest';
import type { AgentSaveToolPlan } from '../types';
import {
  buildDiarySaveRequest,
  buildFamilyMemorySaveRequest,
  buildGrowthGuardSaveRequest,
  normalizeSaveToolPlan,
  savePlanDetail,
  savedRecordType,
} from './savePlan';

function plan(overrides: Partial<AgentSaveToolPlan> = {}): AgentSaveToolPlan {
  return {
    should_save: true,
    tool: 'DIARY',
    content: '今天和孩子聊作业时，他主动说自己读题太快，下次先复述题意。',
    title: '读题复盘',
    summary: '孩子意识到读题太快，准备先复述题意。',
    visibility: 'PRIVATE',
    entry_type: 'SELF_REFLECTION',
    memory_type: 'ELDER_ADVICE',
    scope: 'PRIVATE',
    category: 'OTHER',
    severity: 2,
    importance: 3,
    tags: ['学习'],
    reason: '有具体学习行为变化。',
    confirmation_message: '已保存为每日记录。',
    ...overrides,
  };
}

describe('savePlan helpers', () => {
  it('normalizes unsupported enum values before persistence', () => {
    const normalized = normalizeSaveToolPlan(plan({
      tool: 'FAMILY_MEMORY',
      visibility: 'PUBLIC',
      scope: 'EVERYONE',
      entry_type: 'MOOD',
      memory_type: 'PRINCIPLE',
      category: 'ATTITUDE',
      severity: 99,
      importance: 0,
    } as Partial<AgentSaveToolPlan>));

    expect(normalized.tool).toBe('FAMILY_MEMORY');
    expect(normalized.visibility).toBe('FAMILY_VISIBLE');
    expect(normalized.scope).toBe('FAMILY_VISIBLE');
    expect(normalized.entry_type).toBe('DAILY');
    expect(normalized.memory_type).toBe('ELDER_ADVICE');
    expect(normalized.category).toBe('OTHER');
    expect(normalized.severity).toBe(5);
    expect(normalized.importance).toBe(1);
  });

  it('blocks empty content even when should_save is true', () => {
    const normalized = normalizeSaveToolPlan(plan({ content: '   ', tool: 'GROWTH_GUARD' }));

    expect(normalized.should_save).toBe(false);
    expect(normalized.tool).toBe('NONE');
  });

  it('builds diary save request from normalized plan', () => {
    const normalized = normalizeSaveToolPlan(plan());
    const request = buildDiarySaveRequest(10, normalized, { source: 'TEST' });

    expect(request).toMatchObject({
      familyId: 10,
      entryType: 'SELF_REFLECTION',
      visibility: 'PRIVATE',
      metadata: { source: 'TEST' },
    });
  });

  it('builds family memory save request with backend-safe type and scope', () => {
    const normalized = normalizeSaveToolPlan(plan({
      tool: 'FAMILY_MEMORY',
      memory_type: 'GROWTH_RISK',
      scope: 'CARE_VISIBLE',
    }));
    const request = buildFamilyMemorySaveRequest(10, normalized, { source: 'TEST' });

    expect(request).toMatchObject({
      familyId: 10,
      type: 'GROWTH_RISK',
      scope: 'CARE_VISIBLE',
      metadata: { source: 'TEST' },
    });
    expect(savedRecordType(normalized.tool)).toBe('FAMILY_MEMORY');
    expect(savePlanDetail(normalized, 88)).toContain('#88');
  });

  it('builds growth guard save request for a target member', () => {
    const normalized = normalizeSaveToolPlan(plan({
      tool: 'GROWTH_GUARD',
      category: 'VISION',
      visibility: 'FAMILY_VISIBLE',
    }));
    const request = buildGrowthGuardSaveRequest(10, normalized, '2026-06-08', { source: 'TEST' }, 20);

    expect(request).toMatchObject({
      familyId: 10,
      targetUserId: 20,
      category: 'VISION',
      observedAt: '2026-06-08',
      visibility: 'CARE_VISIBLE',
      metadata: { source: 'TEST' },
    });
  });
});
