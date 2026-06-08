/**
 * 家族Agent 聊天 Hook
 *
 * @param getMastery - 根据知识点ID获取掌握等级（可选，默认返回'中'）
 * @param getKnowledgePoint - 根据知识点ID获取知识点名称（可选，默认返回空）
 */
'use client';

import { useCallback } from 'react';
import { useChatStore } from '@/stores/chatStore';
import { growthGuardApi, heritageTaskApi, memoryApi, memoryLibraryApi, tutorApi } from '@/lib/api';
import type { ChatMessage, DiaryEntry, GrowthGuardRecord, HeritageTask, MemoryEntry, MemoryLibraryItem, Question } from '@/types';
import type { ViewerRole } from '@/lib/roles';

type MemoryContextResult = {
  context: string;
  metadata?: NonNullable<ChatMessage['metadata']>;
};

export type SessionSavedMemory = {
  id: string;
  tool: 'DIARY' | 'FAMILY_MEMORY' | 'GROWTH_GUARD';
  label: string;
  title: string;
  content: string;
  visibility?: string;
  savedAt: string;
  reason?: string;
};

interface UseChatOptions {
  /** 根据 kpId 获取学生对该知识点的掌握等级 */
  getMastery?: (kpId: number) => string;
  /** 根据 kpId 获取知识点名称 */
  getKnowledgePoint?: (kpId: number) => string;
  /** 讲题风格：引导式或快速答案式 */
  teachingStyle?: 'guided' | 'direct';
  getTeachingStyle?: () => 'guided' | 'direct';
  viewerRole?: ViewerRole;
  targetRole?: ViewerRole | 'STUDENT';
  activeFamilyId?: number | null;
  persistMessages?: (messages: ChatMessage[], question: Question) => Promise<void> | void;
  persistChatMessages?: (messages: ChatMessage[]) => Promise<void> | void;
  onFreeChatDone?: (message: string) => void;
  onActivationSceneChange?: (scene: { label: string; instruction: string } | null) => void;
  viewerIdentityContext?: string;
  getSessionSavedMemories?: () => SessionSavedMemory[];
}

