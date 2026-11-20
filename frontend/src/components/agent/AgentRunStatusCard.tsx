'use client';

import { useCallback, useEffect, useState } from 'react';
import { Loader2, RefreshCw, RotateCcw } from 'lucide-react';
import {
  agentRunErrorLabel,
  agentRunOperationLabel,
  agentRunStatusClass,
  agentRunStatusLabel,
} from '@/components/agent/agentRunDisplay';
import { agentApi } from '@/lib/api';
import type { AgentRunView } from '@/types';

interface AgentRunStatusCardProps {
  runId: number;
  onRetry?: () => void;
}

const TERMINAL_STATUSES = new Set(['SUCCEEDED', 'FAILED', 'CANCELED']);

export default function AgentRunStatusCard({ runId, onRetry }: AgentRunStatusCardProps) {
  const [run, setRun] = useState<AgentRunView | null>(null);
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [refreshVersion, setRefreshVersion] = useState(0);
  const refresh = useCallback(() => setRefreshVersion((value) => value + 1), []);

  useEffect(() => {
    let canceled = false;
    let timer: ReturnType<typeof setTimeout> | undefined;
    async function load() {
      try {
        const result = await agentApi.getRun(runId);
        if (canceled) return;
        setRun(result);
        setError('');
        if (!TERMINAL_STATUSES.has(result.status)) timer = setTimeout(load, 1500);
      } catch {
        if (!canceled) setError('运行详情暂时不可用。');
      } finally {
        if (!canceled) setIsLoading(false);
      }
    }
    setIsLoading(true);
    void load();
    return () => {
      canceled = true;
      if (timer) clearTimeout(timer);
    };
  }, [refreshVersion, runId]);

  if (isLoading && !run) {
    return <p className="mt-3 text-xs text-stone-400"><Loader2 className="mr-1 inline h-3 w-3 animate-spin" />正在读取执行状态...</p>;
  }
  if (!run) {
    return (
      <button type="button" onClick={refresh} className="mt-3 text-xs text-stone-500 underline">
        {error || '运行详情不可用'}，点击重试
      </button>
    );
  }

  return (
    <details className="mt-3 rounded-lg border border-stone-200 bg-stone-50/70 px-3 py-2 text-xs">
      <summary className="flex cursor-pointer list-none items-center justify-between gap-3 text-stone-600">
        <span>执行过程 · #{run.runId}</span>
        <span className={`rounded-full px-2 py-1 ${agentRunStatusClass(run.status)}`}>
          {agentRunStatusLabel(run.status)}
        </span>
      </summary>
      <div className="mt-3 space-y-2 border-t border-stone-200 pt-3">
        {run.events.length === 0 && <p className="text-stone-400">暂无可展示步骤。</p>}
        {run.events.map((event) => (
          <div key={`${event.eventType}-${event.id}`} className="flex items-start justify-between gap-3">
            <div>
              <p className="font-medium text-stone-700">{agentRunOperationLabel(event)}</p>
              {event.errorCode && <p className="mt-1 text-rose-600">{agentRunErrorLabel(event.errorCode)}</p>}
            </div>
            <span className="shrink-0 text-stone-400">{agentRunStatusLabel(event.status)}</span>
          </div>
        ))}
        {run.status === 'FAILED' && (
          <p className="rounded-md bg-rose-50 px-2 py-1.5 text-rose-700">
            {agentRunErrorLabel(run.errorCode)}
          </p>
        )}
        <div className="flex justify-end gap-2 pt-1">
          <button type="button" onClick={refresh} className="inline-flex items-center gap-1 text-stone-500">
            <RefreshCw className="h-3.5 w-3.5" />刷新
          </button>
          {run.retryable && onRetry && (
            <button type="button" onClick={onRetry} className="inline-flex items-center gap-1 text-sky-700">
              <RotateCcw className="h-3.5 w-3.5" />重新编辑并发送
            </button>
          )}
        </div>
      </div>
    </details>
  );
}
