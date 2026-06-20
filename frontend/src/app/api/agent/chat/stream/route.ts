import { type NextRequest, NextResponse } from 'next/server';

const BACKEND_URL = process.env.BACKEND_URL || 'http://localhost:8080';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export async function POST(request: NextRequest) {
  const body = await request.text();
  const authorization = request.headers.get('authorization');

  let upstream: Response;
  try {
    upstream = await fetch(`${BACKEND_URL}/api/agent/chat/stream`, {
      method: 'POST',
      headers: {
        Accept: 'text/event-stream',
        'Content-Type': request.headers.get('content-type') || 'application/json;charset=UTF-8',
        ...(authorization ? { Authorization: authorization } : {}),
      },
      body,
      cache: 'no-store',
      signal: request.signal,
    });
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Backend service unavailable';
    return NextResponse.json({ code: 503, message }, { status: 503 });
  }

  if (!upstream.body) {
    return NextResponse.json(
      { code: 502, message: 'Backend returned no stream body.' },
      { status: 502 },
    );
  }

  return new Response(upstream.body, {
    status: upstream.status,
    headers: {
      'Content-Type': upstream.headers.get('content-type') || 'text/event-stream;charset=UTF-8',
      'Cache-Control': 'no-cache, no-transform',
      'X-Accel-Buffering': 'no',
    },
  });
}
