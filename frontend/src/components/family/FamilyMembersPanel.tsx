'use client';

import { useCallback, useEffect, useState } from 'react';
import { CheckCircle, Copy, Loader2, Plus, RefreshCw, UserPlus, Users } from 'lucide-react';
import FamilyRelationshipGraph from './FamilyRelationshipGraph';
import { familyApi } from '@/lib/api';
import type { ViewerRole } from '@/lib/roles';
import { notifyViewerRoleChanged } from '@/hooks/useViewerRole';
import { useAuthStore } from '@/stores/authStore';
import { useFamilyContextStore } from '@/stores/familyContextStore';
import type { Family, FamilyCreationQuota, FamilyMember } from '@/types';

interface FamilyMembersPanelProps {
  viewerRole?: ViewerRole;
  families?: Family[];
  focusedFamilyId?: number | null;
  onFocusedFamilyChange?: (familyId: number | null) => void;
}

export default function FamilyMembersPanel({
  viewerRole = 'MEMBER',
  families: externalFamilies,
  focusedFamilyId = null,
  onFocusedFamilyChange,
}: FamilyMembersPanelProps) {
  const currentUserId = useAuthStore((state) => state.user?.id);
  const setActiveFamilyId = useFamilyContextStore((state) => state.setActiveFamilyId);
  const [families, setFamilies] = useState<Family[]>([]);
  const [creationQuota, setCreationQuota] = useState<FamilyCreationQuota | null>(null);
  const [members, setMembers] = useState<Record<number, FamilyMember[]>>({});
  const [loadingFamilies, setLoadingFamilies] = useState(true);
  const [loadingMembers, setLoadingMembers] = useState<Record<number, boolean>>({});
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [showCreate, setShowCreate] = useState(false);
  const [showJoin, setShowJoin] = useState(false);
  const [newFamilyName, setNewFamilyName] = useState('');
  const [newFamilyDesc, setNewFamilyDesc] = useState('');
  const [inviteCode, setInviteCode] = useState('');
  const [copiedCode, setCopiedCode] = useState<string | null>(null);

  const usingExternalFamilies = Array.isArray(externalFamilies);
  const availableFamilies = usingExternalFamilies ? externalFamilies : families;
  const visibleFamilies = focusedFamilyId
    ? availableFamilies.filter((family) => family.id === focusedFamilyId)
    : availableFamilies;
  const canManageSpace = viewerRole === 'MEMBER' || viewerRole === 'ADMIN';
  const quota = creationQuota ?? {
    maxFamilies: 3,
    createdFamilies: availableFamilies.length,
    remainingFamilies: Math.max(0, 3 - availableFamilies.length),
  };

  const showMessage = useCallback((message: string) => {
    setSuccess(message);
    window.setTimeout(() => setSuccess(''), 3000);
  }, []);

  const loadFamilies = useCallback(async () => {
    if (usingExternalFamilies) {
      setLoadingFamilies(false);
      try {
        setCreationQuota(await familyApi.getCreationQuota());
      } catch {
        setCreationQuota(null);
      }
      return;
    }
    setLoadingFamilies(true);
    try {
      const [nextFamilies, nextQuota] = await Promise.all([
        familyApi.getMyFamilies(),
        familyApi.getCreationQuota(),
      ]);
      setFamilies(Array.isArray(nextFamilies) ? nextFamilies : []);
      setCreationQuota(nextQuota);
      setError('');
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载家庭列表失败');
    } finally {
      setLoadingFamilies(false);
    }
  }, [usingExternalFamilies]);

  const loadMembers = useCallback(async (familyId: number) => {
    setLoadingMembers((current) => ({ ...current, [familyId]: true }));
    try {
      const nextMembers = await familyApi.getMembers(familyId);
      setMembers((current) => ({ ...current, [familyId]: nextMembers }));
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载成员关系失败');
    } finally {
      setLoadingMembers((current) => ({ ...current, [familyId]: false }));
    }
  }, []);

  useEffect(() => {
    void loadFamilies();
  }, [loadFamilies]);
  useEffect(() => {
    if (focusedFamilyId && !members[focusedFamilyId] && !loadingMembers[focusedFamilyId])
      void loadMembers(focusedFamilyId);
  }, [focusedFamilyId, loadMembers, loadingMembers, members]);

  const handleCreate = async () => {
    if (!newFamilyName.trim() || quota.remainingFamilies <= 0) return;
    try {
      const created = await familyApi.create({
        name: newFamilyName.trim(),
        description: newFamilyDesc.trim() || undefined,
      });
      setActiveFamilyId(created.id);
      onFocusedFamilyChange?.(created.id);
      setShowCreate(false);
      setNewFamilyName('');
      setNewFamilyDesc('');
      showMessage('家庭已创建');
      await loadFamilies();
      notifyViewerRoleChanged();
    } catch (err) {
      setError(err instanceof Error ? err.message : '创建家庭失败');
    }
  };

  const handleJoin = async () => {
    if (inviteCode.trim().length < 8) return;
    try {
      const joined = await familyApi.join(inviteCode.trim());
      setActiveFamilyId(joined.familyId);
      onFocusedFamilyChange?.(joined.familyId);
      setShowJoin(false);
      setInviteCode('');
      showMessage('已加入家庭');
      await loadFamilies();
      notifyViewerRoleChanged();
    } catch (err) {
      setError(err instanceof Error ? err.message : '加入家庭失败');
    }
  };

  const copyInviteCode = async (code: string) => {
    try {
      await navigator.clipboard.writeText(code);
      setCopiedCode(code);
      window.setTimeout(() => setCopiedCode(null), 2000);
    } catch {
      setError('复制邀请码失败');
    }
  };

  if (!usingExternalFamilies && loadingFamilies) {
    return (
      <div className="flex h-40 items-center justify-center text-stone-400">
        <RefreshCw className="mr-2 h-5 w-5 animate-spin" />
        正在加载家庭空间...
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-6xl">
      <header className="mb-5 flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
        <h2 className="text-2xl font-semibold tracking-tight text-stone-950">家庭关系图</h2>
        {canManageSpace && (
          <div className="grid grid-cols-2 gap-2 sm:flex">
            <button
              type="button"
              data-testid="family-join-open"
              onClick={() => {
                setShowJoin(true);
                setShowCreate(false);
              }}
              className="inline-flex h-10 items-center justify-center gap-1.5 rounded-full border border-stone-200 bg-white/70 px-4 text-sm font-medium text-stone-700 transition hover:border-sky-200 hover:bg-sky-50 hover:text-sky-800"
            >
              <UserPlus className="h-4 w-4" />
              加入家庭
            </button>
            <button
              type="button"
              data-testid="family-create-open"
              onClick={() => {
                if (quota.remainingFamilies > 0) {
                  setShowCreate(true);
                  setShowJoin(false);
                }
              }}
              disabled={quota.remainingFamilies <= 0}
              className="inline-flex h-10 items-center justify-center gap-1.5 rounded-full bg-gradient-to-r from-sky-700 to-blue-600 px-4 text-sm font-semibold text-white shadow-[0_10px_22px_rgba(14,165,233,0.18)] transition hover:from-sky-600 hover:to-blue-500 disabled:cursor-not-allowed disabled:from-stone-200 disabled:to-stone-200 disabled:text-stone-500"
            >
              <Plus className="h-4 w-4" />
              创建家庭
            </button>
          </div>
        )}
      </header>

      {error && (
        <div className="mb-4 rounded-2xl border border-red-100/80 bg-red-50/75 px-4 py-3 text-sm text-red-700">
          {error}
        </div>
      )}
      {success && (
        <div className="mb-4 rounded-2xl border border-sky-100/80 bg-sky-50/75 px-4 py-3 text-sm text-sky-700">
          {success}
        </div>
      )}

      {canManageSpace && showCreate && (
        <div className="glass-panel mb-5 rounded-[24px] p-5">
          <h3 className="text-base font-semibold text-stone-950">创建新家庭</h3>
          <p className="mt-1 text-sm text-stone-500">
            还可创建 {quota.remainingFamilies} 个家庭空间
          </p>
          <input
            value={newFamilyName}
            onChange={(event) => setNewFamilyName(event.target.value)}
            placeholder="家庭名称"
            className="glass-control mt-3 w-full rounded-2xl px-4 py-2.5 text-sm"
          />
          <textarea
            value={newFamilyDesc}
            onChange={(event) => setNewFamilyDesc(event.target.value)}
            placeholder="可选的家庭描述"
            rows={2}
            className="glass-control mt-3 w-full resize-none rounded-2xl px-4 py-2.5 text-sm"
          />
          <div className="mt-3 flex justify-end gap-2">
            <button
              type="button"
              onClick={() => setShowCreate(false)}
              className="rounded-full px-4 py-2 text-sm text-stone-600 hover:bg-white/70"
            >
              取消
            </button>
            <button
              type="button"
              onClick={() => void handleCreate()}
              disabled={!newFamilyName.trim()}
              className="rounded-full bg-sky-700 px-5 py-2 text-sm font-medium text-white disabled:opacity-50"
            >
              创建
            </button>
          </div>
        </div>
      )}
      {canManageSpace && showJoin && (
        <div className="glass-panel mb-5 rounded-[24px] p-5">
          <h3 className="text-base font-semibold text-stone-950">使用邀请码加入</h3>
          <div className="mt-3 flex flex-col gap-2 sm:flex-row">
            <input
              value={inviteCode}
              maxLength={8}
              onChange={(event) => setInviteCode(event.target.value.toUpperCase())}
              placeholder="请输入 8 位邀请码"
              className="glass-control min-w-0 flex-1 rounded-2xl px-4 py-2.5 text-sm uppercase tracking-widest"
            />
            <button
              type="button"
              onClick={() => void handleJoin()}
              disabled={inviteCode.trim().length < 8}
              className="rounded-full bg-sky-700 px-5 py-2.5 text-sm font-medium text-white disabled:opacity-50"
            >
              加入
            </button>
          </div>
          <button
            type="button"
            onClick={() => setShowJoin(false)}
            className="mt-2 text-sm text-stone-500 hover:text-stone-700"
          >
            取消
          </button>
        </div>
      )}

      {visibleFamilies.length === 0 ? (
        <div className="glass-panel rounded-[24px] border-dashed border-stone-300/80 p-12 text-center">
          <Users className="mx-auto mb-3 h-12 w-12 text-stone-200" />
          <h3 className="text-lg font-medium text-stone-700">还没有家庭</h3>
        </div>
      ) : (
        <div className="space-y-5">
          {visibleFamilies.map((family) => {
            const familyMembers = members[family.id] || [];
            const currentMember = familyMembers.find((member) => member.userId === currentUserId);
            return (
              <section key={family.id} className="glass-panel overflow-hidden rounded-[28px]">
                <div className="relative border-b border-white/70 bg-white/35 px-5 py-6 text-center">
                  <h3 className="text-2xl font-semibold tracking-tight text-stone-950">
                    {family.name}
                  </h3>
                  {family.description && (
                    <p className="mx-auto mt-1 max-w-2xl text-sm text-stone-500">
                      {family.description}
                    </p>
                  )}
                  {canManageSpace && family.inviteCode && (
                    <button
                      type="button"
                      onClick={() => {
                        void copyInviteCode(family.inviteCode!);
                      }}
                      className="absolute right-4 top-1/2 inline-flex -translate-y-1/2 items-center gap-1.5 rounded-full border border-stone-200 bg-white/70 px-3 py-2 text-xs font-medium text-stone-600 hover:border-sky-200 hover:bg-sky-50 hover:text-sky-800"
                    >
                      {copiedCode === family.inviteCode ? (
                        <>
                          <CheckCircle className="h-3.5 w-3.5 text-sky-600" />
                          已复制
                        </>
                      ) : (
                        <>
                          <Copy className="h-3.5 w-3.5" />
                          点击邀请
                        </>
                      )}
                    </button>
                  )}
                </div>
                <div className="p-4 sm:p-6">
                  {loadingMembers[family.id] ? (
                    <div className="flex h-48 items-center justify-center text-sm text-stone-400">
                      <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                      正在整理关系图...
                    </div>
                  ) : familyMembers.length === 0 ? (
                    <div className="rounded-2xl border border-dashed border-stone-200 px-4 py-12 text-center text-sm text-stone-400">
                      这个家庭暂时还没有可展示的成员。
                    </div>
                  ) : (
                    <FamilyRelationshipGraph
                      familyId={family.id}
                      members={familyMembers}
                      currentUserId={currentUserId}
                      isOwner={currentMember?.role === 'OWNER'}
                      canEditRelationships={canManageSpace}
                    />
                  )}
                </div>
              </section>
            );
          })}
        </div>
      )}
    </div>
  );
}
