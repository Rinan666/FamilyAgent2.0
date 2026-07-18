import { describe, expect, it } from 'vitest';
import type { AgentSaveToolPlan } from '../types';
import {
  buildAgentSaveMemoryMetadata,
  buildAgentSaveMemoryToolRequest,
  buildRelevantSaveContext,
  buildWriteMemorySaveRequest,
  isExplicitSaveMemoryCommand,
  normalizeSaveToolPlan,
  routeAgentSubmission,
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
  it('keeps save planning scoped to the selected message and nearby turn', () => {
    const messages = [
      { id: 'old-user', role: 'user', content: 'Old topic', timestamp: '2026-07-01' },
      { id: 'old-assistant', role: 'assistant', content: 'Old answer', timestamp: '2026-07-01' },
      { id: 'current-user', role: 'user', content: 'Current topic', timestamp: '2026-07-02' },
      { id: 'current-assistant', role: 'assistant', content: 'Current answer', timestamp: '2026-07-02' },
    ] as const;

    expect(buildRelevantSaveContext(messages[3], [...messages]).map((item) => item.id))
      .toEqual(['current-user', 'current-assistant']);
  });

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

  it('builds family save-memory tool request with source metadata', () => {
    const normalized = normalizeSaveToolPlan(plan());
    const request = buildAgentSaveMemoryToolRequest(10, normalized, {
      requestId: 'save-memory-message-1',
      sessionId: 88,
      agentMode: 'family',
      familyName: 'Chen Family',
      viewerRole: 'GUARDIAN',
      savedFromMessageRole: 'user',
      savedAt: '2026-07-04T10:00:00.000Z',
    });

    expect(request).toMatchObject({
      familyId: 10,
      writeCategory: 'RECORD',
      requestId: 'save-memory-message-1',
      sessionId: 88,
      agentMode: 'family',
      subject: 'FamilyAgent',
      metadata: {
        source: 'FAMILY_COMPANION_TOOL',
        relationSource: 'FAMILY_AGENT_TOOL',
        familyName: 'Chen Family',
        viewerRole: 'GUARDIAN',
        savedFromMessageRole: 'user',
        plannedTool: 'DIARY',
        plannedTitle: normalized.title,
        plannedReason: normalized.reason,
        confirmationPolicy: 'USER_CONFIRMATION_OR_EXPLICIT_SAVE_COMMAND',
        savedAt: '2026-07-04T10:00:00.000Z',
      },
    });
  });

  it('builds mirror family-memory metadata with target scenario', () => {
    const normalized = normalizeSaveToolPlan(plan({ tool: 'FAMILY_MEMORY' }));
    const metadata = buildAgentSaveMemoryMetadata(normalized, {
      agentMode: 'mirror',
      targetUserId: 202,
      targetMemberName: 'Ming',
      savedFromMessageRole: 'assistant',
      savedAt: '2026-07-04T10:00:00.000Z',
    });

    expect(metadata).toMatchObject({
      source: 'MIRROR_AGENT_TOOL',
      relationSource: 'MIRROR_AGENT_TOOL',
      relatedUserId: 202,
      relatedMemberName: 'Ming',
      savedFromMessageRole: 'assistant',
      sourceType: 'FAMILY_EXPERIENCE',
      scenario: '镜像对话保存',
      target: 'Ming',
    });
  });

  it('builds mirror growth metadata with follow-up status', () => {
    const normalized = normalizeSaveToolPlan(plan({ tool: 'GROWTH_GUARD', category: 'VISION' }));
    const request = buildAgentSaveMemoryToolRequest(10, normalized, {
      agentMode: 'mirror',
      targetUserId: 202,
      targetMemberName: 'Ming',
      savedAt: '2026-07-04T10:00:00.000Z',
    });

    expect(request).toMatchObject({
      writeCategory: 'OBSERVATION',
      relatedUserId: 202,
      growthCategory: 'VISION',
      subject: 'MirrorAgent',
      metadata: {
        sourceType: 'GROWTH_OBSERVATION',
        followUpStatus: 'PENDING',
        relatedUserId: 202,
      },
    });
  });

  it('builds persona family-memory metadata with persona target', () => {
    const normalized = normalizeSaveToolPlan(plan({ tool: 'FAMILY_MEMORY' }));
    const request = buildAgentSaveMemoryToolRequest(10, normalized, {
      agentMode: 'persona',
      targetPersonaId: 303,
      targetPersonaName: 'Grandpa Chen',
      savedAt: '2026-07-04T10:00:00.000Z',
    });

    expect(request).toMatchObject({
      writeCategory: 'EXPERIENCE',
      subject: 'PersonaMemberAgent',
      metadata: {
        source: 'PERSONA_MEMBER_TOOL',
        relationSource: 'PERSONA_MEMBER_TOOL',
        relatedPersonaId: 303,
        relatedPersonaName: 'Grandpa Chen',
        sourceType: 'FAMILY_EXPERIENCE',
        scenario: '精神成员对话保存',
        target: 'Grandpa Chen',
      },
    });
  });

  it('maps save tool to write category', () => {
    expect(writeCategoryFromTool('DIARY')).toBe('RECORD');
    expect(writeCategoryFromTool('FAMILY_MEMORY')).toBe('EXPERIENCE');
    expect(writeCategoryFromTool('GROWTH_GUARD')).toBe('OBSERVATION');
  });

  it('detects explicit memory save commands without matching general save questions', () => {
    expect(isExplicitSaveMemoryCommand('帮我保存')).toBe(true);
    expect(isExplicitSaveMemoryCommand('把刚才的内容记下来')).toBe(true);
    expect(isExplicitSaveMemoryCommand('请保存到记忆库')).toBe(true);
    expect(isExplicitSaveMemoryCommand('给我记一下')).toBe(true);
    expect(isExplicitSaveMemoryCommand('把这段留作记录')).toBe(true);
    expect(isExplicitSaveMemoryCommand('麻烦把这个收进记忆库吧')).toBe(true);
    expect(isExplicitSaveMemoryCommand(
      '保存到记忆库：“标题：应用题先复述题意\n内容：孩子最近做应用题总是先抓数字，我让他先复述题意再画线段图，今天列式明显稳定。\n标签：学习、应用题”',
    )).toBe(true);
    expect(isExplicitSaveMemoryCommand(
      '孩子最近做应用题总是先抓数字，我让他先复述题意再画线段图，今天列式明显稳定。请保存到记忆库。',
    )).toBe(true);
    expect(isExplicitSaveMemoryCommand('怎么保存到本地？')).toBe(false);
    expect(isExplicitSaveMemoryCommand('我今天学会了保存文件的快捷键')).toBe(false);
    expect(isExplicitSaveMemoryCommand('请把今天的作业保存到电脑桌面')).toBe(false);
    expect(isExplicitSaveMemoryCommand('如何把网页内容保存到记忆库？')).toBe(false);
  });

  it('routes explicit save commands with prior non-system context instead of chat', () => {
    const messages = [
      { id: 'system', role: 'system' as const, content: 'internal marker', timestamp: '2026-07-01' },
      ...Array.from({ length: 11 }, (_, index) => ({
        id: `message-${index}`,
        role: index % 2 === 0 ? 'user' as const : 'assistant' as const,
        content: `context ${index}`,
        timestamp: '2026-07-01',
      })),
    ];

    const routed = routeAgentSubmission(' 保存到记忆库吧 ', messages);

    expect(routed.kind).toBe('explicit_save');
    expect(routed.content).toBe('保存到记忆库吧');
    expect(routed.conversationContext).toHaveLength(10);
    expect(routed.conversationContext[0].id).toBe('message-1');
    expect(routed.conversationContext.some((message) => message.role === 'system')).toBe(false);
  });

  it('routes a structured inline memory command directly to save planning', () => {
    const content = '保存到记忆库：标题：应用题先复述题意\n内容：孩子最近做应用题总是先抓数字，我让他先复述题意再画线段图，今天列式明显稳定。\n标签：学习、应用题';

    const routed = routeAgentSubmission(content, []);

    expect(routed.kind).toBe('explicit_save');
    expect(routed.content).toBe(content);
  });

  it('keeps ordinary messages on the chat route', () => {
    const routed = routeAgentSubmission('怎么保存到本地？', []);

    expect(routed.kind).toBe('chat');
    expect(routed.conversationContext).toEqual([]);
  });

  it('never preserves a model claim that planning already saved data', () => {
    const normalized = normalizeSaveToolPlan(plan({
      confirmation_message: '已经完整归档到家庭记忆库。',
    }));

    expect(normalized.confirmation_message).toBe('建议保存为日记，等待后端执行结果。');
    expect(normalized.confirmation_message).not.toContain('已保存');
  });
});
