import { describe, expect, it, vi } from 'vitest';
import { enqueuePersistMessages } from './sessionPersistence';

describe('enqueuePersistMessages', () => {
  it('serializes persistence tasks in enqueue order', async () => {
    const calls: string[] = [];
    let releaseFirst: (() => void) | null = null;

    const persist = vi.fn((messages: string[]) => new Promise<void>((resolve) => {
      calls.push(`start:${messages[0]}`);
      if (messages[0] === 'first') {
        releaseFirst = () => {
          calls.push(`end:${messages[0]}`);
          resolve();
        };
        return;
      }
      calls.push(`end:${messages[0]}`);
      resolve();
    }));

    const first = enqueuePersistMessages({
      queue: Promise.resolve(),
      messages: ['first'],
      persist,
    });

    const second = enqueuePersistMessages({
      queue: first.nextQueue,
      messages: ['second'],
      persist,
    });

    await vi.waitFor(() => {
      expect(calls).toEqual(['start:first']);
    });

    expect(releaseFirst).not.toBeNull();
    releaseFirst!();
    await first.task;
    await second.task;

    expect(calls).toEqual([
      'start:first',
      'end:first',
      'start:second',
      'end:second',
    ]);
  });

  it('recovers the queue after a failed persistence task', async () => {
    const persist = vi.fn(async (_messages: string[]) => undefined);
    persist.mockImplementationOnce(async () => {
      throw new Error('boom');
    });

    const first = enqueuePersistMessages({
      queue: Promise.resolve(),
      messages: ['first'],
      persist,
    });

    await expect(first.task).rejects.toThrow('boom');

    const second = enqueuePersistMessages({
      queue: first.nextQueue,
      messages: ['second'],
      persist,
    });

    await expect(second.task).resolves.toBeUndefined();
    expect(persist).toHaveBeenNthCalledWith(1, ['first']);
    expect(persist).toHaveBeenNthCalledWith(2, ['second']);
  });

  it('skips persistence when there are no messages', async () => {
    const persist = vi.fn(async (_messages: string[]) => undefined);

    const result = enqueuePersistMessages<string>({
      queue: Promise.resolve(),
      messages: [],
      persist,
    });

    await expect(result.task).resolves.toBeUndefined();
    expect(persist).not.toHaveBeenCalled();
  });
});
