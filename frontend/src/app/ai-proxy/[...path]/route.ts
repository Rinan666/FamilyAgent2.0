import { type NextRequest, NextResponse } from 'next/server';

const AI_SERVICE_URL = process.env.AI_SERVICE_URL || 'http://localhost:8000';

async function proxyToAi(request: NextRequest, path: string[]): Promise<NextResponse> {
  const targetUrl = `${AI_SERVICE_URL}/ai/${path.join('/')}`;

  const headers: Record<string, string> = {
    'Content-Type': request.headers.get('content-type') || 'application/json',
    Connection: 'close',
  };
  const auth = request.headers.get('authorization');
  if (auth) headers['Authorization'] = auth;

  let body: string | undefined;
  if (request.method !== 'GET' && request.method !== 'HEAD') {
    body = await request.text();
  }

  let res: Response;
  try {
    res = await fetch(targetUrl, {
      method: request.method,
      headers,
      body,
      // @ts-expect-error Node fetch supports this to disable keep-alive
      agent: undefined,
      cache: 'no-store',
      signal: AbortSignal.timeout(120_000),
    });
  } catch (err) {
    const message = err instanceof Error ? err.message : 'AI service unreachable';
    return NextResponse.json({ success: false, detail: message }, { status: 502 });
  }

  const responseBody = await res.text();
  return new NextResponse(responseBody, {
    status: res.status,
    headers: {
      'Content-Type': res.headers.get('content-type') || 'application/json',
    },
  });
}

export async function GET(request: NextRequest, { params }: { params: Promise<{ path: string[] }> }) {
  return proxyToAi(request, (await params).path);
}

export async function POST(request: NextRequest, { params }: { params: Promise<{ path: string[] }> }) {
  return proxyToAi(request, (await params).path);
}

export async function PUT(request: NextRequest, { params }: { params: Promise<{ path: string[] }> }) {
  return proxyToAi(request, (await params).path);
}

export async function DELETE(request: NextRequest, { params }: { params: Promise<{ path: string[] }> }) {
  return proxyToAi(request, (await params).path);
}
