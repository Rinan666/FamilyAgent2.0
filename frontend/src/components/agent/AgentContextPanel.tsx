'use client';

import Link from 'next/link';
import { Check, House, Sparkles, UsersRound, X } from 'lucide-react';
import type { AgentTargetSelection } from '@/components/agent/agentTarget';
import type { ModeReadiness } from '@/components/agent/agentDisplay';
import type { AgentMode, FamilyMember, MirrorContextResponse, PersonaMember } from '@/types';

interface AgentContextPanelProps {
  open: boolean;
  mode: AgentMode;
  familyName: string;
  targetLabel: string;
  selfTargetLabel: string;
  targetSelection: AgentTargetSelection;
  selectorOptions: FamilyMember[];
  personaOptions: PersonaMember[];
  targetPersona: PersonaMember | null;
  isLoadingMembers: boolean;
  modeReadiness: ModeReadiness;
  mirrorContext: MirrorContextResponse | null;
  contextError: string;
  activeFamilyId: number | null | undefined;
  onClose: () => void;
  onTargetChange: (nextTargetSelection: AgentTargetSelection) => void;
}

function readinessToneClass(tone: ModeReadiness['tone']) {
  if (tone === 'green') return 'bg-emerald-100 text-emerald-800';
  if (tone === 'blue') return 'bg-sky-100 text-sky-800';
  if (tone === 'yellow') return 'bg-amber-100 text-amber-800';
  return 'bg-stone-200 text-stone-700';
}

function targetValue(selection: AgentTargetSelection) {
  return typeof selection === 'number' ? String(selection) : selection;
}

function memberOptionLabel(member: FamilyMember) {
  return member.username?.trim()
    || member.nickname?.trim()
    || member.relationshipLabel?.trim()
    || `用户 ${member.userId}`;
}

