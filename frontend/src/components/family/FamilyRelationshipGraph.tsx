'use client';

import { Network, UserRound } from 'lucide-react';
import { cn } from '@/lib/utils';
import type { FamilyMember } from '@/types';
import { memberAccountName, memberAvatarTone, memberInitial } from './memberDisplay';

interface FamilyRelationshipGraphProps {
  members: FamilyMember[];
  currentUserId?: number;
}

export default function FamilyRelationshipGraph({
  members,
  currentUserId,
}: FamilyRelationshipGraphProps) {
  const viewer = members.find((member) => member.userId === currentUserId);
  if (!viewer) return null;

  const relatedMembers = members.filter((member) => member.userId !== currentUserId);

  return (
    <section className="mb-5 rounded-xl border border-emerald-100 bg-emerald-50/40 p-4 sm:p-5">
      <div className="mb-4 flex items-start gap-3">
        <div className="rounded-lg bg-emerald-800 p-2 text-white">
          <Network className="h-4 w-4" />
        </div>
        <div>
          <h4 className="text-sm font-semibold text-stone-900">我的家族关系图</h4>
          <p className="mt-0.5 text-xs leading-5 text-stone-500">
            连线称呼以你的视角显示，AI 也会按这个方向理解记忆作者。
          </p>
        </div>
      </div>

      <div className="mx-auto flex max-w-4xl flex-col items-center">
        <MemberNode member={viewer} label="我" emphasized />
        <div className="h-5 w-px bg-emerald-200" aria-hidden="true" />
        <div className="h-px w-[min(88%,40rem)] bg-emerald-200" aria-hidden="true" />

        {relatedMembers.length === 0 ? (
          <p className="mt-4 text-xs text-stone-500">邀请家族成员后，关系会显示在这里。</p>
        ) : (
          <div className="grid w-full gap-3 pt-4 sm:grid-cols-2 lg:grid-cols-3">
            {relatedMembers.map((member) => (
              <div key={member.userId} className="relative pt-2">
                <div
                  className="absolute left-1/2 top-0 h-2 w-px -translate-x-1/2 bg-emerald-200"
                  aria-hidden="true"
                />
                <MemberNode
                  member={member}
                  label={member.relationshipLabel?.trim() || '未设置称呼'}
                />
              </div>
            ))}
          </div>
        )}
      </div>
    </section>
  );
}

function MemberNode({
  member,
  label,
  emphasized = false,
}: {
  member: FamilyMember;
  label: string;
  emphasized?: boolean;
}) {
  return (
    <div
      className={cn(
        'flex min-w-0 items-center gap-3 rounded-xl border bg-white px-3 py-3 shadow-sm',
        emphasized ? 'border-emerald-300 ring-2 ring-emerald-100' : 'border-stone-200',
      )}
    >
      <div
        className={cn(
          'flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-gradient-to-br text-sm font-semibold',
          memberAvatarTone(member),
        )}
      >
        {memberInitial(member) || <UserRound className="h-4 w-4" />}
      </div>
      <div className="min-w-0">
        <p className="truncate text-sm font-semibold text-stone-800">{label}</p>
        <p className="truncate text-xs text-stone-500">{memberAccountName(member)}</p>
      </div>
    </div>
  );
}
