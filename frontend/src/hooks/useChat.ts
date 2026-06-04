/**
 * 家教聊天 Hook
 */
'use client';

import { useCallback } from 'react';
import { useChatStore } from '@/stores/chatStore';
import { tutorApi } from '@/lib/api';
import type { Question } from '@/types';

export function useChat() {
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

  /**
   * 发送讲题请求
   */
  const askQuestion = useCallback(
    async (question: Question, studentMessage: string) => {
      if (isStreaming) return;

      setCurrentQuestion(question);
      addMessage('user', studentMessage);
      addMessage('assistant', ''); // 占位，流式填充
      setStreaming(true);

      const history = messages
        .filter((m) => m.role !== 'system')
        .map((m) => ({ role: m.role, content: m.content }));

      tutorApi.explainStream(
        {
          questionContent: question.content.stem,
          answer: question.answer.value,
          steps: question.answer.steps?.join('\n') || '',
          studentMessage,
          history,
          subject: question.subject,
          grade: question.grade,
          knowledgePoint: '',
          masteryLevel: '中',
        },
        (chunk) => {
          appendToLastMessage(chunk);
        },
        () => {
          setStreaming(false);
        },
        (error) => {
          appendToLastMessage(`\n\n[错误] ${error}`);
          setStreaming(false);
        },
      );
    },
    [messages, isStreaming, addMessage, appendToLastMessage, setStreaming, setCurrentQuestion],
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

      const history = messages
        .filter((m) => m.role !== 'system')
        .map((m) => ({ role: m.role, content: m.content }));

      tutorApi.explainStream(
        {
          questionContent: currentQuestion.content.stem,
          answer: currentQuestion.answer.value,
          steps: currentQuestion.answer.steps?.join('\n') || '',
          studentMessage: message,
          history,
          subject: currentQuestion.subject,
          grade: currentQuestion.grade,
          knowledgePoint: '',
          masteryLevel: '中',
        },
        (chunk) => {
          appendToLastMessage(chunk);
        },
        () => {
          setStreaming(false);
        },
        (error) => {
          appendToLastMessage(`\n\n[错误] ${error}`);
          setStreaming(false);
        },
      );
    },
    [messages, isStreaming, currentQuestion, addMessage, appendToLastMessage, setStreaming],
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
