import { create } from 'zustand';
import type { ChatMessage } from '@/types';
import { generateId } from '@/lib/utils';

interface ChatState {
  familyId: number | null;
  sessionId: number | null;
  messages: ChatMessage[];
  isStreaming: boolean;
  setFamilyId: (id: number | null) => void;
  setSessionId: (id: number | null) => void;
  setMessages: (messages: ChatMessage[]) => void;
  addMessage: (role: 'user' | 'assistant' | 'system', content: string) => ChatMessage;
  removeMessageById: (id: string) => void;
  appendToLastMessage: (content: string) => void;
  mergeLastAssistantMetadata: (metadata: NonNullable<ChatMessage['metadata']>) => void;
  setStreaming: (streaming: boolean) => void;
  reset: () => void;
}

export const useChatStore = create<ChatState>((set) => ({
  familyId: null,
  sessionId: null,
  messages: [],
  isStreaming: false,

  setFamilyId: (familyId) => set({ familyId }),

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
    return message;
  },

  removeMessageById: (id) => {
    set((state) => ({
      messages: state.messages.filter((message) => message.id !== id),
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

  mergeLastAssistantMetadata: (metadata) => {
    set((state) => {
      const messages = [...state.messages];
      const lastAssistantIndex = [...messages].reverse().findIndex((message) => message.role === 'assistant');
      if (lastAssistantIndex < 0) return { messages };
      const index = messages.length - 1 - lastAssistantIndex;
      const message = messages[index];
      messages[index] = {
        ...message,
        metadata: {
          ...(message.metadata || {}),
          ...metadata,
        },
      };
      return { messages };
    });
  },

  setStreaming: (isStreaming) => set({ isStreaming }),

  reset: () =>
    set({
      sessionId: null,
      messages: [],
      isStreaming: false,
    }),
}));
