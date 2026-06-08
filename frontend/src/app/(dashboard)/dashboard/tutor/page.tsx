'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import type {
  AbilityProfile,
  AgentSaveToolPlan,
  ChatMessage,
  ChatSession,
  KnowledgePoint,
} from '@/types';
import { CheckCircle, FileText, History, Loader2, MessageSquareText, Paperclip, Plus, Send, Sparkles, Trash2, XCircle } from 'lucide-react';
import MathRenderer from '@/components/tutor/MathRenderer';
import RagMemoryBadge from '@/components/tutor/RagMemoryBadge';
import WebSearchBadge from '@/components/tutor/WebSearchBadge';
import { useAuthStore } from '@/stores/authStore';
import { useChatStore } from '@/stores/chatStore';
import { useChat, type SessionSavedMemory } from '@/hooks/useChat';
import { useViewerRole } from '@/hooks/useViewerRole';
import { assessmentApi, diaryApi, growthGuardApi, memoryApi, questionApi, sessionApi, skillRunApi, tutorApi } from '@/lib/api';
import {
  buildDiarySaveRequest,
  buildFamilyMemorySaveRequest,
  buildGrowthGuardSaveRequest,
  normalizeSaveToolPlan,
  saveMemorySkillMetadata,
  savePlanDetail,
  savedRecordType,
  todayString,
  toolLabel,
  truncateAuditText,
} from '@/lib/savePlan';

type ActivationSceneState = {
  label: string;
  instruction: string;
};

type TutorChatMessage = ChatMessage & {
  toolResult?: {
    label: string;
    detail: string;
    memoryHref?: string;
    followUpPrompt?: string;
  };
  saveSuggestion?: {
    originalContent: string;
  };
};

