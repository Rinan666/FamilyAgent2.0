'use client';

import Link from 'next/link';
import { CheckCircle, Loader2, Save, Sparkles } from 'lucide-react';
import MathRenderer from '@/components/agent/MathRenderer';
import RagMemoryBadge from '@/components/agent/RagMemoryBadge';
import WebSearchBadge from '@/components/agent/WebSearchBadge';
import AnswerEvidenceDisclosure from '@/components/agent/AnswerEvidenceDisclosure';
import { formatSessionTime, type SaveFeedback } from '@/components/agent/agentDisplay';
import type { AgentMode, ChatMessage } from '@/types';

interface AgentMessageListProps {
  messages: ChatMessage[];
  isLoadingMessages: boolean;
  mode: AgentMode;
  targetLabel: string;
  saveFeedback: Record<string, SaveFeedback>;
  onSaveMessage: (message: ChatMessage) => void;
  onOpenContext?: () => void;
}

export default function AgentMessageList({
  messages,
  isLoadingMessages,
  mode,
  targetLabel,
  saveFeedback,
  onSaveMessage,
  onOpenContext,
}: AgentMessageListProps) {
  if (!isLoadingMessages && messages.length === 0) {
    return (
      <div className="mx-auto flex w-full max-w-3xl flex-1 items-center px-2 py-8">
        <div className="w-full rounded-[28px] border border-dashed border-stone-300 bg-white/88 px-6 py-12 text-center shadow-[0_16px_40px_rgba(24,39,32,0.05)]">
          <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-emerald-100 text-emerald-800">
            <Sparkles className="h-6 w-6" />
          </div>
          <h3 className="mt-5 text-xl font-semibold text-stone-950">
            {mode === 'mirror' ? `开始与 ${targetLabel} 的镜像参考对话` : '开始一段家庭对话'}
          </h3>
          <p className="mx-auto mt-3 max-w-2xl text-sm leading-7 text-stone-500">
            {mode === 'mirror'
              ? '你可以从关系、照护、选择和家族经验切入，系统会在授权范围内给出更贴近这位成员经历的参考。'
              : '可以聊家庭记忆、成长观察、关系沟通、照护安排，或任何值得长期留存的内容。'}
          </p>
          {onOpenContext && (
            <div className="mt-6">
              <button
                type="button"
                onClick={onOpenContext}
                className="inline-flex h-11 items-center rounded-full border border-stone-200 bg-white px-4 text-sm font-medium text-stone-700 transition hover:border-stone-300 hover:bg-stone-50"
              >
                查看上下文
              </button>
            </div>
          )}
        </div>
      </div>
    );
  }

  return (
    <div className="flex-1 overflow-y-auto bg-[linear-gradient(180deg,rgba(255,255,255,0.08),rgba(255,255,255,0.42))] px-4 py-5 md:px-6">
      <div className="mx-auto max-w-3xl space-y-4">
        {isLoadingMessages && (
          <div className="rounded-2xl border border-stone-200 bg-white/80 px-4 py-3 text-center text-sm text-stone-500">
            <Loader2 className="mx-auto mb-2 h-4 w-4 animate-spin" />
            正在恢复会话...
          </div>
        )}

        {messages.map((message) => {
          const feedback = saveFeedback[message.id];
          if (message.role === 'system') {
            return (
              <div key={message.id} className="text-center">
                <div className="inline-flex max-w-2xl rounded-full border border-amber-200 bg-amber-50 px-4 py-2 text-xs leading-5 text-amber-800">
                  {message.content}
                </div>
              </div>
            );
          }

          const isAssistant = message.role === 'assistant';
          const isMirrorAssistant = message.metadata?.agentMode === 'mirror' || Boolean(message.metadata?.sourceRefs?.length);

          return (
            <div
              key={message.id}
              className={`rounded-[26px] border px-4 py-4 shadow-[0_14px_34px_rgba(24,39,32,0.05)] ${
                isAssistant ? 'border-white/80 bg-white/92 text-stone-900' : 'border-stone-800 bg-stone-950 text-white'
              }`}
            >
              <div className="mb-3 flex items-center justify-between gap-3">
                <div className={`text-xs font-semibold ${isAssistant ? 'text-emerald-700' : 'text-stone-200'}`}>
                  {isAssistant ? (isMirrorAssistant ? 'MirrorAgent' : 'FamilyAgent') : '你'}
                </div>
                <div className={`text-[11px] ${isAssistant ? 'text-stone-400' : 'text-stone-400'}`}>
                  {formatSessionTime(message.timestamp)}
                </div>
              </div>

              <div className={`text-sm leading-7 ${isAssistant ? 'text-stone-800' : 'text-white'}`}>
                {isAssistant ? <MathRenderer content={message.content} /> : message.content}
              </div>

              {isAssistant && message.metadata?.thinkingSummary && (
                <div className="mt-3 rounded-2xl border border-emerald-100 bg-emerald-50/80 px-3 py-2 text-xs leading-6 text-emerald-800">
                  <span className="font-medium">思路摘要：</span>
                  {message.metadata.thinkingSummary}
                </div>
              )}

              {isAssistant && <RagMemoryBadge metadata={message.metadata} />}
              {isAssistant && <WebSearchBadge metadata={message.metadata} />}
              {isAssistant && isMirrorAssistant && <AnswerEvidenceDisclosure message={message} />}

              <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
                <button
                  type="button"
                  onClick={() => { onSaveMessage(message); }}
                  disabled={feedback?.status === 'saving'}
                  className={`inline-flex items-center gap-1 rounded-full border px-3 py-1.5 text-xs font-medium transition ${
                    isAssistant
                      ? 'border-stone-200 text-stone-600 hover:bg-stone-100'
                      : 'border-white/15 text-stone-100 hover:bg-white/10'
                  }`}
                >
                  {feedback?.status === 'saving'
                    ? <Loader2 className="h-3.5 w-3.5 animate-spin" />
                    : <Save className="h-3.5 w-3.5" />}
                  保存这条消息
                </button>

                {feedback && (
                  <div className={`text-xs ${feedback.status === 'error' ? 'text-rose-600' : 'text-emerald-700'}`}>
                    {feedback.status === 'saved' && <CheckCircle className="mr-1 inline h-3.5 w-3.5" />}
                    {feedback.detail}
                    {feedback.href && (
                      <Link href={feedback.href} className="ml-2 underline underline-offset-2">
                        打开
                      </Link>
                    )}
                  </div>
                )}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