export function useChat(options: UseChatOptions = {}) {
  const {
    messages,
    isStreaming,
    currentQuestion,
    addMessage,
    appendToLastMessage,
    mergeLastAssistantMetadata,
    setStreaming,
    setCurrentQuestion,
    reset,
  } = useChatStore();

  const {
    getMastery = () => '中',
    getKnowledgePoint = () => '',
    teachingStyle = 'guided',
    getTeachingStyle,
    viewerRole = 'STUDENT',
    targetRole = 'STUDENT',
    activeFamilyId,
    persistMessages,
    persistChatMessages,
    onFreeChatDone,
    onActivationSceneChange,
    viewerIdentityContext,
    getSessionSavedMemories,
  } = options;

  const persistSafely = useCallback(
    (question: Question) => {
      void Promise.resolve(persistMessages?.(useChatStore.getState().messages, question))
        .catch((error: unknown) => {
          console.log('Chat session not persisted:', error);
        });
    },
    [persistMessages],
  );

  const persistChatSafely = useCallback(
    () => {
      void Promise.resolve(persistChatMessages?.(useChatStore.getState().messages))
        .catch((error: unknown) => {
          console.log('Chat session not persisted:', error);
        });
    },
    [persistChatMessages],
  );

  const makeBody = useCallback(
    (question: Question, studentMessage: string) => {
      const history = messages
        .filter((m) => m.role !== 'system')
        .map((m) => ({ role: m.role, content: m.content }));

      return {
        questionContent: question.content.stem,
        answer: question.answer.value,
        steps: question.answer.steps?.join('\n') || '',
        studentMessage,
        history,
        subject: question.subject,
        grade: question.grade,
        knowledgePoint: getKnowledgePoint(question.kpId),
        masteryLevel: getMastery(question.kpId),
        teachingStyle: getTeachingStyle?.() || teachingStyle,
        viewerRole,
        targetRole,
      };
    },
    [messages, getMastery, getKnowledgePoint, getTeachingStyle, teachingStyle, viewerRole, targetRole],
  );

  const recallMemoryContext = useCallback(
    async (params: {
      query: string;
      subject?: string;
      knowledgePointId?: number;
    }) => {
      try {
        const allowFamilyContext = shouldRecallFamilyContext(params.query);
        const activationScene = allowFamilyContext ? detectFamilyActivationScene(params.query) : null;
        onActivationSceneChange?.(activationScene
          ? { label: activationScene.label, instruction: activationScene.instruction }
          : null);
        const libraryKeyword = activationScene
          ? `${params.query} ${activationScene.label} ${activationScene.searchKeywords.join(' ')}`
          : params.query;
        const [familyRecall, libraryResult, growthRecords, heritageTasks] = await Promise.all([
          activeFamilyId && allowFamilyContext
            ? memoryApi.recallFamily(activeFamilyId, {
                query: libraryKeyword,
                scene: 'FAMILY_AGENT',
                diaryLimit: 8,
                memoryLimit: 8,
              }).catch(() => null)
            : Promise.resolve(null),
          activeFamilyId && allowFamilyContext
            ? memoryLibraryApi.search({
                familyId: activeFamilyId,
                keyword: libraryKeyword,
                pageSize: 12,
              }).catch(() => null)
            : Promise.resolve(null),
          activeFamilyId && allowFamilyContext
            ? growthGuardApi.listFamilyRecords(activeFamilyId, 8).catch(() => [] as GrowthGuardRecord[])
            : Promise.resolve([] as GrowthGuardRecord[]),
          activeFamilyId && allowFamilyContext
            ? heritageTaskApi.listFamilyTasks(activeFamilyId, 8).catch(() => [] as HeritageTask[])
            : Promise.resolve([] as HeritageTask[]),
        ]);
        const libraryItems = libraryResult?.items || [];
        if (!allowFamilyContext) {
          return { context: '' } satisfies MemoryContextResult;
        }
        const context = formatMemoryContext({
          libraryItems,
          familyMemories: familyRecall?.memories || [],
          diaryEntries: familyRecall?.diaries || [],
          growthRecords: familyRecall?.growthRecords?.length ? familyRecall.growthRecords : growthRecords,
          heritageTasks,
          sessionSavedMemories: getSessionSavedMemories?.() || [],
          retrievalMode: familyRecall?.retrievalMode,
          embeddingReadyCount: familyRecall?.embeddingReadyCount,
          viewerRole,
          viewerIdentityContext,
          activationScene,
        });
        return {
          context,
          metadata: familyRecall
            ? {
                rag: {
                  retrievalMode: familyRecall.retrievalMode,
                  embeddingReadyCount: familyRecall.embeddingReadyCount || 0,
                  diaryCount: familyRecall.diaryCount ?? familyRecall.diaries?.length ?? 0,
                  memoryCount: familyRecall.memoryCount ?? familyRecall.memories?.length ?? 0,
                  growthRecordCount: familyRecall.growthRecordCount ?? familyRecall.growthRecords?.length ?? 0,
                  sources: familyRecall.sources || [],
                },
              }
            : undefined,
        } satisfies MemoryContextResult;
      } catch (error) {
        console.log('Family context memories not loaded:', error);
        return { context: '' } satisfies MemoryContextResult;
      }
    },
    [activeFamilyId, getSessionSavedMemories, onActivationSceneChange, viewerIdentityContext, viewerRole],
  );

  /**
   * 发送讲题请求
   */
  const askQuestion = useCallback(
    async (question: Question, studentMessage: string) => {
      if (isStreaming) return;

      setCurrentQuestion(question);
      addMessage('user', studentMessage);
      addMessage('assistant', '');
      setStreaming(true);
      const timeContext = currentTimeContext();

      const memoryContext = await recallMemoryContext({
        query: studentMessage,
        subject: question.subject,
        knowledgePointId: question.kpId || undefined,
      });
      if (memoryContext.metadata) {
        mergeLastAssistantMetadata(memoryContext.metadata);
      }

      tutorApi.explainStream(
        { ...makeBody(question, studentMessage), memoryContext: memoryContext.context, ...timeContext },
        (chunk) => appendToLastMessage(chunk),
        () => {
          setStreaming(false);
          persistSafely(question);
        },
        (error) => {
          appendToLastMessage(`\n\n[错误] ${error}`);
          setStreaming(false);
          persistSafely(question);
        },
        (metadata) => mergeLastAssistantMetadata(normalizeAssistantMetadata(metadata)),
      );
    },
    [
      isStreaming,
      makeBody,
      addMessage,
      appendToLastMessage,
      mergeLastAssistantMetadata,
      setStreaming,
      setCurrentQuestion,
      persistSafely,
      recallMemoryContext,
    ],
  );

  /**
   * 发送文本消息（在已有会话中）
   */
  const sendMessage = useCallback(
    async (message: string) => {
      if (isStreaming || !currentQuestion) return;

      addMessage('user', message);
      addMessage('assistant', '');
      setStreaming(true);
      const timeContext = currentTimeContext();

      const memoryContext = await recallMemoryContext({
        query: message,
        subject: currentQuestion.subject,
        knowledgePointId: currentQuestion.kpId || undefined,
      });
      if (memoryContext.metadata) {
        mergeLastAssistantMetadata(memoryContext.metadata);
      }

      tutorApi.explainStream(
        { ...makeBody(currentQuestion, message), memoryContext: memoryContext.context, ...timeContext },
        (chunk) => appendToLastMessage(chunk),
        () => {
          setStreaming(false);
          persistSafely(currentQuestion);
        },
        (error) => {
          appendToLastMessage(`\n\n[错误] ${error}`);
          setStreaming(false);
          persistSafely(currentQuestion);
        },
        (metadata) => mergeLastAssistantMetadata(normalizeAssistantMetadata(metadata)),
      );
    },
    [
      isStreaming,
      currentQuestion,
      makeBody,
      addMessage,
      appendToLastMessage,
      mergeLastAssistantMetadata,
      setStreaming,
      persistSafely,
      recallMemoryContext,
    ],
  );

  /**
   * 发送自由对话消息（不绑定某一道题）
   */
  const sendFreeMessage = useCallback(
    async (message: string) => {
      if (isStreaming) return;

      const history = useChatStore.getState().messages
        .filter((m) => m.role !== 'system')
        .map((m) => ({ role: m.role, content: m.content }));

      addMessage('user', message);
      addMessage('assistant', '');
      setStreaming(true);
      const timeContext = currentTimeContext();

      const memoryContext = await recallMemoryContext({
        query: message,
        subject: 'family',
      });
      if (memoryContext.metadata) {
        mergeLastAssistantMetadata(memoryContext.metadata);
      }

      tutorApi.explainStream(
        {
          questionContent: '',
          answer: '',
          steps: '',
          studentMessage: message,
          history,
          subject: '家族Agent',
          grade: '',
          knowledgePoint: '家族记忆',
          masteryLevel: '中',
          teachingStyle: getTeachingStyle?.() || teachingStyle,
          mode: 'chat',
          memoryContext: memoryContext.context,
          viewerRole,
          targetRole,
          ...timeContext,
        },
        (chunk) => appendToLastMessage(chunk),
        () => {
          setStreaming(false);
          onFreeChatDone?.(message);
          persistChatSafely();
        },
        (error) => {
          appendToLastMessage(`\n\n[错误] ${error}`);
          setStreaming(false);
          persistChatSafely();
        },
        (metadata) => mergeLastAssistantMetadata(normalizeAssistantMetadata(metadata)),
      );
    },
    [
      isStreaming,
      addMessage,
      appendToLastMessage,
      mergeLastAssistantMetadata,
      setStreaming,
      getTeachingStyle,
      teachingStyle,
      viewerRole,
      targetRole,
      onFreeChatDone,
      persistChatSafely,
      recallMemoryContext,
    ],
  );

  return {
    messages,
    isStreaming,
    currentQuestion,
    askQuestion,
    sendMessage,
    sendFreeMessage,
    reset,
  };
}