export default function AgentContextPanel({
  open,
  mode,
  familyName,
  targetLabel,
  selfTargetLabel,
  targetSelection,
  selectorOptions,
  personaOptions,
  targetPersona,
  isLoadingMembers,
  modeReadiness,
  mirrorContext,
  contextError,
  activeFamilyId,
  onClose,
  onTargetChange,
}: AgentContextPanelProps) {
  if (!open) return null;

  const selectedValue = targetValue(targetSelection);
  const familyMemberCount = selectorOptions.length + 1;
  const handleTargetChange = (value: AgentTargetSelection) => {
    onTargetChange(value);
  };

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
              <House className="h-4 w-4 text-emerald-700" />
              当前家族
            </div>
            <p className="text-sm font-medium text-stone-900">{familyName}</p>
          </div>

          <div className="rounded-[24px] border border-stone-200 bg-white p-4">
            <div className="flex items-center justify-between gap-3">
              <div>
                <div className="text-sm font-semibold text-stone-900">说话对象</div>
                <div className="mt-1 text-xs text-stone-500">当前：{targetLabel}</div>
              </div>
              <span className="rounded-full bg-emerald-50 px-2.5 py-1 text-[11px] font-medium text-emerald-700">
                {mode === 'persona' ? '精神成员' : mode === 'mirror' ? '镜像参考' : '家庭 Agent'}
              </span>
            </div>

            <div className="mt-4 space-y-3">
              <button
                type="button"
                onClick={() => handleTargetChange('NONE')}
                className={`flex w-full items-center justify-between gap-3 rounded-2xl border px-3 py-3 text-left transition ${
                  selectedValue === 'NONE'
                    ? 'border-emerald-300 bg-emerald-50 text-emerald-900'
                    : 'border-stone-200 bg-stone-50/80 text-stone-700 hover:border-emerald-200 hover:bg-emerald-50/70'
                }`}
              >
                <span>
                  <span className="block text-sm font-semibold">家庭 Agent</span>
                  <span className="mt-1 block text-xs text-stone-500">不指定成员，使用当前家族上下文</span>
                </span>
                {selectedValue === 'NONE' && <Check className="h-4 w-4 shrink-0 text-emerald-700" />}
              </button>

              <div className="space-y-2">
                <div className="flex items-center gap-2 px-1 text-xs font-semibold text-stone-500">
                  <UsersRound className="h-3.5 w-3.5" />
                  家庭成员
                </div>
                <button
                  type="button"
                  onClick={() => handleTargetChange('SELF')}
                  className={`flex w-full items-center justify-between gap-3 rounded-2xl border px-3 py-3 text-left transition ${
                    selectedValue === 'SELF'
                      ? 'border-emerald-300 bg-emerald-50 text-emerald-900'
                      : 'border-stone-200 bg-white text-stone-700 hover:border-emerald-200 hover:bg-emerald-50/70'
                  }`}
                >
                  <span>
                    <span className="block text-sm font-semibold">{selfTargetLabel}</span>
                    <span className="mt-1 block text-xs text-stone-500">镜像自己</span>
                  </span>
                  {selectedValue === 'SELF' && <Check className="h-4 w-4 shrink-0 text-emerald-700" />}
                </button>
                {selectorOptions.map((member) => {
                  const value = String(member.userId);
                  return (
                    <button
                      key={member.userId}
                      type="button"
                      onClick={() => handleTargetChange(member.userId)}
                      className={`flex w-full items-center justify-between gap-3 rounded-2xl border px-3 py-3 text-left transition ${
                        selectedValue === value
                          ? 'border-emerald-300 bg-emerald-50 text-emerald-900'
                          : 'border-stone-200 bg-white text-stone-700 hover:border-emerald-200 hover:bg-emerald-50/70'
                      }`}
                    >
                      <span>
                        <span className="block text-sm font-semibold">{memberOptionLabel(member)}</span>
                        {member.relationshipLabel && member.relationshipLabel !== memberOptionLabel(member) && (
                          <span className="mt-1 block text-xs text-stone-500">{member.relationshipLabel}</span>
                        )}
                      </span>
                      {selectedValue === value && <Check className="h-4 w-4 shrink-0 text-emerald-700" />}
                    </button>
                  );
                })}
              </div>

              {personaOptions.length > 0 && (
                <div className="space-y-2">
                  <div className="flex items-center gap-2 px-1 text-xs font-semibold text-stone-500">
                    <Sparkles className="h-3.5 w-3.5" />
                    精神成员
                  </div>
                  {personaOptions.map((persona) => {
                    const value = `PERSONA:${persona.id}` as AgentTargetSelection;
                    const stringValue = targetValue(value);
                    return (
                      <button
                        key={persona.id}
                        type="button"
                        onClick={() => handleTargetChange(value)}
                        className={`flex w-full items-center justify-between gap-3 rounded-2xl border px-3 py-3 text-left transition ${
                          selectedValue === stringValue
                            ? 'border-violet-300 bg-violet-50 text-violet-900'
                            : 'border-stone-200 bg-white text-stone-700 hover:border-violet-200 hover:bg-violet-50/70'
                        }`}
                      >
                        <span className="text-sm font-semibold">{persona.name}</span>
                        {selectedValue === stringValue && <Check className="h-4 w-4 shrink-0 text-violet-700" />}
                      </button>
                    );
                  })}
                </div>
              )}

              <div className="rounded-2xl bg-stone-50 px-3 py-2 text-xs text-stone-500">
                {isLoadingMembers
                  ? '正在加载对话对象...'
                  : familyMemberCount + personaOptions.length > 0
                    ? `可切换 ${familyMemberCount} 位家庭成员、${personaOptions.length} 位精神成员`
                    : '当前没有其他可切换的对话对象'}
              </div>
            </div>
          </div>

          {mode === 'family' ? (
            null
          ) : mode === 'persona' ? (
            <div className="rounded-[24px] border border-violet-100 bg-violet-50/80 p-4 text-xs leading-6 text-violet-900">
              <div className="text-sm font-semibold">精神成员档案</div>
              <div className="mt-3 space-y-2">
                {targetPersona?.eraIdentity && <p>身份：{targetPersona.eraIdentity}</p>}
                {targetPersona?.description && <p>简介：{targetPersona.description}</p>}
                {targetPersona?.values && <p>价值观：{targetPersona.values}</p>}
                {targetPersona?.speakingStyle && <p>说话风格：{targetPersona.speakingStyle}</p>}
                {targetPersona?.personality && <p>性格气质：{targetPersona.personality}</p>}
              </div>
              <Link
                href={`/dashboard/family?tab=personas${activeFamilyId ? `&familyId=${activeFamilyId}` : ''}`}
                className="mt-4 block rounded-2xl border border-violet-200 bg-white px-3 py-2.5 text-violet-700 transition hover:border-violet-300 hover:bg-violet-50"
              >
                管理精神成员
              </Link>
            </div>
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
              </div>

              <div className="rounded-[24px] border border-stone-200 bg-stone-50/80 p-4 text-xs text-stone-600">
                <div className="font-semibold text-stone-900">快速入口</div>
                <div className="mt-3 space-y-2">
                  <Link
                    href={`/dashboard/family/member?familyId=${activeFamilyId}&userId=${mirrorContext?.targetMember?.userId || ''}`}
                    className="block rounded-2xl border border-stone-200 bg-white px-3 py-2.5 text-stone-700 transition hover:border-emerald-200 hover:bg-emerald-50 hover:text-emerald-800"
                  >
                    查看成员授权资料
                  </Link>
                  <Link
                    href={`/dashboard/diary?familyId=${activeFamilyId}${mirrorContext?.targetMember?.userId ? `&relatedUserId=${mirrorContext.targetMember.userId}&relatedMemberName=${encodeURIComponent(targetLabel)}` : ''}`}
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
