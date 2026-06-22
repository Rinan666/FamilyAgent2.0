import { type NextRequest, NextResponse } from 'next/server';

const DEFAULT_AI_SERVICE_URL = process.env.NODE_ENV === 'production'
  ? 'http://ai-service:8000'
  : 'http://localhost:8000';
const AI_SERVICE_URL = (process.env.AI_SERVICE_URL || DEFAULT_AI_SERVICE_URL).replace(/\/+$/, '');

function upstreamOrigin(): string {
  try {
    return new URL(AI_SERVICE_URL).origin;
  } catch {
    return AI_SERVICE_URL;
  }
}

function plainTextSnippet(text: string): string {
  return text.replace(/\s+/g, ' ').trim().slice(0, 240);
}

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
  const contentType = res.headers.get('content-type') || 'application/json';
  const isHtmlResponse = contentType.toLowerCase().includes('text/html')
    || /^\s*<!doctype html/i.test(responseBody)
    || /^\s*<html/i.test(responseBody);

  if (isHtmlResponse) {
    const detail = [
      `AI upstream returned HTML with HTTP ${res.status} from ${upstreamOrigin()}.`,
      'Check AI_SERVICE_URL; Docker production should use http://ai-service:8000 instead of a Cloudflare/public web domain.',
      plainTextSnippet(responseBody),
    ].filter(Boolean).join(' ');
    return NextResponse.json({ success: false, detail }, { status: res.ok ? 502 : res.status });
  }

  return new NextResponse(responseBody, {
    status: res.status,
    headers: {
      'Content-Type': contentType,
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