function formatMemoryContext({
  libraryItems,
  familyMemories,
  diaryEntries,
  growthRecords,
  heritageTasks,
  sessionSavedMemories,
  retrievalMode,
  embeddingReadyCount,
  viewerRole,
  viewerIdentityContext,
  activationScene,
}: {
  libraryItems: MemoryLibraryItem[];
  familyMemories: MemoryEntry[];
  diaryEntries: DiaryEntry[];
  growthRecords: GrowthGuardRecord[];
  heritageTasks: HeritageTask[];
  sessionSavedMemories: SessionSavedMemory[];
  retrievalMode?: string;
  embeddingReadyCount?: number;
  viewerRole: ViewerRole;
  viewerIdentityContext?: string;
  activationScene: FamilyActivationScene | null;
}): string {
  const sections: string[] = [];
  if (viewerIdentityContext?.trim()) {
    sections.push(`当前对话者身份：\n${viewerIdentityContext.trim()}`);
  }
  const visibleLibraryItems = libraryItems.filter((item) => item.body?.trim() || item.title?.trim());
  sections.push(buildContextHitSummary({
    libraryItems: visibleLibraryItems,
    familyMemories,
    diaryEntries,
    growthRecords,
    heritageTasks,
    sessionSavedMemories,
    retrievalMode,
    embeddingReadyCount,
  }));

  const recentSavedMemories = sessionSavedMemories
    .filter((memory) => memory.content.trim())
    .slice(-5);
  if (recentSavedMemories.length > 0) {
    sections.push([
      '本轮会话刚保存的家族记忆：',
      recentSavedMemories.map((memory, index) => (
        `${index + 1}. [${memory.label}；${memory.visibility || 'PRIVATE'}；保存时间：${memory.savedAt}] ${memory.title}：${memory.content.slice(0, 260)}${memory.reason ? `；保存理由：${memory.reason}` : ''}`
      )).join('\n'),
      '回答策略：这些是本轮对话刚刚沉淀的内容，索引可能还没重建，但你可以在后续回答中自然使用；不要逐条复述，只在相关时体现“我记得刚才保存过这件事”。',
    ].join('\n'));
  }

  if (activationScene) {
    sections.push([
      `场景化激活：本轮问题可能属于「${activationScene.label}」。`,
      activationScene.instruction,
      '回答策略：优先把已授权经验沉淀转化为当前场景下的小判断或小行动；不要直接灌输，不要把长辈经验当成唯一正确答案，结尾尽量用一个问题激活用户继续思考。',
    ].join('\n'));
  }

  if (visibleLibraryItems.length > 0) {
    sections.push(`本轮额外匹配的家族记忆片段：\n${visibleLibraryItems
      .slice(0, 12)
      .map((item, index) => {
        const content = viewerRole === 'STUDENT'
          ? item.body?.slice(0, 180)
          : item.body?.slice(0, 280);
        return `${index + 1}. [${memoryLibrarySourceLabel(item.sourceType)}；${item.memberName || '家族成员'}；${item.visibility}] ${item.title || '未命名片段'}：${content || ''}`;
      })
      .join('\n')}`);
  }

  if (visibleLibraryItems.length > 0) {
    sections.push(`额外匹配片段时间线：\n${visibleLibraryItems
      .slice(0, 12)
      .map((item, index) => `${index + 1}. ${memoryLibrarySourceLabel(item.sourceType)}；归属：${item.memberName || '家族成员'}；创建：${item.createdAt || '未知'}；更新：${item.updatedAt || '未知'}；可见范围：${item.visibility || 'UNKNOWN'}`)
      .join('\n')}`);
  }

  const activeFamilyMemories = visibleLibraryItems.length > 0
    ? []
    : familyMemories.filter((memory) => memory.status === 'ACTIVE' && memory.content?.trim());
  if (activeFamilyMemories.length > 0) {
    sections.push(`经验沉淀：\n${activeFamilyMemories
      .map((memory, index) => {
        const scenario = typeof memory.metadata?.scenario === 'string' ? `；场景：${memory.metadata.scenario}` : '';
        const summary = memory.summary?.trim() || memory.content.trim();
        return `${index + 1}. [${memory.type || 'FAMILY'}${scenario}] ${summary}`;
      })
      .join('\n')}`);
  }

  if (visibleLibraryItems.length === 0 && diaryEntries.length > 0) {
    sections.push(`家族日记授权摘要：\n${diaryEntries
      .filter((entry) => entry.rawText?.trim())
      .map((entry, index) => {
        const entryType = entry.structured?.entryType || 'DAILY';
        const title = entry.structured?.title ? `《${entry.structured.title}》` : '';
        const mood = entry.mood ? `；心情：${entry.mood}` : '';
        const content = viewerRole === 'STUDENT'
          ? entry.rawText.slice(0, 160)
          : entry.rawText.slice(0, 260);
        return `${index + 1}. [${entryType}] ${title}${content}${mood}`;
      })
      .join('\n')}`);
  }

  const activeGrowthRecords = visibleLibraryItems.length > 0
    ? []
    : growthRecords.filter((record) => record.status === 'ACTIVE');
  if (activeGrowthRecords.length > 0) {
    const lines = activeGrowthRecords.map((record, index) => {
      const category = record.category || 'OTHER';
      const status = String(record.metadata?.followUpStatus || 'PENDING');
      const perspective = growthMetadataLabel(record.metadata?.observerPerspective, {
        CAREGIVER: '照护者观察',
        SELF: '本人自述',
        ELDER: '长辈观察',
        FAMILY_MEMBER: '家人补充',
        OTHER: '其他来源',
      });
      const evidence = growthMetadataLabel(record.metadata?.evidenceType, {
        OBSERVED_FACT: '可观察事实',
        SELF_REPORT: '本人表达',
        FEELING: '观察者感受',
        INFERENCE: '初步猜测',
      });
      const confidence = growthMetadataLabel(record.metadata?.confidenceLevel, {
        LOW: '低置信',
        MEDIUM: '中等置信',
        HIGH: '较高置信',
      });
      const selfConfirmed = growthMetadataLabel(record.metadata?.selfConfirmed, {
        YES: '本人确认',
        NO: '本人未确认',
        UNKNOWN: '本人未确认/未知',
      });
      const metaNote = [perspective, evidence, confidence, selfConfirmed].filter(Boolean).join('；');
      if (viewerRole === 'STUDENT') {
        return `${index + 1}. [${category}] 家庭近期有一条成长观察信号，留意程度 ${record.severity}，跟进状态 ${status}${metaNote ? `；${metaNote}` : ''}。面向学习者时只可转化为温和、泛化、非指责的提醒，不要复述原文；如果不是本人自述或本人确认，不能推断其真实想法。`;
      }
      return `${index + 1}. [${category}] ${record.content}；留意程度 ${record.severity}；跟进状态 ${status}${metaNote ? `；${metaNote}` : ''}。请区分事实、感受和猜测，保留不确定性。`;
    });
    sections.push(`成长观察安全摘要：\n${lines.join('\n')}`);
  }

  const activeTasks = heritageTasks.filter((task) => task.status === 'PENDING' || task.status === 'DONE');
  if (activeTasks.length > 0) {
    const pendingTasks = activeTasks.filter((task) => task.status === 'PENDING').slice(0, 5);
    const doneTasks = activeTasks.filter((task) => task.status === 'DONE' && task.completionNote?.trim()).slice(0, 3);
    const taskLines: string[] = [];
    for (const task of pendingTasks) {
      const due = task.dueDate ? `；建议完成：${task.dueDate}` : '';
      const target = task.targetLabel ? `；对象/场景：${task.targetLabel}` : '';
      taskLines.push(`[待实践] ${task.title}：${task.action}${target}${due}`);
    }
    for (const task of doneTasks) {
      taskLines.push(`[已完成] ${task.title}：${task.completionNote}`);
    }
    if (taskLines.length > 0) {
      sections.push([
        `家庭任务上下文：\n${taskLines.join('\n')}`,
        '回答策略：如果用户谈到相关场景，可以温和提醒待实践任务或引导复盘已完成任务；不要催促、打卡化或制造压力。',
      ].join('\n'));
    }
  }

  return sections.join('\n\n');
}

