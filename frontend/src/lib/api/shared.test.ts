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
});
