/**
 * 家庭陪伴 AI 聊天 Hook
 *
 * @param getMastery - 根据知识点ID获取掌握等级（可选，默认返回'中'）
 * @param getKnowledgePoint - 根据知识点ID获取知识点名称（可选，默认返回空）
 */
'use client';

import { useCallback } from 'react';
import { useChatStore } from '@/stores/chatStore';
import { diaryApi, growthGuardApi, memoryApi, tutorApi } from '@/lib/api';
import type { ChatMessage, DiaryEntry, GrowthGuardRecord, MemoryEntry, Question } from '@/types';
import type { ViewerRole } from '@/lib/roles';

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
}

export function useChat(options: UseChatOptions = {}) {
  const {
    messages,
    isStreaming,
    currentQuestion,
    addMessage,
    appendToLastMessage,
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
        const [learningMemories, familyMemories, diaryEntries, growthRecords] = await Promise.all([
          memoryApi.recall({
            query: params.query,
            subject: params.subject,
            knowledgePointId: params.knowledgePointId,
            limit: 8,
          }).catch(() => [] as MemoryEntry[]),
          activeFamilyId
            ? memoryApi.listFamilyMemories(activeFamilyId, 8).catch(() => [] as MemoryEntry[])
            : Promise.resolve([] as MemoryEntry[]),
          activeFamilyId
            ? diaryApi.listFamilyEntries(activeFamilyId, 8).catch(() => [] as DiaryEntry[])
            : Promise.resolve([] as DiaryEntry[]),
          activeFamilyId
            ? growthGuardApi.listFamilyRecords(activeFamilyId, 8).catch(() => [] as GrowthGuardRecord[])
            : Promise.resolve([] as GrowthGuardRecord[]),
        ]);
        return formatMemoryContext({
          learningMemories,
          familyMemories,
          diaryEntries,
          growthRecords,
          viewerRole,
        });
      } catch (error) {
        console.log('Family context memories not loaded:', error);
        return '';
      }
    },
    [activeFamilyId, viewerRole],
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

      const memoryContext = await recallMemoryContext({
        query: studentMessage,
        subject: question.subject,
        knowledgePointId: question.kpId || undefined,
      });

      tutorApi.explainStream(
        { ...makeBody(question, studentMessage), memoryContext },
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
      );
    },
    [
      isStreaming,
      makeBody,
      addMessage,
      appendToLastMessage,
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

      const memoryContext = await recallMemoryContext({
        query: message,
        subject: currentQuestion.subject,
        knowledgePointId: currentQuestion.kpId || undefined,
      });

      tutorApi.explainStream(
        { ...makeBody(currentQuestion, message), memoryContext },
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
      );
    },
    [
      isStreaming,
      currentQuestion,
      makeBody,
      addMessage,
      appendToLastMessage,
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

      const memoryContext = await recallMemoryContext({
        query: message,
        subject: 'math',
      });

      tutorApi.explainStream(
        {
          questionContent: '',
          answer: '',
          steps: '',
          studentMessage: message,
          history,
          subject: '数学',
          grade: '',
          knowledgePoint: '',
          masteryLevel: '中',
          teachingStyle: getTeachingStyle?.() || teachingStyle,
          mode: 'chat',
          memoryContext,
          viewerRole,
          targetRole,
        },
        (chunk) => appendToLastMessage(chunk),
        () => {
          setStreaming(false);
          persistChatSafely();
        },
        (error) => {
          appendToLastMessage(`\n\n[错误] ${error}`);
          setStreaming(false);
          persistChatSafely();
        },
      );
    },
    [
      isStreaming,
      addMessage,
      appendToLastMessage,
      setStreaming,
      getTeachingStyle,
      teachingStyle,
      viewerRole,
      targetRole,
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
  learningMemories,
  familyMemories,
  diaryEntries,
  growthRecords,
  viewerRole,
}: {
  learningMemories: MemoryEntry[];
  familyMemories: MemoryEntry[];
  diaryEntries: DiaryEntry[];
  growthRecords: GrowthGuardRecord[];
  viewerRole: ViewerRole;
}): string {
  const sections: string[] = [];
  const activeLearningMemories = learningMemories.filter((memory) => memory.status === 'ACTIVE' && memory.content?.trim());
  if (activeLearningMemories.length > 0) {
    sections.push(`学习记忆：\n${activeLearningMemories
      .map((memory, index) => {
        const label = memory.type || 'LEARNING';
        return `${index + 1}. [${label}] ${memory.content}`;
      })
      .join('\n')}`);
  }

  const activeFamilyMemories = familyMemories.filter((memory) => memory.status === 'ACTIVE' && memory.content?.trim());
  if (activeFamilyMemories.length > 0) {
    sections.push(`家族经验：\n${activeFamilyMemories
      .map((memory, index) => {
        const scenario = typeof memory.metadata?.scenario === 'string' ? `；场景：${memory.metadata.scenario}` : '';
        const summary = memory.summary?.trim() || memory.content.trim();
        return `${index + 1}. [${memory.type || 'FAMILY'}${scenario}] ${summary}`;
      })
      .join('\n')}`);
  }

  if (diaryEntries.length > 0) {
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

  const activeGrowthRecords = growthRecords.filter((record) => record.status === 'ACTIVE');
  if (activeGrowthRecords.length > 0) {
    const lines = activeGrowthRecords.map((record, index) => {
      const category = record.category || 'OTHER';
      const status = String(record.metadata?.followUpStatus || 'PENDING');
      if (viewerRole === 'STUDENT') {
        return `${index + 1}. [${category}] 家庭近期有一条成长观察信号，留意程度 ${record.severity}，跟进状态 ${status}。面向学习者时只可转化为温和、泛化、非指责的提醒，不要复述原文。`;
      }
      return `${index + 1}. [${category}] ${record.content}；留意程度 ${record.severity}；跟进状态 ${status}`;
    });
    sections.push(`成长守护安全摘要：\n${lines.join('\n')}`);
  }

  return sections.join('\n\n');
}
