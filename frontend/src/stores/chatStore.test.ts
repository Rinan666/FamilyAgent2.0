import { beforeEach, describe, expect, it } from 'vitest';
import { generateId } from '@/lib/utils';
import { useChatStore } from '@/stores/chatStore';

describe('chatStore', () => {
  beforeEach(() => {
    useChatStore.setState({
      familyId: null,
      sessionId: null,
      messages: [],
      isStreaming: false,
    });
  });

  it('keeps the family context when starting a new chat', () => {
    useChatStore.getState().setFamilyId(7);
    useChatStore.getState().setSessionId(42);
    useChatStore.getState().addMessage('assistant', 'draft response');
    useChatStore.getState().setStreaming(true);

    useChatStore.getState().reset();

    const state = useChatStore.getState();
    expect(state.familyId).toBe(7);
    expect(state.sessionId).toBeNull();
    expect(state.messages).toEqual([]);
    expect(state.isStreaming).toBe(false);
  });

  it('stores the in-progress response independently from page components', () => {
    useChatStore.setState({
      familyId: 7,
      sessionId: 42,
      messages: [{
        id: generateId(),
        role: 'assistant',
        content: 'partial response',
        timestamp: new Date().toISOString(),
      }],
      isStreaming: true,
    });

    const state = useChatStore.getState();
    expect(state.familyId).toBe(7);
    expect(state.sessionId).toBe(42);
    expect(state.messages[0].content).toBe('partial response');
    expect(state.isStreaming).toBe(true);
  });
});
