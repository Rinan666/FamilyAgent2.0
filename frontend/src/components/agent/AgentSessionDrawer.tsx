'use client';

import { History, Loader2, RefreshCw, Trash2, X } from 'lucide-react';
import { cn } from '@/lib/utils';
import type { ChatSessionSummary } from '@/types';
import {
  formatSessionTime,
  getSessionTitle,
  normalizeAgentSessionMetadata,
  sessionBadge,
} from '@/components/agent/agentDisplay';

interface AgentSessionDrawerProps {
  open: boolean;
  familyName?: string;
  sessions: ChatSessionSummary[];
  sessionId: number | null;
  isLoadingSessions: boolean;
  sessionError: string;
  onClose: () => void;
  onRefresh: () => void;
  onLoadSession: (sessionId: number) => void;
  onDeleteSession: (sessionId: number) => void;
}

export default function AgentSessionDrawer({
  open,
  familyName,
  sessions,
  sessionId,
  isLoadingSessions,
  sessionError,
  onClose,
  onRefresh,
  onLoadSession,
  onDeleteSession,
}: AgentSessionDrawerProps) {
  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50">
      <button
        type="button"
        className="absolute inset-0 bg-stone-950/24 backdrop-blur-sm"
        aria-label="关闭会话历史"
        onClick={onClose}
      />

      <aside className="absolute inset-y-0 left-0 flex w-full max-w-sm flex-col border-r border-white/80 bg-[#f6f5f0]/96 shadow-[0_20px_70px_rgba(24,39,32,0.16)] backdrop-blur-xl">
        <div className="flex items-start justify-between gap-3 border-b border-stone-200/80 px-5 py-5">
          <div>
            <div className="flex items-center gap-2 text-sm font-semibold text-stone-900">
              <History className="h-4 w-4 text-emerald-700" />
              会话历史
            </div>
            <p className="mt-1 text-sm text-stone-500">{familyName || '当前家庭'}</p>
          </div>
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={onRefresh}
              className="inline-flex h-10 w-10 items-center justify-center rounded-2xl text-stone-500 transition hover:bg-white/80"
              aria-label="刷新会话历史"
            >
              <RefreshCw className={`h-4 w-4 ${isLoadingSessions ? 'animate-spin' : ''}`} />
            </button>
            <button
              type="button"
              onClick={onClose}
              className="inline-flex h-10 w-10 items-center justify-center rounded-2xl text-stone-500 transition hover:bg-white/80"
              aria-label="关闭会话历史"
            >
              <X className="h-5 w-5" />
            </button>
          </div>
        </div>

        <div className="flex-1 overflow-y-auto px-4 py-4">
          {sessionError && (
            <div className="mb-4 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-xs leading-5 text-rose-700">
              {sessionError}
            </div>
          )}

          {isLoadingSessions ? (
            <div className="py-12 text-center text-sm text-stone-500">
              <Loader2 className="mx-auto mb-3 h-4 w-4 animate-spin" />
              正在加载会话...
            </div>
          ) : sessions.length === 0 ? (
            <div className="rounded-[24px] border border-dashed border-stone-300 bg-white/70 px-5 py-10 text-center text-sm leading-6 text-stone-500">
              还没有已保存的会话，开始第一段家庭对话吧。
            </div>
          ) : (
            <div className="space-y-3">
              {sessions.map((session) => {
                const badge = sessionBadge(session.metadata);
                const metadata = normalizeAgentSessionMetadata(session.metadata);

                return (
                  <div
                    key={session.id}
                    className={cn(
                      'rounded-2xl border px-3 py-3 transition-colors',
                      sessionId === session.id ? 'border-emerald-200 bg-emerald-50/80' : 'border-stone-200 bg-white hover:border-stone-300',
                    )}
                  >
                    <button
                      type="button"
                      onClick={() => {
                        onLoadSession(session.id);
                        onClose();
                      }}
                      className="w-full text-left"
                    >
                      <div className="flex items-start justify-between gap-3">
                        <div className="min-w-0">
                          <div className="truncate text-sm font-semibold text-stone-900">{getSessionTitle(session)}</div>
                          {session.summary && (
                            <div className="mt-1 line-clamp-2 text-xs leading-5 text-stone-500">
                              {session.summary}
                            </div>
                          )}
                        </div>
                        <span className={`shrink-0 rounded-full px-2 py-0.5 text-[10px] font-medium ${badge.className}`}>
                          {badge.label}
                        </span>
                      </div>
                      {metadata.targetMemberName && metadata.agentMode === 'mirror' && !metadata.hasTargetSwitches && (
                        <div className="mt-2 text-[11px] text-emerald-700">
                          对象：{metadata.targetMemberName}
                        </div>
                      )}
                      <div className="mt-3 flex items-center justify-between text-[11px] text-stone-500">
                        <span>{formatSessionTime(session.lastMessageAt || session.startedAt)}</span>
                        <span>{session.messageCount || 0} 条消息</span>
                      </div>
                    </button>

                    <div className="mt-2 flex justify-end">
                      <button
                        type="button"
                        onClick={() => onDeleteSession(session.id)}
                        className="inline-flex items-center gap-1 rounded-full px-2 py-1 text-xs text-stone-500 transition hover:bg-rose-50 hover:text-rose-600"
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
    </div>
  );
}
