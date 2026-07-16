export const AI_PROXY_ROUTES = {
  DIP_FACES_CLUSTER_BY_URLS: '/dip/faces/cluster-by-urls',
} as const;

export type AiProxyRoute = typeof AI_PROXY_ROUTES[keyof typeof AI_PROXY_ROUTES];

export type AiProxyBoundaryCategory =
  | 'AI_RUNTIME'
  | 'NON_BUSINESS_MEDIA'
  | 'BACKEND_OWNED'
  | 'UNKNOWN';

export type AiProxyBoundaryDecision = {
  allowed: boolean;
  category: AiProxyBoundaryCategory;
  route: string;
  detail: string;
  migrationTarget?: string;
};

const ALLOWED_ROUTES = new Map<string, Omit<AiProxyBoundaryDecision, 'allowed' | 'route'>>([
  [routeKey(AI_PROXY_ROUTES.DIP_FACES_CLUSTER_BY_URLS), {
    category: 'NON_BUSINESS_MEDIA',
    detail: 'Allowed as temporary media processing without direct family business writes.',
  }],
]);

const BACKEND_OWNED_ROUTES = new Map<string, string>([
  ['memory/save-plan', '/api/agent/save-memory-plan'],
  ['memory/organize-draft', '/api/agent/organize-draft'],
  ['memory/persona-material-draft', '/api/agent/persona-material-draft'],
]);

const BACKEND_OWNED_PREFIXES = new Map<string, string>([
  ['agent', '/api/agent/*'],
  ['diary', '/api/diaries/*'],
  ['family', '/api/families/*'],
  ['growth', '/api/growth/*'],
  ['memory', '/api/memories/*'],
  ['memories', '/api/memories/*'],
  ['permission', '/api/*'],
  ['permissions', '/api/*'],
  ['session', '/api/sessions/*'],
  ['skillrun', '/api/skill-runs/*'],
  ['tool', '/api/agent/*'],
  ['tools', '/api/agent/*'],
]);

function routeKey(route: string): string {
  return route.replace(/^\/+/, '').replace(/\/+$/, '').toLowerCase();
}

function normalizePath(path: string[]): string | null {
  const segments = path.map((segment) => segment.trim()).filter(Boolean);
  if (segments.length === 0) return null;
  if (segments.some((segment) => segment.includes('..') || segment.includes('\\') || segment.includes('/'))) {
    return null;
  }
  return segments.join('/').toLowerCase();
}

export function aiProxyUrl(route: AiProxyRoute): `/ai-proxy${AiProxyRoute}` {
  return `/ai-proxy${route}`;
}

export function resolveAiProxyBoundary(path: string[]): AiProxyBoundaryDecision {
  const route = normalizePath(path);
  if (!route) {
    return {
      allowed: false,
      category: 'UNKNOWN',
      route: '',
      detail: 'Invalid AI proxy path.',
    };
  }

  const allowed = ALLOWED_ROUTES.get(route);
  if (allowed) {
    return {
      allowed: true,
      route,
      ...allowed,
    };
  }

  const backendRoute = BACKEND_OWNED_ROUTES.get(route);
  if (backendRoute) {
    return {
      allowed: false,
      category: 'BACKEND_OWNED',
      route,
      detail: 'This capability is owned by Java Backend and must not bypass Backend Agent Harness.',
      migrationTarget: backendRoute,
    };
  }

  const [prefix] = route.split('/');
  const migrationTarget = BACKEND_OWNED_PREFIXES.get(prefix);
  if (migrationTarget) {
    return {
      allowed: false,
      category: 'BACKEND_OWNED',
      route,
      detail: 'This capability is owned by Java Backend and must not bypass Backend Agent Harness.',
      migrationTarget,
    };
  }

  return {
    allowed: false,
    category: 'UNKNOWN',
    route,
    detail: 'This AI proxy route is not registered in the frontend boundary allowlist.',
  };
}
