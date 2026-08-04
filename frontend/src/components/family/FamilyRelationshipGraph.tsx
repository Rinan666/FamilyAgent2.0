'use client';

import { useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import Image from 'next/image';
import { Check, Network, UserRound, X } from 'lucide-react';
import { familyApi } from '@/lib/api';
import { cn } from '@/lib/utils';
import type { CareAuthorization, FamilyMember } from '@/types';
import PersonaMembersPanel from './PersonaMembersPanel';
import {
  memberAccountName,
  memberAvatarTone,
  memberDisplayName,
  memberInitial,
  memberProfileSummary,
} from './memberDisplay';

interface FamilyRelationshipGraphProps {
  familyId: number;
  members: FamilyMember[];
  currentUserId?: number;
  isOwner?: boolean;
  canEditRelationships?: boolean;
}

export default function FamilyRelationshipGraph({
  familyId,
  members,
  currentUserId,
  isOwner = false,
  canEditRelationships = false,
}: FamilyRelationshipGraphProps) {
  const [displayMembers, setDisplayMembers] = useState(members);
  const [editingMember, setEditingMember] = useState<FamilyMember | null>(null);
  const [draft, setDraft] = useState('');
  const [saving, setSaving] = useState(false);
  const [careAuthorizations, setCareAuthorizations] = useState<CareAuthorization[]>([]);
  const [careLoadingUserId, setCareLoadingUserId] = useState<number | null>(null);
  const [actionError, setActionError] = useState('');
  const viewer = displayMembers.find((member) => member.userId === currentUserId);
  const relatedMembers = useMemo(
    () => displayMembers.filter((member) => member.userId !== currentUserId),
    [currentUserId, displayMembers],
  );

  useEffect(() => setDisplayMembers(members), [members]);

  useEffect(() => {
    if (!currentUserId) return;
    familyApi
      .getMyCareAuthorizations(familyId)
      .then((items) => setCareAuthorizations(Array.isArray(items) ? items : []))
      .catch(() => setCareAuthorizations([]));
  }, [currentUserId, familyId]);

  const hasCareAuthorization = (caregiverUserId: number) => {
    const scopes = new Set(
      careAuthorizations
        .filter(
          (item) =>
            item.subjectUserId === currentUserId &&
            item.caregiverUserId === caregiverUserId &&
            item.status === 'ACTIVE',
        )
        .map((item) => item.scope),
    );
    return (
      scopes.has('ALL') || ['DIARY', 'MEMORY', 'GROWTH_GUARD'].every((scope) => scopes.has(scope))
    );
  };

  const toggleCareAuthorization = async (member: FamilyMember) => {
    if (!currentUserId || member.userId === currentUserId) return;
    const active = hasCareAuthorization(member.userId);
    setCareLoadingUserId(member.userId);
    setActionError('');
    try {
      const updated = await familyApi.upsertCareAuthorization(
        familyId,
        currentUserId,
        member.userId,
        { scope: 'ALL', active: !active },
      );
      setCareAuthorizations((current) => [
        ...current.filter(
          (item) =>
            !(item.subjectUserId === currentUserId && item.caregiverUserId === member.userId),
        ),
        updated,
      ]);
    } catch (err) {
      setActionError(err instanceof Error ? err.message : '照护权限更新失败');
    } finally {
      setCareLoadingUserId(null);
    }
  };

  const startEdit = (member: FamilyMember) => {
    if (!canEditRelationships) return;
    setEditingMember(member);
    setDraft(member.relationshipLabel?.trim() || '');
  };

  const saveRelationship = async () => {
    if (!editingMember || !draft.trim()) return;
    setSaving(true);
    setActionError('');
    try {
      const updated = await familyApi.upsertRelationshipLabel(familyId, editingMember.userId, {
        label: draft.trim(),
      });
      setDisplayMembers((current) =>
        current.map((member) =>
          member.userId === editingMember.userId
            ? {
                ...member,
                relationshipLabel: updated.label,
                reverseRelationshipLabel: updated.reverseLabel,
              }
            : member,
        ),
      );
      setEditingMember(null);
      setDraft('');
    } catch (err) {
      setActionError(err instanceof Error ? err.message : '关系备注保存失败');
    } finally {
      setSaving(false);
    }
  };

  if (!viewer) {
    return (
      <p className="rounded-2xl border border-dashed border-stone-200 px-4 py-10 text-center text-sm text-stone-400">
        关系图需要先加载当前成员。
      </p>
    );
  }

  return (
    <section className="glass-panel rounded-[26px] p-4 sm:p-6">
      <div className="mb-5 flex items-start gap-3">
        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-2xl bg-sky-100 text-sky-700">
          <Network className="h-5 w-5" />
        </div>
        <h4 className="self-center text-base font-semibold text-stone-950">成员关系图</h4>
      </div>

      <div className="rounded-[24px] border border-white/70 bg-white/28 p-4 sm:p-6">
        {actionError && (
          <div className="mb-4 rounded-2xl border border-red-100/80 bg-red-50/75 px-4 py-3 text-sm text-red-700">
            {actionError}
          </div>
        )}
        <div className="mb-5 flex items-center justify-between gap-3">
          <p className="text-xs font-semibold uppercase tracking-[0.18em] text-violet-600">
            精神成员
          </p>
        </div>
        <PersonaMembersPanel familyId={familyId} isOwner={isOwner} graphMode />

        <div className="mt-8 border-t border-white/70 pt-8">
          <div className="flex flex-col items-center">
            <MemberNode member={viewer} familyId={familyId} current label="我" />
            {relatedMembers.length > 0 && (
              <>
                <div className="h-8 w-px bg-sky-200" aria-hidden="true" />
                <div className="h-px w-[min(92%,58rem)] bg-sky-200" aria-hidden="true" />
                <div className="grid w-full grid-cols-2 gap-x-3 gap-y-5 pt-0 sm:grid-cols-3 lg:grid-cols-4">
                  {relatedMembers.map((member) => {
                    const relation = member.relationshipLabel?.trim() || '设置关系';
                    return (
                      <div
                        key={member.userId}
                        className="relative flex min-w-0 flex-col items-center pt-5"
                      >
                        <div
                          className="absolute left-1/2 top-0 h-5 w-px -translate-x-1/2 bg-sky-200"
                          aria-hidden="true"
                        />
                        {canEditRelationships ? (
                          <button
                            type="button"
                            onClick={() => startEdit(member)}
                            className="relative z-10 rounded-full border border-sky-200 bg-sky-50/90 px-2.5 py-1 text-[11px] font-medium text-sky-700 shadow-sm transition hover:border-sky-300 hover:bg-sky-100"
                          >
                            {relation}
                          </button>
                        ) : (
                          <span className="relative z-10 rounded-full border border-stone-200 bg-white/80 px-2.5 py-1 text-[11px] text-stone-500">
                            {relation}
                          </span>
                        )}
                        <MemberNode member={member} familyId={familyId} />
                        <button
                          type="button"
                          onClick={() => {
                            void toggleCareAuthorization(member);
                          }}
                          disabled={careLoadingUserId === member.userId}
                          aria-pressed={hasCareAuthorization(member.userId)}
                          className="mt-2 inline-flex items-center gap-1.5 rounded-full border border-stone-200 bg-white/75 px-2.5 py-1 text-[11px] font-medium text-stone-600 transition hover:border-sky-200 hover:bg-sky-50 hover:text-sky-800 disabled:cursor-not-allowed disabled:opacity-60"
                        >
                          <span
                            className={cn(
                              'relative h-3.5 w-6 rounded-full transition',
                              hasCareAuthorization(member.userId) ? 'bg-sky-600' : 'bg-stone-300',
                            )}
                          >
                            <span
                              className={cn(
                                'absolute top-0.5 h-2.5 w-2.5 rounded-full bg-white shadow transition',
                                hasCareAuthorization(member.userId) ? 'left-3' : 'left-0.5',
                              )}
                            />
                          </span>
                          {careLoadingUserId === member.userId
                            ? '更新中'
                            : hasCareAuthorization(member.userId)
                              ? '照护中'
                              : '照护权限'}
                        </button>
                      </div>
                    );
                  })}
                </div>
              </>
            )}
          </div>
        </div>
      </div>

      {editingMember && (
        <div className="mt-5 rounded-2xl border border-sky-100/80 bg-sky-50/65 p-4">
          <div className="flex items-center justify-between gap-3">
            <div>
              <p className="text-sm font-semibold text-sky-900">编辑关系备注</p>
              <p className="mt-1 text-xs text-sky-700">
                你对 {memberAccountName(editingMember)} 的称呼
              </p>
            </div>
            <button
              type="button"
              onClick={() => setEditingMember(null)}
              className="inline-flex h-8 w-8 items-center justify-center rounded-full text-sky-700 hover:bg-white/70"
              aria-label="关闭编辑"
            >
              <X className="h-4 w-4" />
            </button>
          </div>
          <div className="mt-3 flex flex-col gap-2 sm:flex-row">
            <input
              value={draft}
              onChange={(event) => setDraft(event.target.value)}
              maxLength={60}
              autoFocus
              placeholder="例如：妈妈、哥哥、外婆"
              className="glass-control min-w-0 flex-1 rounded-xl px-3 py-2.5 text-sm"
            />
            <button
              type="button"
              onClick={() => void saveRelationship()}
              disabled={saving || !draft.trim()}
              className="inline-flex items-center justify-center gap-1.5 rounded-full bg-sky-700 px-4 py-2.5 text-sm font-medium text-white disabled:opacity-50"
            >
              {saving ? (
                <span className="h-4 w-4 animate-spin rounded-full border-2 border-white/40 border-t-white" />
              ) : (
                <Check className="h-4 w-4" />
              )}
              保存备注
            </button>
          </div>
        </div>
      )}
    </section>
  );
}

function MemberNode({
  member,
  familyId,
  current = false,
  label,
}: {
  member: FamilyMember;
  familyId: number;
  current?: boolean;
  label?: string;
}) {
  const content = (
    <span
      className={cn(
        'flex h-32 w-32 flex-col items-center justify-center rounded-full border-4 bg-white/80 px-3 text-center shadow-[0_14px_34px_rgba(33,52,47,0.1)] transition',
        current
          ? 'border-sky-300 ring-4 ring-sky-100/70'
          : 'border-white/80 hover:-translate-y-1 hover:border-sky-200',
      )}
    >
      <span
        className={cn(
          'flex h-10 w-10 items-center justify-center rounded-full bg-gradient-to-br text-sm font-semibold',
          memberAvatarTone(member),
        )}
      >
        {member.avatarUrl ? (
          <Image
            src={member.avatarUrl}
            alt=""
            width={40}
            height={40}
            unoptimized
            className="h-full w-full rounded-full object-cover"
          />
        ) : (
          memberInitial(member) || <UserRound className="h-4 w-4" />
        )}
      </span>
      <span className="mt-2 max-w-full truncate text-xs font-semibold text-stone-900">
        {label || memberAccountName(member)}
      </span>
      <span className="max-w-full truncate text-[10px] text-stone-500">
        {memberDisplayName(member)}
      </span>
      <span className="mt-0.5 max-w-full truncate text-[10px] text-stone-400">
        {memberProfileSummary(member)}
      </span>
    </span>
  );

  return current ? (
    content
  ) : (
    <Link
      href={`/dashboard/family/member?familyId=${familyId}&userId=${member.userId}`}
      aria-label={`查看 ${memberAccountName(member)} 的记忆`}
    >
      {content}
    </Link>
  );
}
