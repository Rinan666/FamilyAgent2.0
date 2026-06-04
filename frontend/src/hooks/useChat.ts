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
import type { Question } from '@/types';

interface UseChatOptions {
  /** 根据 kpId 获取学生对该知识点的掌握等级 */
  getMastery?: (kpId: number) => string;
  /** 根据 kpId 获取知识点名称 */
  getKnowledgePoint?: (kpId: number) => string;
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

  const { getMastery = () => '中', getKnowledgePoint = () => '' } = options;

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
      };
    },
    [messages, getMastery, getKnowledgePoint],
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
        () => setStreaming(false),
        (error) => {
          appendToLastMessage(`\n\n[错误] ${error}`);
          setStreaming(false);
        },
      );
    },
    [isStreaming, makeBody, addMessage, appendToLastMessage, setStreaming, setCurrentQuestion],
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
        () => setStreaming(false),
        (error) => {
          appendToLastMessage(`\n\n[错误] ${error}`);
          setStreaming(false);
        },
      );
    },
    [isStreaming, currentQuestion, makeBody, addMessage, appendToLastMessage, setStreaming],
  );

  return {
    messages,
    isStreaming,
    currentQuestion,
    askQuestion,
    sendMessage,
    reset,
  };
}
