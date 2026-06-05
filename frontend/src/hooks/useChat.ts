/**
 * 家教聊天 Hook
 *
 * @param getMastery - 根据知识点ID获取掌握等级（可选，默认返回'中'）
 * @param getKnowledgePoint - 根据知识点ID获取知识点名称（可选，默认返回空）
 */
'use client';

import { useCallback } from 'react';
import { useChatStore } from '@/stores/chatStore';
import { tutorApi } from '@/lib/api';
import type { ChatMessage, Question } from '@/types';

interface UseChatOptions {
  /** 根据 kpId 获取学生对该知识点的掌握等级 */
  getMastery?: (kpId: number) => string;
  /** 根据 kpId 获取知识点名称 */
  getKnowledgePoint?: (kpId: number) => string;
  /** 讲题风格：引导式或快速答案式 */
  teachingStyle?: 'guided' | 'direct';
  getTeachingStyle?: () => 'guided' | 'direct';
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
      };
    },
    [messages, getMastery, getKnowledgePoint, getTeachingStyle, teachingStyle],
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

      tutorApi.explainStream(
        makeBody(question, studentMessage),
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
    [isStreaming, makeBody, addMessage, appendToLastMessage, setStreaming, setCurrentQuestion, persistSafely],
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

      tutorApi.explainStream(
        makeBody(currentQuestion, message),
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
    [isStreaming, currentQuestion, makeBody, addMessage, appendToLastMessage, setStreaming, persistSafely],
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
      persistChatSafely,
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