function memoryLibrarySourceLabel(type: string) {
  if (type === 'LIFE_RECORD') return '每日记录';
  if (type === 'FAMILY_EXPERIENCE') return '经验沉淀';
  if (type === 'GROWTH_OBSERVATION') return '成长观察';
  if (type === 'AI_SUMMARY') return 'AI 摘要';
  return '记忆片段';
}

function buildContextHitSummary({
  libraryItems,
  familyMemories,
  diaryEntries,
  growthRecords,
  heritageTasks,
  sessionSavedMemories,
  retrievalMode,
  embeddingReadyCount,
}: {
  libraryItems: MemoryLibraryItem[];
  familyMemories: MemoryEntry[];
  diaryEntries: DiaryEntry[];
  growthRecords: GrowthGuardRecord[];
  heritageTasks: HeritageTask[];
  sessionSavedMemories: SessionSavedMemory[];
  retrievalMode?: string;
  embeddingReadyCount?: number;
}) {
  const libraryCounts = libraryItems.reduce<Record<string, number>>((acc, item) => {
    const label = memoryLibrarySourceLabel(item.sourceType);
    acc[label] = (acc[label] || 0) + 1;
    return acc;
  }, {});
  const activeFamilyCount = familyMemories.filter((memory) => memory.status === 'ACTIVE' && memory.content?.trim()).length;
  const diaryCount = diaryEntries.filter((entry) => entry.rawText?.trim()).length;
  const growthCount = growthRecords.filter((record) => record.status === 'ACTIVE').length;
  const taskCount = heritageTasks.filter((task) => task.status === 'PENDING').length;
  const sessionSavedCount = sessionSavedMemories.filter((memory) => memory.content.trim()).length;

  const parts = Object.entries(libraryCounts).map(([label, count]) => `${count} 条${label}`);
  if (parts.length === 0) {
    if (activeFamilyCount > 0) parts.push(`${activeFamilyCount} 条经验沉淀`);
    if (diaryCount > 0) parts.push(`${diaryCount} 条每日记录`);
    if (growthCount > 0) parts.push(`${growthCount} 条成长观察`);
    if (taskCount > 0) parts.push(`${taskCount} 个家庭任务`);
    if (sessionSavedCount > 0) parts.push(`${sessionSavedCount} 条刚保存记忆`);
  } else if (taskCount > 0) {
    parts.push(`${taskCount} 个家庭任务`);
    if (sessionSavedCount > 0) parts.push(`${sessionSavedCount} 条刚保存记忆`);
  } else if (sessionSavedCount > 0) {
    parts.push(`${sessionSavedCount} 条刚保存记忆`);
  }

  if (parts.length === 0) {
    return [
      '本轮记忆命中摘要：没有命中明确的家族长期记忆。',
      '回答策略：不要假装了解这个家族；可以温和说明资料不足，并建议补充一条每日记录、经验沉淀或成长观察。',
    ].join('\n');
  }

  return [
    `本轮记忆命中摘要：命中 ${parts.join('、')}。RAG 模式：${retrievalMode || 'TEXT_FALLBACK'}；可用向量索引：${embeddingReadyCount ?? 0} 条。`,
    '回答策略：可以自然说明“我参考了这些授权家族记录”，但不要逐条罗列或复述原文；先给基于家族记录的判断，再给下一步小行动。',
  ].join('\n');
}

