'use client';

import Link from 'next/link';
import { BookHeart, Loader2, UserRound, X } from 'lucide-react';
import type { ActivationSceneState, ModeReadiness } from '@/components/agent/agentDisplay';
import type { AgentMode, FamilyMember, MirrorContextResponse } from '@/types';

interface AgentContextPanelProps {
  open: boolean;
  mode: AgentMode;
  targetLabel: string;
  targetUserId: number | null;
  selectorOptions: FamilyMember[];
  isLoadingMembers: boolean;
  activationScene: ActivationSceneState | null;
  modeReadiness: ModeReadiness;
  mirrorContext: MirrorContextResponse | null;
  isLoadingMirrorContext: boolean;
  contextError: string;
  activeFamilyId: number | null | undefined;
  onClose: () => void;
  onTargetChange: (nextTargetUserId: number | null) => void;
  onSuggestedQuestion: (question: string) => void;
}

function readinessToneClass(tone: ModeReadiness['tone']) {
  if (tone === 'green') return 'bg-emerald-100 text-emerald-800';
  if (tone === 'blue') return 'bg-sky-100 text-sky-800';
  if (tone === 'yellow') return 'bg-amber-100 text-amber-800';
  return 'bg-stone-200 text-stone-700';
}

export default function AgentContextPanel({
  open,
  mode,
  targetLabel,
  targetUserId,
  selectorOptions,
  isLoadingMembers,
  activationScene,
  modeReadiness,
  mirrorContext,
  isLoadingMirrorContext,
  contextError,
  activeFamilyId,
  onClose,
  onTargetChange,
  onSuggestedQuestion,
}: AgentContextPanelProps) {
  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50">
      <button
        type="button"
        className="absolute inset-0 bg-stone-950/24 backdrop-blur-sm"
        aria-label="关闭上下文面板"
        onClick={onClose}
      />

      <aside className="absolute inset-y-0 right-0 flex w-full max-w-md flex-col border-l border-white/80 bg-white/96 shadow-[-20px_0_70px_rgba(24,39,32,0.16)] backdrop-blur-xl">
        <div className="flex items-start justify-between gap-3 border-b border-stone-200/80 px-5 py-5">
          <div>
            <div className="text-sm font-semibold text-stone-900">上下文面板</div>
            <p className="mt-1 text-sm text-stone-500">
              {mode === 'mirror' ? '切换对象、查看镜像资料和快捷问题。' : '查看当前对象与家庭上下文摘要。'}
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="inline-flex h-10 w-10 items-center justify-center rounded-2xl text-stone-500 transition hover:bg-stone-100"
            aria-label="关闭上下文面板"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        <div className="flex-1 space-y-4 overflow-y-auto px-4 py-4">
          <div className="rounded-[24px] border border-stone-200 bg-stone-50/80 p-4">
            <div className="mb-2 flex items-center gap-2 text-sm font-semibold text-stone-900">
              <UserRound className="h-4 w-4 text-emerald-700" />
              当前对象
            </div>
            <p className="text-sm font-medium text-stone-900">{mode === 'mirror' ? targetLabel : '自己 / FamilyAgent'}</p>
            <p className="mt-1 text-xs leading-5 text-stone-500">
              {mode === 'mirror'
                ? '镜像模式只参考授权可见资料，并明确保留不确定性边界。'
                : '普通模式聚焦当前家庭共享记忆、成长记录、归档会话和长期上下文。'}
            </p>
          </div>

          <div className="rounded-[24px] border border-stone-200 bg-white p-4">
            <label htmlFor="agent-target-selector" className="text-sm font-semibold text-stone-900">
              对话对象
            </label>
            <p className="mt-1 text-xs leading-5 text-stone-500">
              切换到其他家庭成员后，将自动进入镜像参考模式。
            </p>
            <div className="mt-3 space-y-2">
              <select
                id="agent-target-selector"
                value={targetUserId ?? ''}
                onChange={(event) => onTargetChange(event.target.value ? Number(event.target.value) : null)}
                className="h-11 w-full rounded-2xl border border-stone-200 bg-stone-50/80 px-3 text-sm text-stone-800 outline-none transition focus:border-emerald-500 focus:bg-white focus:ring-4 focus:ring-emerald-100"
              >
                <option value="">自己 / FamilyAgent</option>
                {selectorOptions.map((member) => (
                  <option key={member.userId} value={member.userId}>
                    {member.relationshipLabel || member.nickname || member.username || `用户 ${member.userId}`}
                  </option>
                ))}
              </select>
              <div className="rounded-2xl bg-stone-50 px-3 py-2 text-xs text-stone-500">
                {isLoadingMembers
                  ? '正在加载家庭成员...'
                  : selectorOptions.length > 0
                    ? `可切换 ${selectorOptions.length} 位家庭成员`
                    : '当前没有其他可切换的家庭成员'}
              </div>
            </div>
          </div>

          {mode === 'family' ? (
            <>
              {activationScene && (
                <div className="rounded-[24px] border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">
                  <div className="font-semibold">已激活上下文：{activationScene.label}</div>
                  <div className="mt-2 text-xs leading-6 text-amber-800">{activationScene.instruction}</div>
                </div>
              )}
              <div className="rounded-[24px] border border-emerald-100 bg-emerald-50/80 p-4">
                <div className="flex items-center gap-2 text-sm font-semibold text-emerald-900">
                  <BookHeart className="h-4 w-4" />
                  FamilyAgent 如何工作
                </div>
                <p className="mt-2 text-xs leading-6 text-emerald-800">
                  系统会优先综合家庭记忆、归档会话、记忆库、成长守护与传承任务，尽量让建议落在当前家庭语境中。
                </p>
              </div>
            </>
          ) : (
            <>
              {contextError && (
                <div className="rounded-[24px] border border-rose-200 bg-rose-50 p-4 text-xs leading-6 text-rose-700">
                  {contextError}
                </div>
              )}

              <div className="rounded-[24px] border border-emerald-100 bg-emerald-50/80 p-4">
                <div className="flex items-center justify-between gap-2">
                  <div className="text-sm font-semibold text-emerald-900">资料充分度</div>
                  <span className={`rounded-full px-2.5 py-1 text-[11px] font-medium ${readinessToneClass(modeReadiness.tone)}`}>
                    {modeReadiness.label}
                  </span>
                </div>
                <p className="mt-2 text-xs leading-6 text-emerald-800">
                  {isLoadingMirrorContext
                    ? '正在刷新镜像资料...'
                    : mirrorContext?.sourceSummary || '当前还没有可展示的镜像来源摘要。'}
                </p>
              </div>

              {mirrorContext?.disclaimer && (
                <div className="rounded-[24px] border border-amber-200 bg-amber-50 p-4 text-xs leading-6 text-amber-800">
                  {mirrorContext.disclaimer}
                </div>
              )}

              {!!mirrorContext?.suggestedQuestions?.length && (
                <div className="rounded-[24px] border border-stone-200 bg-white p-4">
                  <div className="text-sm font-semibold text-stone-900">快捷提问</div>
                  <div className="mt-3 space-y-2">
                    {mirrorContext.suggestedQuestions.slice(0, 4).map((question) => (
                      <button
                        key={question}
                        type="button"
                        onClick={() => onSuggestedQuestion(question)}
                        className="w-full rounded-2xl border border-stone-200 bg-stone-50/70 px-3 py-2.5 text-left text-xs leading-5 text-stone-600 transition hover:border-emerald-200 hover:bg-emerald-50 hover:text-emerald-800"
                      >
                        {question}
                      </button>
                    ))}
                  </div>
                </div>
              )}

              {!!mirrorContext?.missingRecordSuggestions?.length && (
                <details className="rounded-[24px] border border-dashed border-stone-300 bg-white p-4">
                  <summary className="cursor-pointer list-none text-sm font-semibold text-stone-900">
                    让镜像更准确
                  </summary>
                  <div className="mt-3 space-y-2">
                    {mirrorContext.missingRecordSuggestions.slice(0, 3).map((suggestion) => (
                      <p key={suggestion} className="text-xs leading-6 text-stone-600">
                        {suggestion}
                      </p>
                    ))}
                  </div>
                </details>
              )}

              <div className="rounded-[24px] border border-stone-200 bg-stone-50/80 p-4 text-xs text-stone-600">
                <div className="font-semibold text-stone-900">快速入口</div>
                <div className="mt-3 space-y-2">
                  <Link
                    href={`/dashboard/family/member?familyId=${activeFamilyId}&userId=${targetUserId || ''}`}
                    className="block rounded-2xl border border-stone-200 bg-white px-3 py-2.5 text-stone-700 transition hover:border-emerald-200 hover:bg-emerald-50 hover:text-emerald-800"
                  >
                    查看成员授权资料
                  </Link>
                  <Link
                    href={`/dashboard/diary?familyId=${activeFamilyId}${targetUserId ? `&relatedUserId=${targetUserId}&relatedMemberName=${encodeURIComponent(targetLabel)}` : ''}`}
                    className="block rounded-2xl border border-stone-200 bg-white px-3 py-2.5 text-stone-700 transition hover:border-emerald-200 hover:bg-emerald-50 hover:text-emerald-800"
                  >
                    去补充相关记录
                  </Link>
                </div>
              </div>
            </>
          )}
        </div>
      </aside>
    </div>
  );
}
