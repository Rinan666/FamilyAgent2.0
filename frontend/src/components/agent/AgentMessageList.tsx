'use client';

import Link from 'next/link';
import { CheckCircle, Loader2, Save, Sparkles } from 'lucide-react';
import MathRenderer from '@/components/agent/MathRenderer';
import RagMemoryBadge from '@/components/agent/RagMemoryBadge';
import WebSearchBadge from '@/components/agent/WebSearchBadge';
import AnswerEvidenceDisclosure from '@/components/agent/AnswerEvidenceDisclosure';
import { type SaveFeedback } from '@/components/agent/agentDisplay';
import type { AgentMode, ChatMessage } from '@/types';

interface AgentMessageListProps {
  messages: ChatMessage[];
  isLoadingMessages: boolean;
  isStreaming: boolean;
  mode: AgentMode;
  targetLabel: string;
  saveFeedback: Record<string, SaveFeedback>;
  onSaveMessage: (message: ChatMessage) => void;
  onOpenContext?: () => void;
}

function AssistantThinkingIndicator() {
  return (
    <div
      className="inline-flex items-center gap-2 rounded-md bg-stone-50 px-3 py-2 text-sm text-stone-500"
      aria-live="polite"
    >
      <Loader2 className="h-4 w-4 animate-spin text-emerald-700" />
      <span>AI 正在思考...</span>
    </div>
  );
}

function metadataLabel(value: unknown) {
  return typeof value === 'string' && value.trim() ? value.trim() : '';
}

function assistantDisplayName(message: ChatMessage) {
  const metadata = message.metadata;
  if (!metadata) {
    return 'FamilyAgent';
  }
  if (metadata?.agentMode === 'persona') {
    return metadataLabel(metadata.targetPersonaName)
      || metadataLabel(metadata.targetMemberName)
      || 'PersonaMemberAgent';
  }
  if (metadata?.agentMode === 'mirror' || Boolean(metadata?.sourceRefs?.length)) {
    return metadataLabel(metadata.targetMemberName) || 'MirrorAgent';
  }
  return 'FamilyAgent';
}

export default function AgentMessageList({
  messages,
  isLoadingMessages,
  isStreaming,
  mode,
  targetLabel,
  saveFeedback,
  onSaveMessage,
  onOpenContext,
}: AgentMessageListProps) {
  if (!isLoadingMessages && messages.length === 0) {
    return (
      <div className="mx-auto flex w-full max-w-5xl min-h-0 flex-1 items-center justify-center overflow-y-auto px-4 py-8">
        <div className="w-full px-4 py-8 text-center">
          <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-md bg-emerald-100 text-emerald-800 shadow-sm">
            <Sparkles className="h-6 w-6" />
          </div>
          <h3 className="mt-5 text-2xl font-semibold text-stone-950">
            {mode === 'mirror'
              ? `开始与 ${targetLabel} 的镜像参考对话`
              : mode === 'persona'
                ? `开始请教 ${targetLabel}`
                : '开始一段家庭对话'}
          </h3>
          <p className="mx-auto mt-3 max-w-2xl text-sm leading-7 text-stone-500">
            {mode === 'mirror'
              ? '镜像 AI 会参考上下文、授权日常记录和成长观察，不使用家族经验沉淀。'
              : mode === 'persona'
                ? '精神成员会基于家族创建的档案和当前可见家庭经验，以稳定角色声音提供建议。'
                : 'FamilyAgent 会参考当前上下文和家族经验沉淀，不召回日常记录或成长观察。'}
          </p>
          {onOpenContext && (
            <div className="mt-6">
              <button
                type="button"
                onClick={onOpenContext}
                className="inline-flex h-10 items-center rounded-full border border-emerald-200 bg-emerald-50 px-4 text-sm font-medium text-emerald-800 transition hover:bg-emerald-100"
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
    <div className="min-h-0 flex-1 overflow-y-auto px-3 py-4 md:px-5">
      <div className="mx-auto max-w-4xl space-y-4">
        {isLoadingMessages && (
          <div className="rounded-md border border-stone-200 bg-white px-4 py-3 text-center text-sm text-stone-500">
            <Loader2 className="mx-auto mb-2 h-4 w-4 animate-spin" />
            正在恢复会话...
          </div>
        )}

        {messages.map((message) => {
          const feedback = saveFeedback[message.id];
          if (message.role === 'system') {
            return (
              <div key={message.id} className="text-center">
                <div className="inline-flex max-w-3xl rounded-md border border-amber-200 bg-amber-50 px-4 py-2 text-xs leading-5 text-amber-800">
                  {message.content}
                </div>
              </div>
            );
          }

          const isAssistant = message.role === 'assistant';
          const isMirrorAssistant = message.metadata?.agentMode === 'mirror' || Boolean(message.metadata?.sourceRefs?.length);
          const displayName = isAssistant ? assistantDisplayName(message) : '你';
          const showThinkingIndicator = isAssistant && isStreaming && !message.content.trim();
          const showSaveControls = !isStreaming && Boolean(message.content.trim());

          return (
            <div
              key={message.id}
              className={`flex ${isAssistant ? 'justify-start' : 'justify-end'}`}
            >
              <div
                className={isAssistant
                  ? 'max-w-[96%] rounded-md px-1 py-2 text-stone-900 md:max-w-[88%]'
                  : 'max-w-[96%] rounded-md bg-emerald-50 px-4 py-3 text-stone-900 ring-1 ring-emerald-100 md:max-w-[76%]'}
              >
                <div className="mb-3 flex items-center">
                  <div className={`text-xs font-semibold ${isAssistant ? 'text-emerald-700' : 'text-emerald-800'}`}>
                    {displayName}
                  </div>
                </div>

                <div className="whitespace-pre-wrap text-sm leading-7 text-stone-800">
                  {showThinkingIndicator
                    ? <AssistantThinkingIndicator />
                    : isAssistant
                      ? <MathRenderer content={message.content} />
                      : message.content}
                </div>

                {isAssistant && message.metadata?.thinkingSummary && (
                  <div className="mt-3 rounded-md border border-emerald-100 bg-emerald-50/80 px-3 py-2 text-xs leading-6 text-emerald-800">
                    <span className="font-medium">思路摘要：</span>
                    {message.metadata.thinkingSummary}
                  </div>
                )}

                {isAssistant && <RagMemoryBadge metadata={message.metadata} />}
                {isAssistant && <WebSearchBadge metadata={message.metadata} />}
                {isAssistant && isMirrorAssistant && <AnswerEvidenceDisclosure message={message} />}

                {showSaveControls && (
                <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
                  <button
                    type="button"
                    onClick={() => { onSaveMessage(message); }}
                    disabled={feedback?.status === 'saving'}
                    className="inline-flex items-center gap-1 rounded-md border border-stone-200 bg-white px-2.5 py-1 text-xs font-medium text-stone-600 transition hover:bg-stone-50 disabled:opacity-60"
                  >
                    {feedback?.status === 'saving'
                      ? <Loader2 className="h-3.5 w-3.5 animate-spin" />
                      : <Save className="h-3.5 w-3.5" />}
                    智能保存
                  </button>

                  {feedback && (
                    <div className={`text-xs ${
                      feedback.status === 'error'
                        ? 'text-rose-600'
                        : feedback.status === 'skipped'
                          ? 'text-stone-500'
                          : 'text-emerald-700'
                    }`}>
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
                )}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
