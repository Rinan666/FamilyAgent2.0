'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  CheckCircle,
  ChevronDown,
  ChevronUp,
  Copy,
  Plus,
  RefreshCw,
  UserPlus,
  Users,
} from 'lucide-react';
import FamilyMemberCard from './FamilyMemberCard';
import FamilyRelationshipGraph from './FamilyRelationshipGraph';
import { familyApi } from '@/lib/api';
import { type ViewerRole } from '@/lib/roles';
import { cn } from '@/lib/utils';
import { notifyViewerRoleChanged } from '@/hooks/useViewerRole';
import { useAuthStore } from '@/stores/authStore';
import { useFamilyContextStore } from '@/stores/familyContextStore';
import type { CareAuthorization, Family, FamilyCreationQuota, FamilyMember } from '@/types';

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
  const activeFamilyId = useFamilyContextStore((state) => state.activeFamilyId);
  const setActiveFamilyId = useFamilyContextStore((state) => state.setActiveFamilyId);

  const [families, setFamilies] = useState<Family[]>([]);
  const [creationQuota, setCreationQuota] = useState<FamilyCreationQuota | null>(null);
  const [loadingFamilies, setLoadingFamilies] = useState(true);
  const [error, setError] = useState('');
  const [successMsg, setSuccessMsg] = useState('');

  const [showCreate, setShowCreate] = useState(false);
  const [showJoin, setShowJoin] = useState(false);
  const [newFamilyName, setNewFamilyName] = useState('');
  const [newFamilyDesc, setNewFamilyDesc] = useState('');
  const [inviteCode, setInviteCode] = useState('');

  const [expandedFamilyId, setExpandedFamilyId] = useState<number | null>(null);
  const [members, setMembers] = useState<Record<number, FamilyMember[]>>({});
  const [careAuthorizations, setCareAuthorizations] = useState<Record<number, CareAuthorization[]>>({});
  const [loadingDetails, setLoadingDetails] = useState<Record<number, boolean>>({});
  const [copiedCode, setCopiedCode] = useState<string | null>(null);

  const [editingRelationshipKey, setEditingRelationshipKey] = useState<string | null>(null);
  const [relationshipDraft, setRelationshipDraft] = useState('');
  const [updatingRelationshipKey, setUpdatingRelationshipKey] = useState<string | null>(null);
  const [updatingRoleKey, setUpdatingRoleKey] = useState<string | null>(null);
  const [updatingCareKey, setUpdatingCareKey] = useState<string | null>(null);
  const autoExpandedFocusedFamilyIdRef = useRef<number | null>(null);

  const usingExternalFamilies = Array.isArray(externalFamilies);
  const availableFamilies = usingExternalFamilies ? externalFamilies : families;
  const derivedCreationQuota = creationQuota ?? {
    maxFamilies: 3,
    createdFamilies: availableFamilies.filter((family) => family.createdBy === currentUserId).length,
    remainingFamilies: Math.max(0, 3 - availableFamilies.filter((family) => family.createdBy === currentUserId).length),
  };
  const visibleFamilies = focusedFamilyId
    ? availableFamilies.filter((family) => family.id === focusedFamilyId)
    : availableFamilies;
  const currentFamilyId = focusedFamilyId ?? activeFamilyId;
  const canManageSpace = viewerRole === 'MEMBER' || viewerRole === 'ADMIN';

  const showMsg = useCallback((message: string) => {
    setSuccessMsg(message);
    window.setTimeout(() => setSuccessMsg(''), 3000);
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
      setError('');
      const [data, quota] = await Promise.all([
        familyApi.getMyFamilies(),
        familyApi.getCreationQuota(),
      ]);
      setFamilies(Array.isArray(data) ? data : []);
      setCreationQuota(quota);
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载家庭列表失败');
    } finally {
      setLoadingFamilies(false);
    }
  }, [usingExternalFamilies]);

  const loadFamilyDetails = useCallback(async (familyId: number) => {
    setLoadingDetails((current) => ({ ...current, [familyId]: true }));
    try {
      const [memberList, authList] = await Promise.all([
        familyApi.getMembers(familyId),
        familyApi.getMyCareAuthorizations(familyId),
      ]);
      setMembers((current) => ({ ...current, [familyId]: Array.isArray(memberList) ? memberList : [] }));
      setCareAuthorizations((current) => ({ ...current, [familyId]: Array.isArray(authList) ? authList : [] }));
    } finally {
      setLoadingDetails((current) => ({ ...current, [familyId]: false }));
    }
  }, []);

  useEffect(() => {
    void loadFamilies();
  }, [loadFamilies]);

  useEffect(() => {
    if (!focusedFamilyId) return;
    if (autoExpandedFocusedFamilyIdRef.current !== focusedFamilyId) {
      setExpandedFamilyId(focusedFamilyId);
      autoExpandedFocusedFamilyIdRef.current = focusedFamilyId;
    }
    if (!members[focusedFamilyId] && !loadingDetails[focusedFamilyId]) {
      void loadFamilyDetails(focusedFamilyId).catch(() => {});
    }
  }, [focusedFamilyId, loadFamilyDetails, loadingDetails, members]);

  const currentVisibleFamily = useMemo(
    () => visibleFamilies.find((family) => family.id === currentFamilyId) || visibleFamilies[0] || null,
    [currentFamilyId, visibleFamilies],
  );

  const currentVisibleMembers = currentVisibleFamily ? members[currentVisibleFamily.id] || [] : [];

  const handleCreate = async () => {
    if (!newFamilyName.trim()) return;

    setError('');
    try {
      const created = await familyApi.create({
        name: newFamilyName.trim(),
        description: newFamilyDesc.trim() || undefined,
      });
      setActiveFamilyId(created.id);
      onFocusedFamilyChange?.(created.id);
      setExpandedFamilyId(created.id);
      setShowCreate(false);
      setNewFamilyName('');
      setNewFamilyDesc('');
      showMsg('已创建家庭');
      await loadFamilies();
      notifyViewerRoleChanged();
    } catch (err) {
      setError(err instanceof Error ? err.message : '创建家庭失败');
    }
  };

  const handleJoin = async () => {
    if (!inviteCode.trim()) return;

    setError('');
    try {
      const joined = await familyApi.join(inviteCode.trim());
      setActiveFamilyId(joined.familyId);
      onFocusedFamilyChange?.(joined.familyId);
      setExpandedFamilyId(joined.familyId);
      setShowJoin(false);
      setInviteCode('');
      showMsg('已加入家庭');
      await loadFamilies();
      notifyViewerRoleChanged();
    } catch (err) {
      setError(err instanceof Error ? err.message : '加入家庭失败');
    }
  };

  const refreshFamilyMembers = async (familyId: number) => {
    try {
      await loadFamilyDetails(familyId);
    } catch (err) {
      setError(err instanceof Error ? err.message : '刷新成员信息失败');
    }
  };

  const handleExpand = async (familyId: number) => {
    onFocusedFamilyChange?.(familyId);
    setActiveFamilyId(familyId);

    if (expandedFamilyId === familyId) {
      setExpandedFamilyId(null);
      return;
    }

    setExpandedFamilyId(familyId);
    if (!members[familyId] && !loadingDetails[familyId]) {
      try {
        await loadFamilyDetails(familyId);
      } catch (err) {
        setError(err instanceof Error ? err.message : '加载成员信息失败');
      }
    }
  };

  const startEditRelationship = (familyId: number, member: FamilyMember) => {
    setEditingRelationshipKey(`${familyId}:${member.userId}`);
    setRelationshipDraft(member.relationshipLabel?.trim() || '');
  };

  const cancelEditRelationship = () => {
    setEditingRelationshipKey(null);
    setRelationshipDraft('');
  };

  const handleUpdateRelationship = async (familyId: number, member: FamilyMember) => {
    const label = relationshipDraft.trim();
    if (!label) {
      setError('关系称呼不能为空');
      return;
    }

    const key = `${familyId}:${member.userId}`;
    setUpdatingRelationshipKey(key);
    setError('');
    try {
      const updated = await familyApi.upsertRelationshipLabel(familyId, member.userId, { label });
      setMembers((current) => ({
        ...current,
        [familyId]: (current[familyId] || []).map((item) => (
          item.userId === member.userId
            ? { ...item, relationshipLabel: updated.label, reverseRelationshipLabel: updated.reverseLabel }
            : item
        )),
      }));
      cancelEditRelationship();
      showMsg('关系称呼已更新');
    } catch (err) {
      setError(err instanceof Error ? err.message : '更新关系称呼失败');
    } finally {
      setUpdatingRelationshipKey(null);
    }
  };

  const handleUpdateRole = async (familyId: number, member: FamilyMember, role: FamilyMember['role']) => {
    if (member.role === role) return;

    const key = `${familyId}:${member.userId}`;
    setUpdatingRoleKey(key);
    setError('');
    try {
      const updated = await familyApi.updateMemberRole(familyId, member.userId, role);
      setMembers((current) => ({
        ...current,
        [familyId]: (current[familyId] || []).map((item) => (item.userId === member.userId ? updated : item)),
      }));
      showMsg('成员角色已更新');
      notifyViewerRoleChanged();
    } catch (err) {
      setError(err instanceof Error ? err.message : '更新成员角色失败');
      await refreshFamilyMembers(familyId);
    } finally {
      setUpdatingRoleKey(null);
    }
  };

  const hasActiveCareScope = (
    familyId: number,
    caregiverUserId: number,
    scope: 'DIARY' | 'GROWTH_GUARD',
  ) => {
    if (!currentUserId) return false;
    return (careAuthorizations[familyId] || []).some((item) => (
      item.subjectUserId === currentUserId
      && item.caregiverUserId === caregiverUserId
      && item.status === 'ACTIVE'
      && (item.scope === 'ALL' || item.scope === scope)
    ));
  };

  const hasCareAuthorization = (familyId: number, caregiverUserId: number) => (
    hasActiveCareScope(familyId, caregiverUserId, 'DIARY')
    && hasActiveCareScope(familyId, caregiverUserId, 'GROWTH_GUARD')
  );

  const handleToggleCareAuthorization = async (familyId: number, caregiver: FamilyMember) => {
    if (!currentUserId || caregiver.userId === currentUserId) return;

    const key = `${familyId}:${caregiver.userId}:care`;
    const active = hasCareAuthorization(familyId, caregiver.userId);
    setUpdatingCareKey(key);
    setError('');

    try {
      const updates = await Promise.all([
        familyApi.upsertCareAuthorization(familyId, currentUserId, caregiver.userId, {
          scope: 'DIARY',
          active: !active,
        }),
        familyApi.upsertCareAuthorization(familyId, currentUserId, caregiver.userId, {
          scope: 'GROWTH_GUARD',
          active: !active,
        }),
      ]);

      setCareAuthorizations((current) => {
        const existing = current[familyId] || [];
        const filtered = existing.filter((item) => !(
          item.subjectUserId === currentUserId
          && item.caregiverUserId === caregiver.userId
          && updates.some((updated) => updated.scope === item.scope)
        ));
        return { ...current, [familyId]: [...filtered, ...updates] };
      });

      showMsg(active ? '已撤销照护授权' : '已开启照护授权');
    } catch (err) {
      setError(err instanceof Error ? err.message : '更新照护授权失败');
    } finally {
      setUpdatingCareKey(null);
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
        正在加载家庭列表...
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-6xl">
      <div className="mb-5 flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <h2 className="text-2xl font-semibold tracking-tight text-stone-950">成员与权限</h2>
          <p className="mt-1 text-sm text-stone-500">
            {visibleFamilies.length} 个家庭 · {currentVisibleMembers.length} 位成员
            {canManageSpace && ` · 还可创建 ${derivedCreationQuota.remainingFamilies} 个`}
          </p>
        </div>

        {canManageSpace && (
          <div className="grid grid-cols-2 gap-2 sm:flex">
            <button
              type="button"
              onClick={() => {
                setShowJoin(true);
                setShowCreate(false);
                setError('');
              }}
              className="inline-flex h-10 items-center justify-center gap-1.5 rounded-md border border-stone-200 bg-white px-4 text-sm font-medium text-stone-700 transition hover:border-emerald-200 hover:bg-emerald-50 hover:text-emerald-800"
            >
              <UserPlus className="h-4 w-4" />
              加入家庭
            </button>
            <button
              type="button"
              onClick={() => {
                if (derivedCreationQuota.remainingFamilies <= 0) return;
                setShowCreate(true);
                setShowJoin(false);
                setError('');
              }}
              disabled={derivedCreationQuota.remainingFamilies <= 0}
              className="inline-flex h-10 items-center justify-center gap-1.5 rounded-md bg-gradient-to-r from-emerald-800 to-teal-700 px-4 text-sm font-semibold text-white shadow-[0_10px_22px_rgba(13,148,136,0.18)] transition hover:from-emerald-700 hover:to-teal-600 disabled:cursor-not-allowed disabled:from-stone-200 disabled:to-stone-200 disabled:text-stone-500 disabled:shadow-none"
            >
              <Plus className="h-4 w-4" />
              创建家庭
            </button>
          </div>
        )}
      </div>

      {error && <div className="mb-4 rounded-md border border-red-100 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}
      {successMsg && <div className="mb-4 rounded-md border border-emerald-100 bg-emerald-50 px-4 py-3 text-sm text-emerald-700">{successMsg}</div>}

      {canManageSpace && showCreate && (
        <div className="mb-5 rounded-lg border border-stone-200 bg-white p-5 shadow-[0_1px_3px_rgba(24,39,32,0.05)]">
          <h3 className="mb-1 text-base font-semibold text-stone-950">创建新家庭</h3>
          <p className="mb-3 text-sm text-stone-500">
            还可创建 {derivedCreationQuota.remainingFamilies} 个家族空间
          </p>
          <input
            name="newFamilyName"
            type="text"
            value={newFamilyName}
            onChange={(event) => setNewFamilyName(event.target.value)}
            placeholder="例如：王家成长小组"
            className="mb-3 w-full rounded-md border border-stone-200 bg-stone-50 px-4 py-2.5 text-sm text-stone-900 outline-none transition placeholder:text-stone-400 focus:border-emerald-500 focus:bg-white focus:ring-2 focus:ring-emerald-100"
          />
          <textarea
            name="newFamilyDescription"
            rows={2}
            value={newFamilyDesc}
            onChange={(event) => setNewFamilyDesc(event.target.value)}
            placeholder="可选的家庭描述"
            className="mb-3 w-full resize-none rounded-md border border-stone-200 bg-stone-50 px-4 py-2.5 text-sm text-stone-900 outline-none transition placeholder:text-stone-400 focus:border-emerald-500 focus:bg-white focus:ring-2 focus:ring-emerald-100"
          />
          <div className="flex justify-end gap-2">
            <button
              type="button"
              onClick={() => setShowCreate(false)}
              className="rounded-md px-4 py-2 text-sm text-stone-600 transition hover:bg-stone-50"
            >
              取消
            </button>
            <button
              type="button"
              onClick={handleCreate}
              disabled={!newFamilyName.trim() || derivedCreationQuota.remainingFamilies <= 0}
              className="rounded-md bg-emerald-700 px-5 py-2 text-sm font-medium text-white transition hover:bg-emerald-800 disabled:cursor-not-allowed disabled:opacity-50"
            >
              创建
            </button>
          </div>
        </div>
      )}

      {canManageSpace && showJoin && (
        <div className="mb-5 rounded-lg border border-stone-200 bg-white p-5 shadow-[0_1px_3px_rgba(24,39,32,0.05)]">
          <h3 className="mb-3 text-base font-semibold text-stone-950">使用邀请码加入</h3>
          <div className="flex flex-col gap-2 sm:flex-row">
            <input
              name="inviteCode"
              type="text"
              value={inviteCode}
              maxLength={8}
              onChange={(event) => setInviteCode(event.target.value.toUpperCase())}
              placeholder="请输入 8 位邀请码"
              className="min-w-0 flex-1 rounded-md border border-stone-200 bg-stone-50 px-4 py-2.5 text-sm uppercase tracking-widest text-stone-900 outline-none transition placeholder:text-stone-400 focus:border-emerald-500 focus:bg-white focus:ring-2 focus:ring-emerald-100"
            />
            <button
              type="button"
              onClick={handleJoin}
              disabled={inviteCode.trim().length < 8}
              className="rounded-md bg-emerald-700 px-5 py-2.5 text-sm font-medium text-white transition hover:bg-emerald-800 disabled:cursor-not-allowed disabled:opacity-50"
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
        <div className="rounded-lg border border-dashed border-stone-300 bg-white p-12 text-center">
          <Users className="mx-auto mb-3 h-12 w-12 text-stone-200" />
          <h3 className="mb-1 text-lg font-medium text-stone-700">还没有家庭</h3>
          {canManageSpace && (
            <button
              type="button"
              onClick={() => {
                if (derivedCreationQuota.remainingFamilies <= 0) return;
                setShowCreate(true);
              }}
              disabled={derivedCreationQuota.remainingFamilies <= 0}
              className="mt-4 rounded-md bg-emerald-700 px-5 py-2.5 text-sm font-medium text-white transition hover:bg-emerald-800 disabled:cursor-not-allowed disabled:bg-stone-200 disabled:text-stone-500"
            >
              创建第一个家庭
            </button>
          )}
        </div>
      ) : (
        <div className="space-y-5">
          {visibleFamilies.map((family) => {
            const isExpanded = expandedFamilyId === family.id;
            const memberList = members[family.id] || [];
            const isLoadingFamilyDetails = Boolean(loadingDetails[family.id]);
            const currentMember = memberList.find((member) => member.userId === currentUserId);
            const canManageRoles = canManageSpace && currentMember?.role === 'OWNER';

            return (
              <section
                key={family.id}
                className={cn(
                  'overflow-hidden rounded-lg border bg-white shadow-[0_1px_3px_rgba(24,39,32,0.06)]',
                  currentFamilyId === family.id ? 'border-emerald-200 ring-1 ring-emerald-100' : 'border-stone-200',
                )}
              >
                <div className="bg-[#fafaf8] p-5">
                  <div className="flex flex-col gap-5 lg:flex-row lg:items-start lg:justify-between">
                    <div className="flex min-w-0 items-start gap-4">
                      <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-lg bg-emerald-900 text-lg font-semibold text-white shadow-sm">
                        {family.name.charAt(0)}
                      </div>
                      <div className="min-w-0">
                        <div className="flex flex-wrap items-center gap-2">
                          <h3 className="truncate text-xl font-semibold text-stone-950">{family.name}</h3>
                          {currentFamilyId === family.id && (
                            <span className="rounded-full bg-emerald-50 px-2.5 py-1 text-xs font-medium text-emerald-700 ring-1 ring-emerald-100">
                              当前家庭
                            </span>
                          )}
                        </div>
                        {family.description && (
                          <p className="mt-1 max-w-2xl text-sm leading-6 text-stone-500">{family.description}</p>
                        )}
                        <div className="mt-3 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-stone-500">
                          <span>{memberList.length || currentVisibleMembers.length} / {family.maxMembers} 成员</span>
                          <span>创建于 {new Date(family.createdAt).toLocaleDateString('zh-CN')}</span>
                        </div>
                      </div>
                    </div>

                    <div className="flex flex-wrap items-center gap-2">
                      {canManageSpace && family.inviteCode && (
                        <button
                          type="button"
                          onClick={() => { void copyInviteCode(family.inviteCode!); }}
                          className="inline-flex h-9 items-center gap-1.5 rounded-md border border-stone-200 bg-white px-3 text-xs font-medium text-stone-600 transition hover:border-emerald-200 hover:bg-emerald-50 hover:text-emerald-800"
                        >
                          {copiedCode === family.inviteCode ? (
                            <>
                              <CheckCircle className="h-3.5 w-3.5 text-emerald-600" />
                              已复制
                            </>
                          ) : (
                            <>
                              <Copy className="h-3.5 w-3.5" />
                              {family.inviteCode}
                            </>
                          )}
                        </button>
                      )}

                      <button
                        type="button"
                        onClick={() => { void handleExpand(family.id); }}
                        className="inline-flex h-9 items-center gap-1.5 rounded-md border border-stone-200 bg-white px-3 text-xs font-medium text-stone-600 transition hover:border-emerald-200 hover:bg-emerald-50 hover:text-emerald-800"
                      >
                        {isExpanded ? <ChevronUp className="h-3.5 w-3.5" /> : <ChevronDown className="h-3.5 w-3.5" />}
                        {isExpanded ? '收起成员' : '查看成员'}
                      </button>
                    </div>
                  </div>
                </div>

                {isExpanded && (
                  <div className="border-t border-stone-100 bg-white px-5 py-5">
                    {isLoadingFamilyDetails ? (
                      <div className="flex h-32 items-center justify-center text-sm text-stone-400">
                        <RefreshCw className="mr-2 h-4 w-4 animate-spin" />
                        正在加载成员...
                      </div>
                    ) : memberList.length === 0 ? (
                      <p className="rounded-md border border-dashed border-stone-200 px-4 py-8 text-center text-sm text-stone-400">
                        这个家庭暂时还没有可展示的成员信息。
                      </p>
                    ) : (
                      <>
                        <FamilyRelationshipGraph members={memberList} currentUserId={currentUserId} />
                        <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
                          {memberList.map((member) => (
                            <FamilyMemberCard
                              key={member.id}
                              familyId={family.id}
                              member={member}
                              currentUserId={currentUserId}
                              canManageSpace={canManageSpace}
                              canManageRoles={canManageRoles}
                              editingRelationshipKey={editingRelationshipKey}
                              relationshipDraft={relationshipDraft}
                              updatingRelationshipKey={updatingRelationshipKey}
                              updatingRoleKey={updatingRoleKey}
                              updatingCareKey={updatingCareKey}
                              careAuthorized={hasCareAuthorization(family.id, member.userId)}
                              onStartEditRelationship={startEditRelationship}
                              onRelationshipDraftChange={setRelationshipDraft}
                              onCancelEditRelationship={cancelEditRelationship}
                              onUpdateRelationship={(targetFamilyId, targetMember) => {
                                void handleUpdateRelationship(targetFamilyId, targetMember);
                              }}
                              onUpdateRole={(targetFamilyId, targetMember, role) => {
                                void handleUpdateRole(targetFamilyId, targetMember, role);
                              }}
                              onToggleCareAuthorization={(targetFamilyId, caregiver) => {
                                void handleToggleCareAuthorization(targetFamilyId, caregiver);
                              }}
                            />
                          ))}
                        </div>
                      </>
                    )}
                  </div>
                )}
              </section>
            );
          })}
        </div>
      )}
    </div>
  );
}
