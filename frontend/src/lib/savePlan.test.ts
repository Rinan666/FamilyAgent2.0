import { describe, expect, it } from 'vitest';
import type { AgentSaveToolPlan } from '../types';
import {
  buildWriteMemorySaveRequest,
  normalizeSaveToolPlan,
  savePlanPersistenceDecision,
  savePlanDetail,
  savedRecordType,
  writeCategoryFromTool,
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

  it('treats none plans as skipped instead of persistable', () => {
    const decision = savePlanPersistenceDecision(plan({
      should_save: false,
      tool: 'NONE',
      content: '',
      reason: '内容缺少具体事实。',
    }));

    expect(decision.shouldPersist).toBe(false);
    expect(decision.plan.tool).toBe('NONE');
    expect(decision.skippedDetail).toContain('内容缺少具体事实');
  });

  it('keeps valid save plans persistable', () => {
    const decision = savePlanPersistenceDecision(plan());

    expect(decision.shouldPersist).toBe(true);
    expect(decision.skippedDetail).toBe('');
  });

  it('does not treat unavailable planning as a raw-save fallback', () => {
    const decision = savePlanPersistenceDecision(plan({
      should_save: false,
      tool: 'NONE',
      content: '',
      reason: '保存规划暂时不可用，已跳过自动保存。',
    }));

    expect(decision.shouldPersist).toBe(false);
    expect(decision.skippedDetail).toBe('暂未保存，可稍后重试。');
  });

  it('builds unified write request for record plan', () => {
    const normalized = normalizeSaveToolPlan(plan());
    const request = buildWriteMemorySaveRequest(10, normalized, { source: 'TEST' });

    expect(request).toMatchObject({
      familyId: 10,
      writeCategory: 'RECORD',
      diaryEntryType: 'SELF_REFLECTION',
      visibility: 'PRIVATE',
      metadata: { source: 'TEST' },
    });
  });

  it('builds unified write request for experience plan', () => {
    const normalized = normalizeSaveToolPlan(plan({
      tool: 'FAMILY_MEMORY',
      memory_type: 'GROWTH_RISK',
      scope: 'CARE_VISIBLE',
    }));
    const request = buildWriteMemorySaveRequest(10, normalized, { source: 'TEST' });

    expect(request).toMatchObject({
      familyId: 10,
      writeCategory: 'EXPERIENCE',
      memoryType: 'GROWTH_RISK',
      visibility: 'CARE_VISIBLE',
      metadata: { source: 'TEST' },
    });
    expect(savedRecordType(normalized.tool)).toBe('FAMILY_MEMORY');
    expect(savePlanDetail(normalized, 88)).toContain('#88');
  });

  it('builds unified write request for observation plan', () => {
    const normalized = normalizeSaveToolPlan(plan({
      tool: 'GROWTH_GUARD',
      category: 'VISION',
      visibility: 'FAMILY_VISIBLE',
    }));
    const request = buildWriteMemorySaveRequest(10, normalized, { source: 'TEST' }, 20);

    expect(request).toMatchObject({
      familyId: 10,
      writeCategory: 'OBSERVATION',
      relatedUserId: 20,
      growthCategory: 'VISION',
      visibility: 'CARE_VISIBLE',
      metadata: { source: 'TEST' },
    });
  });

  it('maps save tool to write category', () => {
    expect(writeCategoryFromTool('DIARY')).toBe('RECORD');
    expect(writeCategoryFromTool('FAMILY_MEMORY')).toBe('EXPERIENCE');
    expect(writeCategoryFromTool('GROWTH_GUARD')).toBe('OBSERVATION');
  });
});
