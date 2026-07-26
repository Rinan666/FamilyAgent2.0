'use client';

import { useCallback, useState } from 'react';
import type { SaveFeedback } from '@/components/agent/agentDisplay';
import { agentApi, memoryApi, skillRunApi } from '@/lib/api';
import {
  buildAgentSaveMemoryToolRequest,
  buildFallbackSaveToolPlan,
  savePlanDetail,
  savePlanPersistenceDecision,
  savedRecordType,
} from '@/lib/savePlan';
import type { AgentMode, AgentSaveToolPlan, ChatMessage } from '@/types';

interface UseAgentSaveDraftOptions {
  activeFamilyId?: number | null;
  familyName?: string;
  viewerRole?: string;
  mode: AgentMode;
  targetName?: string;
  targetUserId?: number | null;
  targetPersonaId?: number | null;
  targetPersonaName?: string;
  sessionId: () => number | null;
  onSaved: (plan: AgentSaveToolPlan, savedAt: string) => void;
  onOpenContext: () => void;
}

export function useAgentSaveDraft(options: UseAgentSaveDraftOptions) {
  const [saveFeedback, setSaveFeedback] = useState<Record<string, SaveFeedback>>({});

  const resetSaveDrafts = useCallback(() => setSaveFeedback({}), []);

  const prepareSaveDraft = useCallback(async (
    message: ChatMessage,
    conversationContext: ChatMessage[],
  ) => {
    if (!options.activeFamilyId) {
      setFeedback(message.id, { status: 'error', detail: '请先选择一个家庭再保存。' });
      return;
    }
    if (!message.content.trim()) return;

    setFeedback(message.id, { status: 'saving', detail: '正在生成可编辑草稿...' });
    let skillRunId: number | null = null;
    try {
      const planResult = await memoryApi.planSaveTool({
        familyId: options.activeFamilyId,
        message: message.content.trim(),
        familyContext: options.familyName || '',
        conversationContext,
        targetMemberName: options.targetName || '',
        viewerRole: options.viewerRole || '',
        source: skillSource(options.mode),
        requestId: `save-plan-${message.id}`,
      });
      skillRunId = planResult.skillRunId;
      const decision = savePlanPersistenceDecision(planResult.plan);
      if (!decision.shouldPersist) {
        setFeedback(message.id, { status: 'skipped', detail: decision.skippedDetail });
        return;
      }

      setFeedback(message.id, {
        status: 'draft',
        detail: decision.plan.confirmation_message || '草稿已准备，请修改或确认后保存。',
        skillRunId,
        draft: decision.plan,
      });
    } catch (error) {
      await markSkillRunFailed(skillRunId, error);
      const fallbackDraft = buildFallbackSaveToolPlan(message.content, conversationContext);
      if (fallbackDraft) {
        setFeedback(message.id, {
          status: 'draft',
          detail: 'AI 整理暂时不可用，已保留原文草稿，请检查后保存。',
          draft: fallbackDraft,
        });
        return;
      }
      setFeedback(message.id, {
        status: 'error',
        detail: error instanceof Error ? error.message : '草稿生成失败，请稍后重试。',
      });
    }
  }, [options]);

  const confirmSaveDraft = useCallback(async (message: ChatMessage, editedPlan: AgentSaveToolPlan) => {
    const feedback = saveFeedback[message.id];
    const skillRunId = feedback?.skillRunId ?? null;
    const decision = savePlanPersistenceDecision(editedPlan);
    if (!options.activeFamilyId || !decision.shouldPersist) {
      setFeedback(message.id, {
        status: 'error',
        detail: '草稿标题和内容不能为空。',
        skillRunId: skillRunId ?? undefined,
        draft: editedPlan,
      });
      return;
    }
    const plan = decision.plan;
    if (plan.tool === 'GROWTH_GUARD' && !options.targetUserId) {
      options.onOpenContext();
      setFeedback(message.id, {
        status: 'error',
        detail: '成长观察需要先选择对应家庭成员，或将保存类型改为日记/家庭记忆。',
        skillRunId: skillRunId ?? undefined,
        draft: plan,
      });
      return;
    }

    setFeedback(message.id, {
      status: 'confirming',
      detail: '正在确认并保存草稿...',
      skillRunId: skillRunId ?? undefined,
      draft: plan,
    });

    try {
      const initialResult = await agentApi.requestSaveMemoryTool(buildAgentSaveMemoryToolRequest(
        options.activeFamilyId,
        plan,
        {
          requestId: `save-memory-${message.id}`,
          sessionId: options.sessionId(),
          agentMode: options.mode,
          contextLabel: 'save_memory',
          familyName: options.familyName || '',
          viewerRole: options.viewerRole || '',
          savedFromMessageRole: message.role,
          targetUserId: options.targetUserId,
          targetMemberName: options.mode === 'mirror' ? options.targetName : '',
          targetPersonaId: options.targetPersonaId,
          targetPersonaName: options.mode === 'persona' ? options.targetPersonaName : '',
        },
      ));

      let toolResult = initialResult;
      let confirmationId: number | undefined;
      if (initialResult.status === 'CONFIRMATION_REQUIRED') {
        confirmationId = initialResult.confirmationId ?? undefined;
        if (!confirmationId) throw new Error('保存确认记录缺失，请重试。');
        const confirmation = await agentApi.decideToolConfirmation(confirmationId, 'APPROVE');
        if (confirmation.toolResult) {
          toolResult = confirmation.toolResult;
        } else if (confirmation.confirmation.executionStatus === 'SUCCEEDED') {
          toolResult = {
            success: true,
            status: 'SUCCEEDED',
            retryable: false,
          };
        } else {
          throw new Error('保存确认已处理，但没有执行结果。');
        }
      }
      if (!toolResult.success) {
        throw new Error(toolResult.message || '保存工具执行失败，请稍后重试。');
      }

      if (skillRunId) {
        await skillRunApi.update(skillRunId, {
          status: 'SUCCEEDED',
          saved: true,
          outputSummary: savePlanDetail(plan),
          metadata: {
            confirmationId,
            savedRecordType: savedRecordType(plan.tool),
            plannedTool: plan.tool,
            plannedReason: plan.reason,
          },
        });
      }
      const savedAt = new Date().toISOString();
      options.onSaved(plan, savedAt);
      setFeedback(message.id, {
        status: 'saved',
        detail: savePlanDetail(plan),
        href: savedMemoryHref(options.activeFamilyId, plan),
      });
    } catch (error) {
      await markSkillRunFailed(skillRunId, error);
      setFeedback(message.id, {
        status: 'error',
        detail: error instanceof Error ? error.message : '保存失败，请稍后重试。',
        skillRunId: skillRunId ?? undefined,
        draft: plan,
      });
    }
  }, [options, saveFeedback]);

  const cancelSaveDraft = useCallback(async (message: ChatMessage) => {
    const skillRunId = saveFeedback[message.id]?.skillRunId;
    if (skillRunId) {
      try {
        await skillRunApi.update(skillRunId, {
          status: 'CANCELED',
          saved: false,
          outputSummary: '用户取消保存草稿。',
        });
      } catch {
        // The local cancellation remains valid when audit update fails.
      }
    }
    setFeedback(message.id, { status: 'skipped', detail: '已取消保存草稿。' });
  }, [saveFeedback]);

  function setFeedback(messageId: string, feedback: SaveFeedback) {
    setSaveFeedback((current) => ({ ...current, [messageId]: feedback }));
  }

  return {
    saveFeedback,
    resetSaveDrafts,
    prepareSaveDraft,
    confirmSaveDraft,
    cancelSaveDraft,
  };
}

async function markSkillRunFailed(skillRunId: number | null, error: unknown) {
  if (!skillRunId) return;
  try {
    await skillRunApi.update(skillRunId, {
      status: 'FAILED',
      saved: false,
      outputSummary: error instanceof Error ? error.message : '保存失败',
    });
  } catch {
    // Ignore secondary audit failure.
  }
}

function skillSource(mode: AgentMode) {
  if (mode === 'mirror') return 'MIRROR_AGENT_CHAT';
  if (mode === 'persona') return 'PERSONA_MEMBER_CHAT';
  return 'FAMILY_AGENT_CHAT';
}

function savedMemoryHref(familyId: number, plan: AgentSaveToolPlan) {
  if (plan.tool === 'PERSONAL_MEMORY') return '/dashboard/memory-library?library=personal';
  return `/dashboard/memory-library?familyId=${familyId}&library=family`;
}
