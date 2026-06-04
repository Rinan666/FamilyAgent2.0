/**
 * 家教聊天状态管理
 */
import { create } from 'zustand';
import type { ChatMessage, Question } from '@/types';
import { generateId } from '@/lib/utils';

interface ChatState {
  // 当前会话
  sessionId: number | null;
  messages: ChatMessage[];
  isStreaming: boolean;
  currentQuestion: Question | null;

  // 操作
  setSessionId: (id: number | null) => void;
  setMessages: (messages: ChatMessage[]) => void;
  addMessage: (role: 'user' | 'assistant', content: string) => void;
  appendToLastMessage: (content: string) => void;
  setStreaming: (streaming: boolean) => void;
  setCurrentQuestion: (question: Question | null) => void;
  reset: () => void;
}

export const useChatStore = create<ChatState>((set, get) => ({
  sessionId: null,
  messages: [],
  isStreaming: false,
  currentQuestion: null,

  setSessionId: (sessionId) => set({ sessionId }),

  setMessages: (messages) => set({ messages }),

  addMessage: (role, content) => {
    const message: ChatMessage = {
      id: generateId(),
      role,
      content,
      timestamp: new Date().toISOString(),
    };
    set((state) => ({
      messages: [...state.messages, message],
    }));
  },

  appendToLastMessage: (content) => {
    set((state) => {
      const messages = [...state.messages];
      const lastMessage = messages[messages.length - 1];
      if (lastMessage && lastMessage.role === 'assistant') {
        messages[messages.length - 1] = {
          ...lastMessage,
          content: lastMessage.content + content,
        };
      }
      return { messages };
    });
  },

  setStreaming: (isStreaming) => set({ isStreaming }),

  setCurrentQuestion: (currentQuestion) => set({ currentQuestion }),

  reset: () =>
    set({
      sessionId: null,
      messages: [],
      isStreaming: false,
      currentQuestion: null,
    }),
}));
