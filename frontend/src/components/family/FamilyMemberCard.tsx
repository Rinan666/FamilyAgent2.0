'use client';

import Link from 'next/link';
import Image from 'next/image';
import { BookHeart, Crown, Eye, Pencil, RefreshCw, User, X } from 'lucide-react';
import { familyRoleLabel } from '@/lib/roles';
import { cn } from '@/lib/utils';
import type { FamilyMember } from '@/types';
import {
  memberAccountName,
  memberAvatarTone,
  memberDisplayName,
  memberInitial,
  memberProfileSummary,
} from './memberDisplay';

interface FamilyMemberCardProps {
  familyId: number;
  member: FamilyMember;
  currentUserId?: number;
  canManageSpace: boolean;
  canManageRoles: boolean;
  editingRelationshipKey: string | null;
  relationshipDraft: string;
  updatingRelationshipKey: string | null;
  updatingRoleKey: string | null;
  updatingCareKey: string | null;
  careAuthorized: boolean;
  onStartEditRelationship: (familyId: number, member: FamilyMember) => void;
  onRelationshipDraftChange: (value: string) => void;
  onCancelEditRelationship: () => void;
  onUpdateRelationship: (familyId: number, member: FamilyMember) => void;
  onUpdateRole: (familyId: number, member: FamilyMember, role: FamilyMember['role']) => void;
  onToggleCareAuthorization: (familyId: number, caregiver: FamilyMember) => void;
}

function roleBadge(role?: string) {
  if ((role || '').toUpperCase() === 'OWNER') {
    return {
      icon: Crown,
      label: familyRoleLabel(role),
      className: 'border-amber-200 bg-amber-50 text-amber-700',
    };
  }
  return {
    icon: User,
    label: familyRoleLabel(role),
    className: 'border-stone-200 bg-stone-50 text-stone-600',
  };
}

function isLegacyFamilyRole(role?: string) {
  const normalized = (role || '').toUpperCase();
  return normalized !== 'OWNER' && normalized !== 'MEMBER';
}