function formatSessionTime(value?: string) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  return date.toLocaleString('zh-CN', {
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

function getSessionTitle(session: ChatSession) {
  const firstUserMessage = session.messages.find((msg) => msg.role === 'user')?.content;
  const metadata = session.metadata || {};
  const questionContent = metadata.questionContent as { stem?: string } | string | undefined;
  const questionStem = typeof questionContent === 'string' ? questionContent : questionContent?.stem;
  return (questionStem || firstUserMessage || session.summary || '未命名会话').slice(0, 36);
}

function shouldPlanSaveTool(content: string) {
  return /(保存|存起来|记下来|记录一下|记录下来|沉淀|加入经验|写进|帮我记|帮我存)/.test(content);
}

function hasAutomaticSaveValue(content: string) {
  const text = content.trim();
  if (text.length < 6) return false;
  const hasEmotion = /(难过|开心|焦虑|压力|委屈|生气|害怕|失落|感动|担心|烦躁|崩溃|释然|后悔|遗憾|希望|不安|孤独|撑不住|很累|很痛苦)/.test(text);
  const hasInsight = /(明白|意识到|发现|学到|想通|感悟|反思|复盘|教训|以后|下次|原则|经验|提醒|值得记住|提醒自己)/.test(text);
  const hasFamilyMemory = /(爷爷|奶奶|外公|外婆|爸爸|妈妈|长辈|父亲|母亲|祖辈|以前|小时候|当年|经历|故事|家里|我们家|家族).{0,80}(说|讲|提醒|建议|教训|规矩|原则|踩坑|后悔|不要|一定要|值得)/.test(text);
  const hasGrowthSignal = /(孩子|儿子|女儿|孙子|孙女|小孩|本人|我).{0,80}(牙|刷牙|视力|眼睛|揉眼|体态|坐姿|驼背|睡眠|熬夜|运动|屏幕|手机|情绪|沟通|烦躁|反驳)/.test(text);
  const hasEventRecord = /(今天|昨天|最近|这次|那天|小时候|当年|以前).{0,80}(发生|遇到|聊|说|决定|选择|记录|看见|想到|感受)/.test(text);
  return hasEmotion || hasInsight || hasFamilyMemory || hasGrowthSignal || hasEventRecord;
}

function normalizeSaveCommand(content: string) {
  return content.trim().replace(/\s+/g, '');
}

function isBareSaveCommand(content: string) {
  const text = normalizeSaveCommand(content);
  return /^(请|麻烦)?(帮我)?(保存|保存一下|存起来|记下来|记录一下|记录下来|帮我记|帮我存|帮我保存|帮我记录)[。.!！?？]*$/u.test(text);
}

function isContextualSaveCommand(content: string) {
  const text = normalizeSaveCommand(content);
  if (isBareSaveCommand(text)) return true;
  return /^(请|麻烦)?(帮我)?(把)?(上面|刚才|前面|上一段|前一段|之前|刚刚)(提到的|说的|讲的)?(事情|内容|这件事|这段话|记录)?(保存|保存一下|存起来|记下来|记录下来|沉淀下来)[。.!！?？]*$/u.test(text);
}

function isSubstantiveSaveTarget(content: string) {
  const text = content.trim();
  return text.length >= 6 && !isContextualSaveCommand(text) && !isBareSaveCommand(text);
}

function findPreviousSaveTarget(messages: TutorChatMessage[]) {
  for (let index = messages.length - 1; index >= 0; index -= 1) {
    const message = messages[index];
    if (message.role !== 'user') continue;
    if (isSubstantiveSaveTarget(message.content)) return message.content.trim();
  }
  return '';
}

function buildSaveConversationContext(messages: TutorChatMessage[], current?: TutorChatMessage) {
  const contextMessages = current ? [...messages, current] : messages;
  return contextMessages
    .filter((message) => message.role === 'user' || message.role === 'assistant')
    .filter((message) => message.content.trim())
    .slice(-10)
    .map((message) => ({
      id: message.id,
      role: message.role,
      content: message.content.trim().slice(0, 900),
      timestamp: message.timestamp,
    }));
}

function fallbackDiarySavePlan(content: string): AgentSaveToolPlan {
  const cleaned = content
    .replace(/^(请|麻烦)?(帮我)?(保存|存起来|记下来|记录一下|记录下来|写进|帮我记|帮我存)[：:，,\s]*/u, '')
    .trim();
  const title = cleaned.slice(0, 24) || '对话保存的每日记录';
  return {
    should_save: true,
    tool: 'DIARY',
    content: cleaned,
    title,
    summary: cleaned.slice(0, 80),
    visibility: 'PRIVATE',
    entry_type: 'DAILY',
    memory_type: 'ELDER_ADVICE',
    scope: 'PRIVATE',
    category: 'OTHER',
    severity: 2,
    importance: 3,
    tags: ['家族Agent保存'],
    reason: '用户明确要求保存，模型未给出可执行保存类型时兜底为每日记录。',
    confirmation_message: '已保存为每日记录。',
  };
}

function shouldEvaluateAutoSave(content: string) {
  const text = content.trim();
  if (shouldPlanSaveTool(text)) return false;
  if (hasAutomaticSaveValue(text)) return true;
  if (/[?？]$/.test(text)) return false;
  return false;
}

function findLastAssistantMessageId(messages: TutorChatMessage[]) {
  for (let index = messages.length - 1; index >= 0; index -= 1) {
    if (messages[index].role === 'assistant') return messages[index].id;
  }
  return '';
}

function ageFromMetadata(metadata?: Record<string, unknown>) {
  if (!metadata) return '20岁';
  const birthDate = metadata.birthDate || metadata.birthday || metadata.dateOfBirth;
  if (typeof birthDate === 'string' && birthDate.trim()) {
    const date = new Date(birthDate);
    if (!Number.isNaN(date.getTime())) {
      const now = new Date();
      let age = now.getFullYear() - date.getFullYear();
      const monthDelta = now.getMonth() - date.getMonth();
      if (monthDelta < 0 || (monthDelta === 0 && now.getDate() < date.getDate())) age -= 1;
      if (age >= 0 && age <= 130) return `${age}岁`;
    }
  }
  const birthYear = Number(metadata.birthYear || metadata.yearOfBirth);
  if (Number.isFinite(birthYear)) {
    const age = new Date().getFullYear() - birthYear;
    if (age >= 0 && age <= 130) return `${age}岁`;
  }
  return '20岁';
}

function savedMemoryFromPlan(plan: AgentSaveToolPlan, savedAt: string): SessionSavedMemory | null {
  if (!plan.should_save || plan.tool === 'NONE' || !plan.content?.trim()) return null;
  return {
    id: `session-saved-${savedAt}-${plan.tool}`,
    tool: plan.tool,
    label: toolLabel(plan.tool),
    title: plan.title || toolLabel(plan.tool),
    content: plan.content.trim(),
    visibility: String(plan.visibility || plan.scope || 'PRIVATE'),
    savedAt,
    reason: plan.reason,
  };
}

function savedMemoryHref(plan: AgentSaveToolPlan, familyId?: number | null) {
  const familyQuery = familyId ? `?familyId=${familyId}` : '';
  if (plan.tool === 'DIARY') return `/dashboard/diary${familyQuery}`;
  if (plan.tool === 'FAMILY_MEMORY') return `/dashboard/heritage${familyQuery}`;
  if (plan.tool === 'GROWTH_GUARD') return `/dashboard/growth${familyQuery}`;
  return `/dashboard/memory${familyQuery}`;
}

function followUpPrompt(plan: AgentSaveToolPlan) {
  if (plan.tool === 'FAMILY_MEMORY') return `继续补充这条经验沉淀的背景：${plan.title}`;
  if (plan.tool === 'GROWTH_GUARD') return `继续补充这条成长观察的时间、对象和后续变化：${plan.title}`;
  return `继续补充这条每日记录的时间、地点、人物和后来影响：${plan.title}`;
}

export default function TutorPage() {
  const searchParams = useSearchParams();
  const [profiles, setProfiles] = useState<AbilityProfile[]>([]);
  const [kpNames, setKpNames] = useState<Record<number, string>>({});
  const [sessions, setSessions] = useState<ChatSession[]>([]);
  const [isLoadingSessions, setIsLoadingSessions] = useState(false);
  const [sessionError, setSessionError] = useState('');
  const [input, setInput] = useState('');
  const [isExtractingFile, setIsExtractingFile] = useState(false);
  const [extractMessage, setExtractMessage] = useState('');
  const [planningToolMessageId, setPlanningToolMessageId] = useState<string | null>(null);
  const [activationScene, setActivationScene] = useState<ActivationSceneState | null>(null);
  const { viewerRole, activeFamilyId, activeFamily, activeMembership, setActiveFamilyId } = useViewerRole();

  const routePromptAppliedRef = useRef('');
  const fileInputRef = useRef<HTMLInputElement>(null);
  const chatEndRef = useRef<HTMLDivElement>(null);
  const sessionSavedMemoriesRef = useRef<SessionSavedMemory[]>([]);

  const user = useAuthStore((s) => s.user);
  const userId = user?.id;
  const sessionId = useChatStore((s) => s.sessionId);
  const setSessionId = useChatStore((s) => s.setSessionId);
  const setMessages = useChatStore((s) => s.setMessages);
  const setCurrentQuestion = useChatStore((s) => s.setCurrentQuestion);

  const clearSessionSavedMemories = useCallback(() => {
    sessionSavedMemoriesRef.current = [];
  }, []);

  const routePrompt = useMemo(() => searchParams.get('prompt')?.trim() || '', [searchParams]);
  const routeFamilyId = useMemo(() => {
    const value = Number(searchParams.get('familyId'));
    return Number.isFinite(value) && value > 0 ? value : null;
  }, [searchParams]);
  const viewerIdentityContext = useMemo(() => {
    const displayName = user?.nickname || user?.username || '未知用户';
    return [
      `当前对话者：${displayName}`,
      `账号身份：${user?.role || 'USER'}`,
      `当前对话者年龄：${ageFromMetadata(user?.metadata)}`,
      `当前家族：${activeFamily?.name || '未选择家族'}`,
      `家族成员身份：${activeMembership?.role || 'UNKNOWN'}`,
      activeMembership?.relationshipLabel ? `与当前视角的亲属称呼：${activeMembership.relationshipLabel}` : '',
      `当前 Agent 视图：${viewerRole}`,
    ].filter(Boolean).join('\n');
  }, [activeFamily?.name, activeMembership?.relationshipLabel, activeMembership?.role, user?.metadata, user?.nickname, user?.role, user?.username, viewerRole]);

  const upsertSession = useCallback((session: ChatSession) => {
    setSessions((prev) => {
      const next = [session, ...prev.filter((item) => item.id !== session.id)];
      return next.sort((a, b) => {
        const left = new Date(a.startedAt || 0).getTime();
        const right = new Date(b.startedAt || 0).getTime();
        return right - left;
      });
    });
  }, []);

  const loadSessions = useCallback(() => {
    if (!userId) return;
    setIsLoadingSessions(true);
    setSessionError('');
    sessionApi.getUserSessions(userId, 20)
      .then((data) => setSessions(data || []))
      .catch((err: unknown) => {
        console.log('Sessions not loaded:', err);
        setSessionError('历史会话加载失败');
      })
      .finally(() => setIsLoadingSessions(false));
  }, [userId]);

  const endCurrentSession = useCallback(async (options: { resetChat?: boolean } = {}) => {
    const currentSessionId = useChatStore.getState().sessionId;
    if (!currentSessionId) {
      if (options.resetChat) {
        useChatStore.getState().reset();
        clearSessionSavedMemories();
        setInput('');
      }
      return;
    }

    try {
      const ended = await sessionApi.endSession(currentSessionId);
      upsertSession(ended);
    } catch (error) {
      console.log('Session not ended:', error);
    } finally {
      if (options.resetChat) {
        useChatStore.getState().reset();
        clearSessionSavedMemories();
        setInput('');
      }
    }
  }, [clearSessionSavedMemories, upsertSession]);

  const persistFreeChatMessages = useCallback(async (nextMessages: ChatMessage[]) => {
    if (nextMessages.length === 0) return;
    const currentSessionId = useChatStore.getState().sessionId;
    const metadata = {
      mode: 'chat',
      source: 'free_chat',
    };

    const saved = currentSessionId
      ? await sessionApi.updateMessages(currentSessionId, nextMessages)
      : await sessionApi.createSession({
          subject: 'math',
          messages: nextMessages,
          visibility: 'PRIVATE',
          source: 'TUTOR_CHAT',
          metadata,
        });

    setSessionId(saved.id);
    setCurrentQuestion(null);
    upsertSession({ ...saved, metadata: saved.metadata || metadata });
  }, [setSessionId, setCurrentQuestion, upsertSession]);

  const masteryMap = useMemo(() => {
    const map: Record<number, string> = {};
    for (const profile of profiles) {
      if (profile.masteryProbability < 0.30) map[profile.kpId] = 'weak';
      else if (profile.masteryProbability < 0.60) map[profile.kpId] = 'medium';
      else if (profile.masteryProbability < 0.85) map[profile.kpId] = 'strong';
      else map[profile.kpId] = 'excellent';
    }
    return map;
  }, [profiles]);

  const executeSavePlan = useCallback(async (plan: AgentSaveToolPlan, originalContent: string) => {
    const safePlan = normalizeSaveToolPlan(plan);
    if (!safePlan.should_save || safePlan.tool === 'NONE') {
      throw new Error('这条内容没有可执行的保存方案。');
    }
    if (!activeFamilyId) {
      throw new Error('请先选择或创建一个家族空间，再保存为家族长期记忆。');
    }

    const savedAt = new Date().toISOString();
    const auditMetadata = saveMemorySkillMetadata(safePlan, savedAt);
    let skillRunId: number | null = null;
    try {
      const skillRun = await skillRunApi.create({
        familyId: activeFamilyId,
        skillName: 'save_memory',
        status: 'RUNNING',
        source: 'FAMILY_AGENT_CHAT',
        inputSummary: truncateAuditText(originalContent),
        saved: false,
        usedSources: [{
          sourceType: 'CHAT_MESSAGE',
          snippet: truncateAuditText(originalContent, 240),
        }],
        metadata: auditMetadata,
      });
      skillRunId = skillRun.id;
    } catch (error) {
      console.log('Skill run audit not created:', error);
    }

    const commonMetadata = {
      source: 'FAMILY_COMPANION_TOOL',
      plannedTool: safePlan.tool,
      plannedToolReason: safePlan.reason,
      savedFromFamilyChatAt: savedAt,
      recordedAt: savedAt,
      eventAt: savedAt,
      originalPrompt: originalContent.slice(0, 500),
      ...(skillRunId ? { skillRunId } : {}),
    };

    try {
      let savedRecordId: number | undefined;
      const recordType = savedRecordType(safePlan.tool);

      if (safePlan.tool === 'DIARY') {
        const record = await diaryApi.create(buildDiarySaveRequest(activeFamilyId, safePlan, commonMetadata));
        savedRecordId = record.id;
      } else if (safePlan.tool === 'FAMILY_MEMORY') {
        const record = await memoryApi.createFamilyMemory(buildFamilyMemorySaveRequest(
          activeFamilyId,
          safePlan,
          {
            ...commonMetadata,
            sourceType: 'FAMILY_EXPERIENCE',
            scenario: '家族Agent对话保存',
          },
        ));
        savedRecordId = record.id;
      } else if (safePlan.tool === 'GROWTH_GUARD') {
        const record = await growthGuardApi.createRecord(buildGrowthGuardSaveRequest(
          activeFamilyId,
          safePlan,
          todayString(),
          {
            ...commonMetadata,
            sourceType: 'GROWTH_OBSERVATION',
            followUpStatus: 'PENDING',
          },
        ));
        savedRecordId = record.id;
      }

      if (skillRunId) {
        try {
          await skillRunApi.update(skillRunId, {
            status: 'SUCCEEDED',
            saved: true,
            outputSummary: `已保存为${toolLabel(safePlan.tool)}：${safePlan.title || safePlan.content.slice(0, 24)}`,
            metadata: {
              ...auditMetadata,
              savedRecordId,
              savedRecordType: recordType,
              savedAt,
            },
          });
        } catch (error) {
          console.log('Skill run audit not updated after save:', error);
        }
      }

      const sessionMemory = savedMemoryFromPlan(safePlan, savedAt);
      if (sessionMemory) {
        sessionSavedMemoriesRef.current = [
          ...sessionSavedMemoriesRef.current.filter((item) => item.content !== sessionMemory.content),
          sessionMemory,
        ].slice(-8);
      }
    } catch (error) {
      if (skillRunId) {
        try {
          await skillRunApi.update(skillRunId, {
            status: 'FAILED',
            saved: false,
            outputSummary: error instanceof Error
              ? truncateAuditText(error.message, 500)
              : '保存失败',
            metadata: {
              ...auditMetadata,
              failureReason: error instanceof Error ? error.message : '保存失败',
              savedRecordType: savedRecordType(safePlan.tool),
            },
          });
        } catch (auditError) {
          console.log('Skill run audit not updated after save failure:', auditError);
        }
      }
      throw error;
    }
  }, [activeFamilyId]);

  const createSavePlanForMessage = useCallback(async (messageId: string, content: string) => {
    if (!activeFamilyId) {
      setMessages((useChatStore.getState().messages as TutorChatMessage[]).map((message) => (
        message.id === messageId
          ? {
              ...message,
              saveSuggestion: undefined,
              content: `${message.content}\n\n这段内容看起来值得保存，但你还没有选择家族空间。请先创建或切换到一个家族后再保存。`,
            }
          : message
      )));
      return;
    }

    setPlanningToolMessageId(messageId);
    try {
      const conversationContext = buildSaveConversationContext(useChatStore.getState().messages as TutorChatMessage[]);
      const planResult = await memoryApi.planSaveTool({
        message: content,
        familyContext: '家族Agent主动沉淀建议',
        conversationContext,
        viewerRole,
      });
      const plan = normalizeSaveToolPlan(planResult.data);
      if (plan.should_save && plan.tool !== 'NONE') {
        await executeSavePlan(plan, content);
      }
      setMessages((useChatStore.getState().messages as TutorChatMessage[]).map((message) => (
        message.id === messageId
          ? {
              ...message,
              saveSuggestion: undefined,
              content: plan.should_save && plan.tool !== 'NONE'
                ? `${message.content}\n\n${plan.confirmation_message || `已保存为${toolLabel(plan.tool)}。`}`
                : message.content,
              toolResult: plan.should_save && plan.tool !== 'NONE'
                ? {
                    label: toolLabel(plan.tool),
                    detail: savePlanDetail(plan),
                    memoryHref: savedMemoryHref(plan, activeFamilyId),
                    followUpPrompt: followUpPrompt(plan),
                  }
                : undefined,
            }
          : message
      )));
    } catch (error) {
      setMessages((useChatStore.getState().messages as TutorChatMessage[]).map((message) => (
        message.id === messageId
          ? {
              ...message,
              saveSuggestion: undefined,
              content: error instanceof Error
                ? `${message.content}\n\n保存失败：${error.message}`
                : `${message.content}\n\n保存失败，请稍后重试。`,
            }
          : message
      )));
    } finally {
      setPlanningToolMessageId(null);
    }
  }, [activeFamilyId, executeSavePlan, setMessages, viewerRole]);

  const planSaveFromFreeChat = useCallback(async (content: string) => {
    if (!shouldPlanSaveTool(content)) return false;

    const now = new Date().toISOString();
    const previous = useChatStore.getState().messages as TutorChatMessage[];
    const usesPreviousContext = isContextualSaveCommand(content);
    const saveTarget = usesPreviousContext ? findPreviousSaveTarget(previous) : content.trim();
    const userMessage: TutorChatMessage = {
      id: `u-save-${Date.now()}`,
      role: 'user',
      content,
      timestamp: now,
    };
    const assistantMessage: TutorChatMessage = {
      id: `a-save-${Date.now()}`,
      role: 'assistant',
      content: '我在判断这条内容适合保存成哪类家族记忆...',
      timestamp: now,
    };
    setMessages([...previous, userMessage, assistantMessage]);

    if (!saveTarget) {
      setMessages([
        ...previous,
        userMessage,
        {
          ...assistantMessage,
          content: '我没有找到上一段可保存的内容。你可以先说出要保存的事情，再发“保存”。',
        },
      ]);
      return true;
    }

    if (!activeFamilyId) {
      setMessages([
        ...previous,
        userMessage,
        {
          ...assistantMessage,
          content: '这条内容看起来值得保存，但你还没有选择家族空间。请先创建或切换到一个家族后再保存。',
        },
      ]);
      return true;
    }

    try {
      const planResult = await memoryApi.planSaveTool({
        message: saveTarget,
        familyContext: '家族Agent自由对话',
        conversationContext: buildSaveConversationContext(previous, userMessage),
        viewerRole,
      });
      const rawPlan = normalizeSaveToolPlan(planResult.data);
      const plan = rawPlan.should_save && rawPlan.tool !== 'NONE'
        ? rawPlan
        : normalizeSaveToolPlan(fallbackDiarySavePlan(saveTarget));
      await executeSavePlan(plan, saveTarget);

      setMessages([
        ...previous,
        userMessage,
        {
          ...assistantMessage,
          content: usesPreviousContext
            ? `已把上一段内容保存为${toolLabel(plan.tool)}。`
            : plan.confirmation_message || `已自动保存为${toolLabel(plan.tool)}。`,
          toolResult: {
            label: toolLabel(plan.tool),
            detail: savePlanDetail(plan),
            memoryHref: savedMemoryHref(plan, activeFamilyId),
            followUpPrompt: followUpPrompt(plan),
          },
        },
      ]);
      return true;
    } catch (error) {
      setMessages([
        ...previous,
        userMessage,
        {
          ...assistantMessage,
          content: error instanceof Error
            ? `我判断这条内容可能适合保存，但生成保存方案失败：${error.message}`
            : '我判断这条内容可能适合保存，但生成保存方案失败。',
        },
      ]);
      return true;
    }
  }, [activeFamilyId, executeSavePlan, setMessages, viewerRole]);

  const { messages, isStreaming, sendFreeMessage } = useChat({
    getMastery: (kpId) => masteryMap[kpId] || 'medium',
    getKnowledgePoint: (kpId) => kpNames[kpId] || '',
    viewerRole,
    targetRole: 'STUDENT',
    activeFamilyId,
    viewerIdentityContext,
    persistChatMessages: persistFreeChatMessages,
    onActivationSceneChange: setActivationScene,
    getSessionSavedMemories: () => sessionSavedMemoriesRef.current,
    onFreeChatDone: (message) => {
      if (!shouldEvaluateAutoSave(message)) return;
      const assistantMessageId = findLastAssistantMessageId(useChatStore.getState().messages as TutorChatMessage[]);
      if (!assistantMessageId) return;
      setMessages((useChatStore.getState().messages as TutorChatMessage[]).map((item) => (
        item.id === assistantMessageId && !item.toolResult
          ? { ...item, saveSuggestion: { originalContent: message } }
          : item
      )));
    },
  });

  const dismissSaveSuggestion = useCallback((messageId: string) => {
    setMessages((useChatStore.getState().messages as TutorChatMessage[]).map((message) => (
      message.id === messageId
        ? { ...message, saveSuggestion: undefined }
        : message
    )));
  }, [setMessages]);

  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  useEffect(() => {
    if (routeFamilyId && routeFamilyId !== activeFamilyId) {
      clearSessionSavedMemories();
      setActiveFamilyId(routeFamilyId);
    }
  }, [activeFamilyId, clearSessionSavedMemories, routeFamilyId, setActiveFamilyId]);

  useEffect(() => {
    if (!routePrompt || routePromptAppliedRef.current === routePrompt) return;
    routePromptAppliedRef.current = routePrompt;
    setInput(routePrompt);
    setCurrentQuestion(null);
  }, [routePrompt, setCurrentQuestion]);

  useEffect(() => {
    loadSessions();
  }, [loadSessions]);

  useEffect(() => {
    if (userId) {
      assessmentApi.getProfiles(userId)
        .then((data) => setProfiles(data || []))
        .catch((err: unknown) => { console.log('Profiles not loaded:', err); });
    }

    questionApi.getKnowledgeTree()
      .then((tree) => {
        const names: Record<number, string> = {};
        const flatten = (nodes: KnowledgePoint[]) => {
          for (const node of nodes) {
            names[node.id] = node.name;
            if (node.children) flatten(node.children);
          }
        };
        flatten(tree || []);
        setKpNames(names);
      })
      .catch((err: unknown) => { console.log('KP names not loaded:', err); });
  }, [userId]);

  const handleSend = async () => {
    if (!input.trim() || isStreaming) return;
    const msg = input.trim();
    setInput('');

    const handledBySaveTool = await planSaveFromFreeChat(msg);
    if (handledBySaveTool) {
      setActivationScene(null);
      return;
    }
    sendFreeMessage(msg);
  };

  const handleKeyDown = (event: React.KeyboardEvent) => {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      handleSend();
    }
  };

  const handleFileUpload = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file || isStreaming || isExtractingFile) return;

    setIsExtractingFile(true);
    setExtractMessage('');

    try {
      const result = await tutorApi.extractContent(file);
      const extracted = result.data;
      if (!extracted.supported) {
        setExtractMessage(extracted.message);
        return;
      }
      const extractedText = extracted.structuredText || extracted.text;
      const prompt = `我上传了文件「${extracted.filename}」。请先核对解析内容，再用适合学生的方式讲解或整理：\n\n${extractedText}`;
      setInput((prev) => (prev.trim() ? `${prev.trim()}\n\n${prompt}` : prompt));
      setExtractMessage(extracted.message);
    } catch (error) {
      console.log('File extraction failed:', error);
      const message = error instanceof Error ? error.message : '';
      setExtractMessage(message ? `文件解析失败：${message}` : '文件解析失败，请稍后重试。');
    } finally {
      setIsExtractingFile(false);
      if (event.target) event.target.value = '';
    }
  };

  const startNewSession = () => {
    setActivationScene(null);
    clearSessionSavedMemories();
    void endCurrentSession({ resetChat: true });
  };

  const restoreSession = async (session: ChatSession) => {
    if (isStreaming) return;
    const currentSessionId = useChatStore.getState().sessionId;
    if (currentSessionId && currentSessionId !== session.id) {
      await endCurrentSession();
    }
    const detail = await sessionApi.getSession(session.id);
    clearSessionSavedMemories();
    setSessionId(detail.status === 'ACTIVE' ? detail.id : null);
    setMessages(detail.messages || []);
    setCurrentQuestion(null);
    setActivationScene(null);
    upsertSession(detail);
  };

  const deleteSession = async (session: ChatSession, event: React.MouseEvent) => {
    event.stopPropagation();
    if (isStreaming) return;
    await sessionApi.deleteSession(session.id);
    setSessions((prev) => prev.filter((item) => item.id !== session.id));
    if (sessionId === session.id) {
      useChatStore.getState().reset();
      clearSessionSavedMemories();
      setInput('');
    }
  };

  const renderSessionList = (compact = false) => (
    <div className="space-y-1">
      {sessions.map((session) => (
        <button
          type="button"
          key={session.id}
          onClick={() => { void restoreSession(session); }}
          disabled={isStreaming}
          className={`group w-full rounded-lg px-3 py-2 text-left transition-colors disabled:opacity-60 ${
            sessionId === session.id ? 'bg-blue-50 text-blue-700' : 'text-gray-700 hover:bg-gray-50'
          }`}
        >
          <div className="flex items-center gap-2">
            <div className="min-w-0 flex-1 truncate text-xs font-medium">{getSessionTitle(session)}</div>
            {!compact && (
              <span
                role="button"
                tabIndex={0}
                onClick={(event) => { void deleteSession(session, event); }}
                onKeyDown={(event) => {
                  if (event.key === 'Enter' || event.key === ' ') {
                    void deleteSession(session, event as unknown as React.MouseEvent);
                  }
                }}
                className="flex h-6 w-6 items-center justify-center rounded-md text-gray-400 opacity-0 hover:bg-red-50 hover:text-red-600 group-hover:opacity-100"
                title="删除会话"
              >
                <Trash2 className="h-3.5 w-3.5" />
              </span>
            )}
          </div>
          <div className="mt-1 text-[10px] text-gray-400">
            <span>{formatSessionTime(session.startedAt)}</span>
          </div>
        </button>
      ))}
    </div>
  );

  return (
    <div className="mx-auto flex h-[calc(100dvh-8rem)] max-w-7xl flex-col sm:h-[calc(100dvh-11rem)] lg:h-[calc(100vh-8rem)]">
      <div className="mb-2 flex shrink-0 flex-col gap-2 sm:mb-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="min-w-0">
          <h1 className="text-lg font-bold text-gray-900 sm:text-xl">家族Agent</h1>
          <p className="text-xs text-gray-500">学习陪伴 · 家族上下文陪伴</p>
        </div>
        <div className="flex w-full flex-wrap gap-2 sm:w-auto sm:justify-end">
          {messages.length > 0 && (
            <button
              type="button"
              onClick={startNewSession}
              className="rounded-lg border border-gray-200 bg-white px-3 py-2 text-xs text-gray-600 hover:bg-gray-50"
            >
              结束并新建
            </button>
          )}
        </div>
      </div>

      <details className="mb-2 shrink-0 overflow-hidden rounded-xl border border-gray-200 bg-white sm:mb-3 lg:hidden">
        <summary className="flex cursor-pointer list-none items-center gap-2 px-3 py-2 text-sm font-medium text-gray-800">
          <History className="h-4 w-4" />
          最近会话
          <span className="ml-auto text-xs text-gray-400">{sessions.length}</span>
        </summary>
        <div className="max-h-56 overflow-y-auto border-t border-gray-100 p-2">
          {isLoadingSessions ? (
            <div className="flex h-16 items-center justify-center text-gray-400">
              <Loader2 className="h-4 w-4 animate-spin" />
            </div>
          ) : sessionError ? (
            <div className="px-3 py-3 text-xs text-red-500">{sessionError}</div>
          ) : sessions.length === 0 ? (
            <div className="px-3 py-3 text-xs text-gray-400">暂无历史会话</div>
          ) : renderSessionList(true)}
        </div>
      </details>

      <div className="flex min-h-0 flex-1 gap-3 overflow-hidden">
        <aside className="hidden w-72 shrink-0 flex-col overflow-hidden rounded-xl border border-gray-200 bg-white lg:flex">
          <div className="flex items-center justify-between border-b border-gray-100 px-3 py-2">
            <div className="flex items-center gap-2 text-sm font-medium text-gray-800">
              <History className="h-4 w-4" />
              最近会话
            </div>
            <button
              type="button"
              onClick={startNewSession}
              disabled={isStreaming}
              className="flex h-7 w-7 items-center justify-center rounded-md border border-gray-200 text-gray-500 hover:bg-gray-50 disabled:opacity-50"
              title="结束当前会话并新建"
            >
              <Plus className="h-4 w-4" />
            </button>
          </div>
          <div className="flex-1 overflow-y-auto p-2">
            {isLoadingSessions ? (
              <div className="flex h-24 items-center justify-center text-gray-400">
                <Loader2 className="h-4 w-4 animate-spin" />
              </div>
            ) : sessionError ? (
              <div className="px-3 py-4 text-xs text-red-500">{sessionError}</div>
            ) : sessions.length === 0 ? (
              <div className="flex h-full items-center justify-center text-center text-gray-400">
                <div>
                  <MessageSquareText className="mx-auto mb-2 h-8 w-8 opacity-30" />
                  <p className="text-xs">暂无历史会话</p>
                </div>
              </div>
            ) : renderSessionList()}
          </div>
        </aside>

        <div className="flex h-full min-w-0 flex-1 flex-col overflow-hidden rounded-xl border border-gray-200 bg-white">
          <div className="flex-1 space-y-3 overflow-y-auto p-2.5 sm:space-y-4 sm:p-4">
            {messages.length === 0 ? (
              <div className="flex h-full items-center justify-center">
                <div className="text-center text-gray-400">
                  <FileText className="mx-auto mb-2 h-12 w-12 opacity-30" />
                  <p className="text-sm">
                    可以聊学习计划、卡点、情绪、经验沉淀或一道具体题目
                  </p>
                  <p className="mt-1 text-xs">
                    我会结合可见的每日记录、经验沉淀和成长观察摘要来回应。
                  </p>
                </div>
              </div>
            ) : (
              messages.map((rawMsg) => {
                const msg = rawMsg as TutorChatMessage;
                return (
                  <div
                    key={msg.id}
                    className={`flex gap-1.5 sm:gap-3 ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}
                  >
                    {msg.role === 'assistant' && (
                      <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-blue-600 text-xs font-medium text-white">
                        AI
                      </div>
                    )}
                    <div
                      className={`max-w-[90%] overflow-hidden rounded-2xl px-3 py-2.5 text-sm leading-relaxed whitespace-pre-wrap sm:max-w-[80%] sm:px-4 ${
                        msg.role === 'user'
                          ? 'rounded-br-md bg-blue-600 text-white'
                          : 'rounded-bl-md bg-gray-100 text-gray-900'
                      } ${msg.role === 'assistant' && !msg.content ? 'animate-pulse' : ''}`}
                    >
                      {msg.content ? <MathRenderer content={msg.content} /> : (msg.role === 'assistant' ? '思考中...' : '')}
                      {msg.role === 'assistant' && <WebSearchBadge metadata={msg.metadata} />}
                      {msg.role === 'assistant' && <RagMemoryBadge metadata={msg.metadata} />}
                      {msg.role === 'assistant' && msg.toolResult && (
                        <div className="mt-3 rounded-lg border border-green-100 bg-white/80 p-3 text-xs text-gray-600">
                          <div className="mb-1 flex items-center gap-2 font-medium text-green-700">
                            <CheckCircle className="h-3.5 w-3.5" />
                            已保存为{msg.toolResult.label}
                          </div>
                          <div className="text-green-600">{msg.toolResult.detail}</div>
                          <p className="mt-2 leading-5 text-gray-500">
                            这条内容已保存，并已排队生成长期索引；当前会话会立即参考它，索引完成后家族Agent 和镜像 Agent 会在权限允许时召回。
                          </p>
                          <div className="mt-3 flex flex-wrap gap-2">
                            {msg.toolResult.memoryHref && (
                              <Link
                                href={msg.toolResult.memoryHref}
                                className="inline-flex h-8 items-center rounded-lg border border-green-100 bg-green-50 px-3 text-xs font-medium text-green-700 hover:bg-green-100"
                              >
                                查看家族知识库
                              </Link>
                            )}
                            {msg.toolResult.followUpPrompt && (
                              <button
                                type="button"
                                onClick={() => setInput(msg.toolResult!.followUpPrompt || '')}
                                className="inline-flex h-8 items-center rounded-lg border border-gray-200 bg-white px-3 text-xs font-medium text-gray-600 hover:bg-gray-50"
                              >
                                继续补充细节
                              </button>
                            )}
                          </div>
                        </div>
                      )}
                      {msg.role === 'assistant' && msg.saveSuggestion && !msg.toolResult && (
                        <div className="mt-3 rounded-lg border border-blue-100 bg-white/80 p-3 text-xs text-gray-700">
                          <div className="font-medium text-blue-700">这段内容以后可能有价值</div>
                          <p className="mt-1 leading-5 text-gray-500">
                            要不要让我整理成一条每日记录、经验沉淀或成长观察？
                          </p>
                          <div className="mt-3 flex flex-wrap gap-2">
                            <button
                              type="button"
                              onClick={() => {
                                void createSavePlanForMessage(msg.id, msg.saveSuggestion!.originalContent);
                              }}
                              disabled={planningToolMessageId === msg.id}
                              className="inline-flex h-8 items-center gap-1.5 rounded-lg bg-blue-600 px-3 text-xs font-medium text-white hover:bg-blue-700 disabled:opacity-60"
                            >
                              {planningToolMessageId === msg.id ? (
                                <Loader2 className="h-3.5 w-3.5 animate-spin" />
                              ) : (
                                <CheckCircle className="h-3.5 w-3.5" />
                              )}
                              整理并保存
                            </button>
                            <button
                              type="button"
                              onClick={() => dismissSaveSuggestion(msg.id)}
                              disabled={planningToolMessageId === msg.id}
                              className="inline-flex h-8 items-center gap-1.5 rounded-lg border border-gray-200 bg-white px-3 text-xs font-medium text-gray-600 hover:bg-gray-50 disabled:opacity-60"
                            >
                              <XCircle className="h-3.5 w-3.5" />
                              暂不保存
                            </button>
                          </div>
                        </div>
                      )}
                    </div>
                    {msg.role === 'user' && (
                      <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-gray-300 text-xs font-medium text-gray-600">
                        U
                      </div>
                    )}
                  </div>
                );
              })
            )}
            <div ref={chatEndRef} />
          </div>

          <div className="border-t border-gray-200 p-1.5 pb-[max(env(safe-area-inset-bottom),0.375rem)] sm:p-3">
            {routePrompt && input === routePrompt && (
              <div className="mb-2 flex flex-wrap items-center gap-2 rounded-lg border border-blue-100 bg-blue-50 px-3 py-2 text-xs text-blue-700">
                <FileText className="h-3.5 w-3.5" />
                <span className="min-w-0 flex-1">
                  已从家族知识库带入回流测试问题，确认后发送给家族Agent。
                </span>
                <button
                  type="button"
                  onClick={() => setInput('')}
                  className="inline-flex h-6 items-center rounded-md bg-white px-2 text-[11px] font-medium text-blue-700 ring-1 ring-blue-100 hover:bg-blue-100"
                >
                  清空
                </button>
              </div>
            )}
            {activationScene && (
              <div className="mb-2 flex items-center gap-2 rounded-lg border border-amber-100 bg-amber-50 px-3 py-2 text-xs text-amber-800">
                <Sparkles className="h-3.5 w-3.5 shrink-0" />
                <span className="shrink-0 font-medium">已激活：{activationScene.label}</span>
                <span className="min-w-0 flex-1 truncate text-amber-700">{activationScene.instruction}</span>
                <button
                  type="button"
                  onClick={() => setActivationScene(null)}
                  className="inline-flex h-6 shrink-0 items-center rounded-md bg-white px-2 text-[11px] font-medium text-amber-700 ring-1 ring-amber-100 hover:bg-amber-100"
                >
                  隐藏
                </button>
              </div>
            )}
            <form
              onSubmit={(event) => { event.preventDefault(); handleSend(); }}
              className="flex items-end gap-1.5 sm:gap-2"
            >
              <input
                name="studyFile"
                ref={fileInputRef}
                type="file"
                accept=".txt,.md,.markdown,.csv,.json,.tex,.pdf,.docx,image/*"
                onChange={(event) => { void handleFileUpload(event); }}
                className="hidden"
              />
              <button
                type="button"
                onClick={() => fileInputRef.current?.click()}
                disabled={isStreaming || isExtractingFile}
                className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border border-gray-200 text-gray-500 hover:bg-gray-50 disabled:opacity-50 sm:h-10 sm:w-10"
                title="上传题目或学习资料"
              >
                {isExtractingFile ? <Loader2 className="h-4 w-4 animate-spin" /> : <Paperclip className="h-4 w-4" />}
              </button>
              <textarea
                name="tutorMessage"
                rows={1}
                value={input}
                onChange={(event) => setInput(event.target.value)}
                onKeyDown={handleKeyDown}
                placeholder="聊学习计划、卡点、情绪、经验沉淀，或直接发一道题..."
                disabled={isStreaming || isExtractingFile}
                className="min-h-9 max-h-28 min-w-0 flex-1 resize-none overflow-y-auto rounded-lg border border-gray-200 px-2.5 py-2 text-sm outline-none focus:border-transparent focus:ring-2 focus:ring-blue-500 disabled:bg-gray-50 sm:min-h-10 sm:max-h-32 sm:px-4"
              />
              <button
                type="submit"
                disabled={!input.trim() || isStreaming || isExtractingFile}
                className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-blue-600 text-white transition-colors hover:bg-blue-700 disabled:opacity-50 sm:h-10 sm:w-auto sm:gap-1 sm:px-4"
                aria-label="发送"
              >
                {isStreaming ? <Loader2 className="h-4 w-4 animate-spin" /> : <Send className="h-4 w-4" />}
              </button>
            </form>
            {extractMessage && (
              <p className="mt-2 text-xs text-gray-500">{extractMessage}</p>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