const familyContextTerms = [
  'family', 'diary', 'memory', 'growth', 'parent', 'child', 'study',
  'tooth', 'teeth', 'dental', 'screen', 'sleep', 'health', 'exercise', 'emotion',
  '家族', '家庭', '家人', '家里', '我家', '我们家', '家长', '爸', '妈', '爷', '奶', '外公', '外婆',
  '孩子', '儿子', '女儿', '孙', '长辈', '亲子', '关系', '沟通', '日记', '记录',
  '记忆', '经验', '沉淀', '传承', '故事', '成长', '观察', '情绪', '焦虑', '压力',
  '学习', '作业', '考试', '升学', '志愿', '学校', '选择', '复盘', '后悔', '健康',
  '牙', '刷牙', '视力', '睡眠', '运动', '体态', '手机', '屏幕', '习惯', '陪伴',
  '教育', '保存', '记下来', '想起来',
];

function shouldRecallFamilyContext(query: string) {
  const normalized = query.trim().toLowerCase().replace(/[，。！？；：“”‘’（）【】《》、,.!?;:'"()[\]{}<>]/g, ' ');
  if (!normalized) return true;
  const compact = normalized.replace(/\s+/g, '');
  return familyContextTerms.some((term) => normalized.includes(term) || compact.includes(term));
}

type FamilyActivationScene = {
  label: string;
  searchKeywords: string[];
  instruction: string;
};

const activationScenes: FamilyActivationScene[] = [
  {
    label: '升学选择',
    searchKeywords: ['升学', '志愿', '专业', '考研', '择校', '人生选择'],
    instruction: '重点寻找家族里关于选择、取舍、长期后果、后悔和复盘的经验。',
  },
  {
    label: '健康提醒',
    searchKeywords: ['牙齿', '视力', '体态', '睡眠', '运动', '健康', '换牙期'],
    instruction: '重点寻找牙齿、视力、体态、睡眠、运动等早期信号和可执行提醒，表达要温和，不制造焦虑。',
  },
  {
    label: '亲子沟通',
    searchKeywords: ['亲子', '沟通', '吵架', '误会', '青春期', '陪伴'],
    instruction: '重点寻找家人沟通方式、误会复盘、表达边界和陪伴经验，避免站队或扩大冲突。',
  },
  {
    label: '钱与工作',
    searchKeywords: ['工作', '职业', '钱', '投资', '创业', '风险', '责任'],
    instruction: '重点寻找职业选择、金钱观、风险承担、责任边界相关经验，帮助用户拆解判断条件。',
  },
  {
    label: '失败复盘',
    searchKeywords: ['失败', '后悔', '低谷', '挫折', '遗憾', '复盘'],
    instruction: '重点寻找从失败、后悔、低谷中提炼出的教训，把情绪转成可执行的下一步。',
  },
];

function detectFamilyActivationScene(query: string): FamilyActivationScene | null {
  const normalized = query.trim().toLowerCase();
  if (!normalized) return null;
  return activationScenes.find((scene) => scene.searchKeywords.some((keyword) => normalized.includes(keyword))) || null;
}

function growthMetadataLabel(value: unknown, labels: Record<string, string>) {
  if (typeof value !== 'string') return '';
  return labels[value] || '';
}

function normalizeAssistantMetadata(metadata: Record<string, unknown>): NonNullable<ChatMessage['metadata']> {
  const webSearch = metadata.web_search;
  if (!webSearch || typeof webSearch !== 'object') return {};
  const data = webSearch as Record<string, unknown>;
  const rawSources = Array.isArray(data.sources) ? data.sources : [];
  return {
    webSearch: {
      needed: Boolean(data.needed),
      used: Boolean(data.used),
      resultCount: Number(data.result_count) || 0,
      sources: rawSources
        .filter((item): item is Record<string, unknown> => Boolean(item) && typeof item === 'object')
        .map((item) => ({
          title: typeof item.title === 'string' ? item.title : '未命名来源',
          url: typeof item.url === 'string' ? item.url : '',
          snippet: typeof item.snippet === 'string' ? item.snippet : '',
        }))
        .filter((item) => item.url)
        .slice(0, 4),
    },
  };
}

function currentTimeContext() {
  return {
    clientTimestamp: new Date().toISOString(),
    clientTimezone: Intl.DateTimeFormat().resolvedOptions().timeZone || '',
  };
}
