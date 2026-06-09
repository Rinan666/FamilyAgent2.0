import { describe, expect, it, vi } from 'vitest';
import type { ChatSessionMessagePage } from '@/types';
import { loadSessionMessagesChronologically } from './sessionHistory';

function page(items: number[], hasMore: boolean, nextBeforeSeq?: number): ChatSessionMessagePage {
  return {
    items: items.map((seq) => ({
      seq,
      id: `m-${seq}`,
      role: seq % 2 === 0 ? 'assistant' : 'user',
      content: `message-${seq}`,
      createdAt: `2026-06-09T10:${String(seq).padStart(2, '0')}:00Z`,
    })),
    hasMore,
    nextBeforeSeq,
  };
}

describe('loadSessionMessagesChronologically', () => {
  it('prepends older pages so the final history stays chronological', async () => {
    const fetchPage = vi.fn()
      .mockResolvedValueOnce(page([5, 6], true, 5))
      .mockResolvedValueOnce(page([1, 2, 3, 4], false));

    const messages = await loadSessionMessagesChronologically(fetchPage, 88, 4);

    expect(fetchPage).toHaveBeenNthCalledWith(1, 88, undefined, 4);
    expect(fetchPage).toHaveBeenNthCalledWith(2, 88, 5, 4);
    expect(messages.map((item) => item.content)).toEqual([
      'message-1',
      'message-2',
      'message-3',
      'message-4',
      'message-5',
      'message-6',
    ]);
  });

  it('stops safely when nextBeforeSeq does not advance', async () => {
    const fetchPage = vi.fn()
      .mockResolvedValueOnce(page([9, 10], true, 9))
      .mockResolvedValueOnce(page([], true, 9));

    const messages = await loadSessionMessagesChronologically(fetchPage, 99, 2);

    expect(fetchPage).toHaveBeenCalledTimes(2);
    expect(messages.map((item) => item.content)).toEqual(['message-9', 'message-10']);
  });

  it('stops safely when the backend returns an empty page while claiming there is more history', async () => {
    const fetchPage = vi.fn()
      .mockResolvedValueOnce(page([7, 8], true, 7))
      .mockResolvedValueOnce(page([], true, 3));

    const messages = await loadSessionMessagesChronologically(fetchPage, 77, 2);

    expect(fetchPage).toHaveBeenCalledTimes(2);
    expect(messages.map((item) => item.content)).toEqual(['message-7', 'message-8']);
  });
});
