'use client';

import { History, Loader2, PanelLeftClose, PanelLeftOpen, RefreshCw, Trash2 } from 'lucide-react';
import { cn } from '@/lib/utils';
import type { ChatSessionSummary } from '@/types';
import {
  formatSessionTime,
  getSessionTitle,
  normalizeAgentSessionMetadata,
  sessionBadge,
} from '@/components/agent/agentDisplay';

interface AgentSessionSidebarProps {
  collapsed: boolean;
  familyName?: string;
  sessions: ChatSessionSummary[];
  sessionId: number | null;
  isLoadingSessions: boolean;
  sessionError: string;
  isClearingSessions?: boolean;
  onToggleCollapsed: () => void;
  onRefresh: () => void;
  onLoadSession: (sessionId: number) => void;
  onDeleteSession: (sessionId: number) => void;
  onClearSessions: () => void;
}

export default function AgentSessionSidebar({
  collapsed,
  familyName,
  sessions,
  sessionId,
  isLoadingSessions,
  sessionError,
  isClearingSessions = false,
  onToggleCollapsed,
  onRefresh,
  onLoadSession,
  onDeleteSession,
  onClearSessions,
}: AgentSessionSidebarProps) {
  const confirmPermanentDelete = (session: ChatSessionSummary) => {
    const title = getSessionTitle(session);
    const confirmed = window.confirm(`永久删除“${title}”？\n\n删除后会话、消息和归档内容都无法恢复。`);
    if (confirmed) {
      onDeleteSession(session.id);
    }
  };

  const confirmClearSessions = () => {
    if (sessions.length === 0 || isClearingSessions) return;
    const confirmed = window.confirm(`永久删除当前家庭的 ${sessions.length} 个会话？\n\n删除后会话、消息和归档内容都无法恢复。`);
    if (confirmed) {
      onClearSessions();
    }
  };

  if (collapsed) {
    return (
      <aside className="hidden h-full w-14 shrink-0 flex-col items-center gap-2 border-r border-stone-200 bg-stone-50/80 py-3 lg:flex">
        <button
          type="button"
          onClick={onToggleCollapsed}
          className="inline-flex h-10 w-10 items-center justify-center rounded-md text-stone-500 transition hover:bg-white hover:text-stone-900"
          aria-label="展开会话历史"
          title="展开会话历史"
        >
          <PanelLeftOpen className="h-5 w-5" />
        </button>
        <button
          type="button"
          onClick={onRefresh}
          className="inline-flex h-10 w-10 items-center justify-center rounded-md text-stone-500 transition hover:bg-white hover:text-emerald-700"
          aria-label="刷新会话历史"
          title="刷新会话历史"
        >
          <RefreshCw className={cn('h-4 w-4', isLoadingSessions && 'animate-spin')} />
        </button>
      </aside>
    );
  }

  return (
    <aside className="hidden h-full w-[18rem] shrink-0 flex-col border-r border-stone-200 bg-stone-50/80 lg:flex">
      <div className="shrink-0 px-3 py-3">
        <div className="flex items-center justify-between gap-2">
          <div className="min-w-0">
            <div className="flex items-center gap-2 text-sm font-semibold text-stone-950">
              <History className="h-4 w-4 text-emerald-700" />
              <span>会话历史</span>
            </div>
            <p className="mt-1 truncate text-xs text-stone-500">{familyName || '当前家庭'}</p>
          </div>
          <button
            type="button"
            onClick={onToggleCollapsed}
            className="inline-flex h-9 w-9 items-center justify-center rounded-md text-stone-500 transition hover:bg-white hover:text-stone-900"
            aria-label="折叠会话历史"
            title="折叠会话历史"
          >
            <PanelLeftClose className="h-4 w-4" />
          </button>
        </div>
      </div>

      <div className="border-y border-stone-200 px-3 py-2">
        <button
          type="button"
          onClick={confirmClearSessions}
          disabled={sessions.length === 0 || isClearingSessions || isLoadingSessions}
          className="inline-flex h-8 w-full items-center justify-center gap-2 rounded-md text-xs font-medium text-stone-500 transition hover:bg-rose-50 hover:text-rose-600 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {isClearingSessions ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Trash2 className="h-3.5 w-3.5" />}
          清空
        </button>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto px-2 py-3">
        {sessionError && (
          <div className="mb-3 rounded-md border border-rose-200 bg-rose-50 px-3 py-2 text-xs leading-5 text-rose-700">
            {sessionError}
          </div>
        )}

        {isLoadingSessions ? (
          <div className="py-12 text-center text-sm text-stone-500">
            <Loader2 className="mx-auto mb-3 h-4 w-4 animate-spin" />
            正在加载会话...
          </div>
        ) : sessions.length === 0 ? (
          <div className="rounded-md border border-dashed border-stone-300 bg-white/70 px-4 py-8 text-center text-sm leading-6 text-stone-500">
            还没有已保存的会话。
          </div>
        ) : (
          <div className="space-y-1.5">
            {sessions.map((session) => {
              const badge = sessionBadge(session.metadata);
              const metadata = normalizeAgentSessionMetadata(session.metadata);
              const active = sessionId === session.id;

              return (
                <div
                  key={session.id}
                  className={cn(
                    'group rounded-md px-2 py-2 transition-colors',
                    active ? 'bg-emerald-50 text-emerald-950 ring-1 ring-emerald-100' : 'text-stone-700 hover:bg-white',
                  )}
                >
                  <button
                    type="button"
                    onClick={() => onLoadSession(session.id)}
                    className="w-full text-left"
                  >
                    <div className="flex items-start justify-between gap-2">
                      <div className="min-w-0">
                        <div className="truncate text-sm font-medium text-stone-900">{getSessionTitle(session)}</div>
                        {session.summary && (
                          <div className="mt-1 line-clamp-2 text-xs leading-5 text-stone-500">
                            {session.summary}
                          </div>
                        )}
                      </div>
                      <span className={cn('shrink-0 rounded-full px-2 py-0.5 text-[10px] font-medium', badge.className)}>
                        {badge.label}
                      </span>
                    </div>
                    {metadata.targetMemberName && metadata.agentMode === 'mirror' && !metadata.hasTargetSwitches && (
                      <div className="mt-2 truncate text-[11px] text-emerald-700">
                        对象：{metadata.targetMemberName}
                      </div>
                    )}
                    {metadata.targetPersonaName && metadata.agentMode === 'persona' && !metadata.hasTargetSwitches && (
                      <div className="mt-2 truncate text-[11px] text-violet-700">
                        精神成员：{metadata.targetPersonaName}
                      </div>
                    )}
                    <div className="mt-2 flex items-center justify-between text-[11px] text-stone-500">
                      <span>{formatSessionTime(session.lastMessageAt || session.startedAt)}</span>
                      <span>{session.messageCount || 0} 条</span>
                    </div>
                  </button>

                  <div className="mt-1 flex justify-end opacity-0 transition-opacity group-hover:opacity-100 group-focus-within:opacity-100">
                    <button
                      type="button"
                      onClick={() => confirmPermanentDelete(session)}
                      className="inline-flex items-center gap-1 rounded-md px-2 py-1 text-xs text-stone-500 transition hover:bg-rose-50 hover:text-rose-600"
                      aria-label={`永久删除 ${getSessionTitle(session)}`}
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                      删除
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </aside>
  );
}