export default function FamilyMemberCard({
  familyId,
  member,
  currentUserId,
  canManageSpace,
  canManageRoles,
  editingRelationshipKey,
  relationshipDraft,
  updatingRelationshipKey,
  updatingRoleKey,
  updatingCareKey,
  careAuthorized,
  onStartEditRelationship,
  onRelationshipDraftChange,
  onCancelEditRelationship,
  onUpdateRelationship,
  onUpdateRole,
  onToggleCareAuthorization,
}: FamilyMemberCardProps) {
  const badge = roleBadge(member.role);
  const BadgeIcon = badge.icon;
  const updateKey = `${familyId}:${member.userId}`;
  const careKey = `${familyId}:${member.userId}:care`;
  const isEditingRelationship = editingRelationshipKey === updateKey;
  const canEditRelationship = canManageSpace && member.userId !== currentUserId;
  const canNormalizeRole = canManageRoles && member.role !== 'OWNER' && isLegacyFamilyRole(member.role);
  const profileText = member.relationshipLabel?.trim()
    ? `${memberAccountName(member)} · ${memberProfileSummary(member)}`
    : memberProfileSummary(member);

  return (
    <article className="group flex min-h-[220px] flex-col rounded-lg border border-stone-200 bg-white p-4 shadow-[0_1px_3px_rgba(24,39,32,0.06)] transition duration-200 hover:-translate-y-0.5 hover:border-emerald-200 hover:shadow-[0_12px_30px_rgba(24,39,32,0.08)]">
      <div className="flex items-start justify-between gap-3">
        <div className="flex min-w-0 items-start gap-3">
          <div
            className={cn(
              'flex h-12 w-12 shrink-0 items-center justify-center rounded-lg bg-gradient-to-br text-base font-semibold shadow-sm ring-1 ring-white/70',
              memberAvatarTone(member),
            )}
          >
            {member.avatarUrl ? (
              <Image
                src={member.avatarUrl}
                alt={memberDisplayName(member)}
                width={48}
                height={48}
                unoptimized
                className="h-full w-full rounded-lg object-cover"
              />
            ) : (
              memberInitial(member)
            )}
          </div>

          {isEditingRelationship ? (
            <form
              className="min-w-0 flex-1"
              onSubmit={(event) => {
                event.preventDefault();
                onUpdateRelationship(familyId, member);
              }}
            >
              <input
                name="relationshipLabel"
                value={relationshipDraft}
                maxLength={60}
                autoFocus
                onChange={(event) => onRelationshipDraftChange(event.target.value)}
                placeholder="例如：妈妈、叔叔、小楠"
                className="h-9 w-full rounded-md border border-stone-200 bg-white px-3 text-sm text-stone-700 outline-none transition focus:border-emerald-500 focus:ring-2 focus:ring-emerald-100"
              />
              <div className="mt-2 flex items-center gap-2">
                <button
                  type="submit"
                  disabled={updatingRelationshipKey === updateKey || !relationshipDraft.trim()}
                  className="inline-flex h-8 items-center rounded-md bg-emerald-700 px-3 text-xs font-medium text-white transition hover:bg-emerald-800 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  保存
                </button>
                <button
                  type="button"
                  onClick={onCancelEditRelationship}
                  className="inline-flex h-8 w-8 items-center justify-center rounded-md text-stone-400 transition hover:bg-stone-100 hover:text-stone-600"
                  aria-label="取消编辑关系称呼"
                >
                  <X className="h-4 w-4" />
                </button>
              </div>
            </form>
          ) : (
            <div className="min-w-0">
              <div className="flex min-w-0 items-center gap-1.5">
                <Link
                  href={`/dashboard/family/member?familyId=${familyId}&userId=${member.userId}`}
                  className="truncate text-base font-semibold text-stone-950 transition hover:text-emerald-700"
                >
                  {memberDisplayName(member)}
                </Link>
                {canEditRelationship && (
                  <button
                    type="button"
                    onClick={() => onStartEditRelationship(familyId, member)}
                    className="inline-flex h-7 w-7 shrink-0 items-center justify-center rounded-md text-stone-400 transition hover:bg-emerald-50 hover:text-emerald-700"
                    aria-label="编辑关系称呼"
                  >
                    <Pencil className="h-3.5 w-3.5" />
                  </button>
                )}
              </div>
              <p className="mt-1 line-clamp-2 text-xs leading-5 text-stone-500">{profileText}</p>
            </div>
          )}
        </div>

        <span className={cn('inline-flex shrink-0 items-center gap-1 rounded-full border px-2 py-1 text-[11px] font-medium', badge.className)}>
          <BadgeIcon className="h-3 w-3" />
          {badge.label}
        </span>
      </div>

      <div className="mt-auto pt-5">
        <div className="grid grid-cols-2 gap-2">
          <Link
            href={`/dashboard/family/member?familyId=${familyId}&userId=${member.userId}`}
            className="inline-flex h-9 items-center justify-center gap-1.5 rounded-md border border-stone-200 bg-white px-3 text-xs font-medium text-stone-700 transition hover:border-emerald-200 hover:bg-emerald-50 hover:text-emerald-800"
          >
            <BookHeart className="h-3.5 w-3.5" />
            记忆
          </Link>

          {canManageSpace && member.userId !== currentUserId ? (
            <button
              type="button"
              disabled={updatingCareKey === careKey}
              onClick={() => onToggleCareAuthorization(familyId, member)}
              aria-pressed={careAuthorized}
              className={cn(
                'inline-flex h-9 items-center justify-center gap-2 rounded-md border px-2 text-xs font-medium transition disabled:cursor-not-allowed disabled:opacity-50',
                careAuthorized
                  ? 'border-emerald-200 bg-emerald-50 text-emerald-800 hover:bg-emerald-100'
                  : 'border-stone-200 bg-white text-stone-600 hover:border-emerald-200 hover:bg-stone-50',
              )}
            >
              {updatingCareKey === careKey ? (
                <RefreshCw className="h-3.5 w-3.5 animate-spin" />
              ) : (
                <span
                  className={cn(
                    'relative h-4 w-7 rounded-full transition',
                    careAuthorized ? 'bg-emerald-600' : 'bg-stone-300',
                  )}
                >
                  <span
                    className={cn(
                      'absolute top-0.5 h-3 w-3 rounded-full bg-white shadow transition',
                      careAuthorized ? 'left-3.5' : 'left-0.5',
                    )}
                  />
                </span>
              )}
              {careAuthorized ? '照护中' : '照护'}
            </button>
          ) : (
            <span className="inline-flex h-9 items-center justify-center gap-1.5 rounded-md border border-stone-100 bg-stone-50 px-2 text-xs font-medium text-stone-400">
              <Eye className="h-3.5 w-3.5" />
              本人
            </span>
          )}
        </div>

        {canNormalizeRole && (
          <button
            type="button"
            disabled={updatingRoleKey === updateKey}
            onClick={() => onUpdateRole(familyId, member, 'MEMBER')}
            className="mt-2 inline-flex h-8 w-full items-center justify-center rounded-md border border-stone-200 bg-white px-3 text-xs font-medium text-stone-600 transition hover:border-emerald-200 hover:text-emerald-700 disabled:cursor-not-allowed disabled:opacity-50"
          >
            规范为成员
          </button>
        )}
      </div>
    </article>
  );
}
