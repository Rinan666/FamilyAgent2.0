import { describe, expect, it, vi } from 'vitest';
import { sseStreamRequest } from './shared';

function streamFromChunks(chunks: string[]) {
  const encoder = new TextEncoder();
  return new ReadableStream<Uint8Array>({
    start(controller) {
      chunks.forEach((chunk) => controller.enqueue(encoder.encode(chunk)));
      controller.close();
    },
  });
}

describe('sseStreamRequest', () => {
  it('uses the backend API proxy and handles streamed SSE chunks incrementally', async () => {
    const events: string[] = [];
    const fetchMock = vi.fn(async () => new Response(
      streamFromChunks([
        ': connected\n\n',
        'data: {"content":"hello"}\n\n',
        'data: {"content":" world"}\n\n',
        'data: {"done":true}\n\n',
      ]),
      {
        status: 200,
        headers: { 'Content-Type': 'text/event-stream' },
      },
    ));
    vi.stubGlobal('fetch', fetchMock);

    const handle = sseStreamRequest(
      '/agent/chat/stream',
      { member_message: 'hi' },
      (chunk) => events.push(`chunk:${chunk}`),
      () => events.push('done'),
      (error) => events.push(`error:${error}`),
    );

    await handle.completed;

    expect(fetchMock).toHaveBeenCalledWith('/api/agent/chat/stream', expect.objectContaining({
      method: 'POST',
      headers: expect.objectContaining({
        Accept: 'text/event-stream',
        'Content-Type': 'application/json;charset=UTF-8',
      }),
    }));
    expect(events).toEqual(['chunk:hello', 'chunk: world', 'done']);
  });

  it('uses typed stream error message instead of boolean error flag', async () => {
    const events: string[] = [];
    vi.stubGlobal('fetch', vi.fn(async () => new Response(
      streamFromChunks([
        'data: {"type":"error","error":true,"code":"AI_STREAM_UNAVAILABLE","message":"AI service unavailable, please retry later.","retryable":true,"degraded":false,"requestId":"chat-test-request"}\n\n',
      ]),
      { status: 200, headers: { 'Content-Type': 'text/event-stream' } },
    )));

    const handle = sseStreamRequest(
      '/agent/chat/stream',
      { member_message: 'hi' },
      (chunk) => events.push(`chunk:${chunk}`),
      () => events.push('done'),
      (error) => events.push(`error:${error}`),
    );

    await handle.completed;

    expect(events).toEqual(['error:AI service unavailable, please retry later.']);
  });

  it('normalizes top-level typed metadata events', async () => {
    const metadataEvents: Record<string, unknown>[] = [];
    vi.stubGlobal('fetch', vi.fn(async () => new Response(
      streamFromChunks([
        'data: {"type":"metadata","web_search":{"needed":true,"used":false},"requestId":"chat-test-request"}\n\n',
        'data: {"type":"done","done":true,"requestId":"chat-test-request"}\n\n',
      ]),
      { status: 200, headers: { 'Content-Type': 'text/event-stream' } },
    )));

    const handle = sseStreamRequest(
      '/agent/chat/stream',
      { member_message: 'hi' },
      () => undefined,
      () => undefined,
      () => undefined,
      (metadata) => metadataEvents.push(metadata),
    );

    await handle.completed;

    expect(metadataEvents).toEqual([{ web_search: { needed: true, used: false }, requestId: 'chat-test-request' }]);
  });
});
