'use client';

import Link from 'next/link';
import { CheckCircle, Loader2, Sparkles } from 'lucide-react';
import MathRenderer from '@/components/agent/MathRenderer';
import AnswerEvidenceDisclosure from '@/components/agent/AnswerEvidenceDisclosure';
import SaveDraftCard from '@/components/agent/SaveDraftCard';
import { type SaveFeedback } from '@/components/agent/agentDisplay';
import type { AgentMode, AgentSaveToolPlan, ChatMessage } from '@/types';

interface AgentMessageListProps {
  messages: ChatMessage[];
  isLoadingMessages: boolean;
  isStreaming: boolean;
  mode: AgentMode;
  targetLabel: string;
  saveFeedback: Record<string, SaveFeedback>;
  onConfirmSaveDraft: (message: ChatMessage, plan: AgentSaveToolPlan) => void;
  onCancelSaveDraft: (message: ChatMessage) => void;
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

export default function AgentMessageList({
  messages,
  isLoadingMessages,
  isStreaming,
  mode,
  targetLabel,
  saveFeedback,
  onConfirmSaveDraft,
  onCancelSaveDraft,
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
    <div className="min-h-0 flex-1 overflow-y-auto px-5 py-5">
      <div className="mx-auto max-w-4xl space-y-7">
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
                <div className="inline-flex max-w-3xl rounded-full bg-stone-100/80 px-3 py-1.5 text-[11px] leading-5 text-stone-500">
                  {message.content}
                </div>
              </div>
            );
          }

          const isAssistant = message.role === 'assistant';
          const showThinkingIndicator = isAssistant && isStreaming && !message.content.trim();

          return (
            <div
              key={message.id}
              className={`flex ${isAssistant ? 'justify-start' : 'justify-end'}`}
            >
              <div
                className={
                  isAssistant
                    ? 'max-w-full rounded-md px-0 py-1 text-stone-900'
                    : 'max-w-[78%] rounded-[22px] bg-blue-50 px-4 py-3 text-stone-900'
                }
              >
                {isAssistant && !showThinkingIndicator && (
                  <div className="mb-2 inline-flex items-center gap-1.5 text-xs font-medium text-stone-400">
                    <span>已思考</span>
                    <span aria-hidden>›</span>
                  </div>
                )}

                <div
                  className={
                    isAssistant
                      ? 'whitespace-pre-wrap text-[17px] leading-8 text-stone-900'
                      : 'whitespace-pre-wrap text-base leading-7 text-stone-900'
                  }
                >
                  {showThinkingIndicator ? (
                    <AssistantThinkingIndicator />
                  ) : isAssistant ? (
                    <MathRenderer content={message.content} />
                  ) : (
                    message.content
                  )}
                </div>

                {isAssistant && message.metadata?.thinkingSummary && (
                  <div className="mt-3 rounded-md border border-emerald-100 bg-emerald-50/80 px-3 py-2 text-xs leading-6 text-emerald-800">
                    <span className="font-medium">思路摘要：</span>
                    {message.metadata.thinkingSummary}
                  </div>
                )}

                {isAssistant && <AnswerEvidenceDisclosure message={message} />}

                {feedback && (
                  <div
                    className={`mt-4 text-xs ${
                      feedback.status === 'error'
                        ? 'text-rose-600'
                        : feedback.status === 'skipped'
                          ? 'text-stone-500'
                          : 'text-emerald-700'
                    }`}
                  >
                    {feedback.status === 'saving' || feedback.status === 'confirming' ? (
                      <Loader2 className="mr-1 inline h-3.5 w-3.5 animate-spin" />
                    ) : feedback.status === 'saved' ? (
                      <CheckCircle className="mr-1 inline h-3.5 w-3.5" />
                    ) : null}
                    {feedback.detail}
                    {feedback.href && (
                      <Link href={feedback.href} className="ml-2 underline underline-offset-2">
                        打开
                      </Link>
                    )}
                    {feedback.draft && (feedback.status === 'draft' || feedback.status === 'confirming' || feedback.status === 'error') && (
                      <SaveDraftCard
                        plan={feedback.draft}
                        isConfirming={feedback.status === 'confirming'}
                        onConfirm={(plan) => onConfirmSaveDraft(message, plan)}
                        onCancel={() => onCancelSaveDraft(message)}
                      />
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
