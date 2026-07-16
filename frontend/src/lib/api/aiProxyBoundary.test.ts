import { describe, expect, it } from 'vitest';
import { readdirSync, readFileSync, statSync } from 'node:fs';
import path from 'node:path';
import { AI_PROXY_ROUTES, aiProxyUrl, resolveAiProxyBoundary } from './aiProxyBoundary';

const SRC_ROOT = path.resolve(__dirname, '../..');
const ALLOWED_RAW_PROXY_FILES = new Set([
  path.resolve(__dirname, 'aiProxyBoundary.ts'),
  path.resolve(__dirname, 'aiProxyBoundary.test.ts'),
]);

function sourceFiles(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const fullPath = path.join(dir, name);
    const stat = statSync(fullPath);
    if (stat.isDirectory()) return sourceFiles(fullPath);
    if (!/\.(ts|tsx)$/.test(name)) return [];
    return [fullPath];
  });
}

describe('resolveAiProxyBoundary', () => {
  it('routes family draft generation through Java Backend', () => {
    expect(resolveAiProxyBoundary(['memory', 'organize-draft'])).toMatchObject({
      allowed: false,
      category: 'BACKEND_OWNED',
      migrationTarget: '/api/agent/organize-draft',
    });
    expect(resolveAiProxyBoundary(['memory', 'persona-material-draft'])).toMatchObject({
      allowed: false,
      category: 'BACKEND_OWNED',
      migrationTarget: '/api/agent/persona-material-draft',
    });
  });

  it('allows temporary non-business media processing routes', () => {
    expect(resolveAiProxyBoundary(['dip', 'faces', 'cluster-by-urls'])).toMatchObject({
      allowed: true,
      category: 'NON_BUSINESS_MEDIA',
      route: 'dip/faces/cluster-by-urls',
    });
  });

  it('blocks Agent and family data capabilities from bypassing Java Backend', () => {
    expect(resolveAiProxyBoundary(['memory', 'save-plan'])).toMatchObject({
      allowed: false,
      category: 'BACKEND_OWNED',
      migrationTarget: '/api/agent/save-memory-plan',
    });
    expect(resolveAiProxyBoundary(['agent', 'chat', 'stream'])).toMatchObject({
      allowed: false,
      category: 'BACKEND_OWNED',
      migrationTarget: '/api/agent/*',
    });
    expect(resolveAiProxyBoundary(['family', 'members', 'summarize'])).toMatchObject({
      allowed: false,
      category: 'BACKEND_OWNED',
      migrationTarget: '/api/families/*',
    });
    expect(resolveAiProxyBoundary(['memory', 'create-family-memory'])).toMatchObject({
      allowed: false,
      category: 'BACKEND_OWNED',
      migrationTarget: '/api/memories/*',
    });
  });

  it('blocks unknown or malformed proxy paths by default', () => {
    expect(resolveAiProxyBoundary(['evals', 'run'])).toMatchObject({
      allowed: false,
      category: 'UNKNOWN',
    });
    expect(resolveAiProxyBoundary(['..', 'agent'])).toMatchObject({
      allowed: false,
      category: 'UNKNOWN',
      route: '',
    });
  });

  it('builds proxy URLs only from allowlisted route constants', () => {
    expect(aiProxyUrl(AI_PROXY_ROUTES.DIP_FACES_CLUSTER_BY_URLS)).toBe('/ai-proxy/dip/faces/cluster-by-urls');
  });

  it('keeps raw frontend proxy paths centralized in the boundary module', () => {
    const offenders = sourceFiles(SRC_ROOT)
      .filter((file) => !ALLOWED_RAW_PROXY_FILES.has(file))
      .filter((file) => readFileSync(file, 'utf8').includes('/ai-proxy'));

    expect(offenders).toEqual([]);
  });
});
