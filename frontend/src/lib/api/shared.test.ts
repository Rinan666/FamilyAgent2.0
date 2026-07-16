import { afterEach, describe, expect, it, vi } from 'vitest';
import { AI_PROXY_ROUTES } from './aiProxyBoundary';
import { aiRequest, ApiError, sseStreamRequest } from './shared';

function streamFromChunks(chunks: string[]) {
  const encoder = new TextEncoder();
  return new ReadableStream<Uint8Array>({
    start(controller) {
      chunks.forEach((chunk) => controller.enqueue(encoder.encode(chunk)));
      controller.close();
    },
  });
}

afterEach(() => {
  vi.restoreAllMocks();
});

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

  it('reports an interrupted stream when EOF arrives before done or error', async () => {
    const events: string[] = [];
    vi.stubGlobal('fetch', vi.fn(async () => new Response(
      streamFromChunks([
        ': connected\n\n',
        'data: {"type":"content","content":"partial answer","requestId":"chat-test-request"}\n\n',
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

    expect(events).toEqual([
      'chunk:partial answer',
      'error:AI stream ended before a completion event. Please retry.',
    ]);
  });
});

describe('aiRequest structured failures for remaining proxy routes', () => {
  it('maps provider failure without exposing server details', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      success: false,
      data: null,
      errorCode: 'AI_PROVIDER_ERROR',
      error: 'private provider outage detail',
    }), { status: 200, headers: { 'Content-Type': 'application/json' } })));

    const request = aiRequest(AI_PROXY_ROUTES.DIP_FACES_CLUSTER_BY_URLS, { urls: [] });

    await expect(request).rejects.toEqual(new ApiError(500, 'AI 服务暂时不可用，请稍后再试。'));
  });

  it('maps invalid structured output to a retryable user message', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      success: false,
      data: null,
      errorCode: 'AI_INVALID_RESPONSE',
    }), { status: 200, headers: { 'Content-Type': 'application/json' } })));

    await expect(aiRequest(AI_PROXY_ROUTES.DIP_FACES_CLUSTER_BY_URLS, {}))
      .rejects.toEqual(new ApiError(500, 'AI 返回的草稿格式异常，请重试。'));
  });
});
