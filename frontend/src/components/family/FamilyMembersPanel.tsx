'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import Link from 'next/link';
import {
  BookHeart,
  CheckCircle,
  ChevronDown,
  ChevronUp,
  Copy,
  Crown,
  Eye,
  Pencil,
  Plus,
  RefreshCw,
  User,
  UserPlus,
  Users,
  X,
} from 'lucide-react';
import { familyApi } from '@/lib/api';
import { type ViewerRole, familyRoleLabel } from '@/lib/roles';
import { notifyViewerRoleChanged } from '@/hooks/useViewerRole';
import { useAuthStore } from '@/stores/authStore';
import { useFamilyContextStore } from '@/stores/familyContextStore';
import type { CareAuthorization, Family, FamilyMember } from '@/types';

interface FamilyMembersPanelProps {
  viewerRole?: ViewerRole;
  families?: Family[];
  focusedFamilyId?: number | null;
  onFocusedFamilyChange?: (familyId: number | null) => void;
}

function memberAccountName(member: FamilyMember) {
  return member.nickname?.trim() || member.username?.trim() || `用户 ${member.userId}`;
}

function memberDisplayName(member: FamilyMember) {
  return member.relationshipLabel?.trim() || memberAccountName(member);
}

function memberBirthDate(member: FamilyMember) {
  const value = member.birthDate
    || (typeof member.metadata?.birthDate === 'string' ? member.metadata.birthDate : '')
    || (typeof member.metadata?.birthday === 'string' ? member.metadata.birthday : '')
    || (typeof member.metadata?.dateOfBirth === 'string' ? member.metadata.dateOfBirth : '');
  return value ? value.slice(0, 10) : '';
}

function memberAge(member: FamilyMember) {
  const birthDate = memberBirthDate(member);
  if (birthDate) {
    const date = new Date(birthDate);
    if (!Number.isNaN(date.getTime())) {
      const now = new Date();
      let age = now.getFullYear() - date.getFullYear();
      const monthDelta = now.getMonth() - date.getMonth();
      if (monthDelta < 0 || (monthDelta === 0 && now.getDate() < date.getDate())) age -= 1;
      if (age >= 0 && age <= 130) return age;
    }
  }

  const year = Number(member.birthYear || member.metadata?.birthYear || member.metadata?.yearOfBirth);
  if (Number.isFinite(year) && year > 1870 && year <= new Date().getFullYear()) {
    return new Date().getFullYear() - year;
  }

  return null;
}

function memberProfileLine(member: FamilyMember) {
  const birthDate = memberBirthDate(member);
  const age = memberAge(member);
  const birthText = birthDate ? `生日：${birthDate}` : '生日：未设置';
  const ageText = age == null ? '年龄：未设置' : `年龄：${age}`;
  return `${birthText} · ${ageText}`;
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
      return;
    }

    setLoadingFamilies(true);
    try {
      setError('');
      const data = await familyApi.getMyFamilies();
      setFamilies(Array.isArray(data) ? data : []);
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

  const roleBadge = (role?: string) => {
    if ((role || '').toUpperCase() === 'OWNER') {
      return {
        icon: Crown,
        label: familyRoleLabel(role),
        className: 'bg-yellow-50 text-yellow-700',
      };
    }
    return {
      icon: User,
      label: familyRoleLabel(role),
      className: 'bg-gray-100 text-gray-600',
    };
  };

  const isLegacyFamilyRole = (role?: string) => {
    const normalized = (role || '').toUpperCase();
    return normalized !== 'OWNER' && normalized !== 'MEMBER';
  };

  if (!usingExternalFamilies && loadingFamilies) {
    return (
      <div className="flex h-40 items-center justify-center text-gray-400">
        <RefreshCw className="mr-2 h-5 w-5 animate-spin" />
        正在加载家庭列表...
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-4xl">
      <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="text-xl font-semibold text-gray-900">成员与权限</h2>
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
              className="inline-flex items-center justify-center gap-1.5 rounded-lg border border-gray-200 bg-white px-4 py-2 text-sm text-gray-700 hover:bg-gray-50"
            >
              <UserPlus className="h-4 w-4" />
              加入家庭
            </button>
            <button
              type="button"
              onClick={() => {
                setShowCreate(true);
                setShowJoin(false);
                setError('');
              }}
              className="inline-flex items-center justify-center gap-1.5 rounded-lg bg-blue-600 px-4 py-2 text-sm text-white hover:bg-blue-700"
            >
              <Plus className="h-4 w-4" />
              创建家庭
            </button>
          </div>
        )}
      </div>

      {error && <div className="mb-4 rounded-lg bg-red-50 px-4 py-3 text-sm text-red-600">{error}</div>}
      {successMsg && <div className="mb-4 rounded-lg bg-green-50 px-4 py-3 text-sm text-green-600">{successMsg}</div>}

      {canManageSpace && showCreate && (
        <div className="mb-6 rounded-xl border border-gray-200 bg-white p-5">
          <h3 className="mb-3 text-base font-semibold text-gray-900">创建新家庭</h3>
          <input
            name="newFamilyName"
            type="text"
            value={newFamilyName}
            onChange={(event) => setNewFamilyName(event.target.value)}
            placeholder="例如：王家成长小组"
            className="mb-3 w-full rounded-lg border border-gray-200 px-4 py-2.5 text-sm outline-none focus:ring-2 focus:ring-blue-500"
          />
          <textarea
            name="newFamilyDescription"
            rows={2}
            value={newFamilyDesc}
            onChange={(event) => setNewFamilyDesc(event.target.value)}
            placeholder="可选的家庭描述"
            className="mb-3 w-full resize-none rounded-lg border border-gray-200 px-4 py-2.5 text-sm outline-none focus:ring-2 focus:ring-blue-500"
          />
          <div className="flex justify-end gap-2">
            <button
              type="button"
              onClick={() => setShowCreate(false)}
              className="rounded-lg px-4 py-2 text-sm text-gray-600 hover:bg-gray-50"
            >
              取消
            </button>
            <button
              type="button"
              onClick={handleCreate}
              disabled={!newFamilyName.trim()}
              className="rounded-lg bg-blue-600 px-5 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
            >
              创建
            </button>
          </div>
        </div>
      )}

      {canManageSpace && showJoin && (
        <div className="mb-6 rounded-xl border border-gray-200 bg-white p-5">
          <h3 className="mb-3 text-base font-semibold text-gray-900">使用邀请码加入</h3>
          <div className="flex flex-col gap-2 sm:flex-row">
            <input
              name="inviteCode"
              type="text"
              value={inviteCode}
              maxLength={8}
              onChange={(event) => setInviteCode(event.target.value.toUpperCase())}
              placeholder="请输入 8 位邀请码"
              className="min-w-0 flex-1 rounded-lg border border-gray-200 px-4 py-2.5 text-sm uppercase tracking-widest outline-none focus:ring-2 focus:ring-blue-500"
            />
            <button
              type="button"
              onClick={handleJoin}
              disabled={inviteCode.trim().length < 8}
              className="rounded-lg bg-blue-600 px-5 py-2.5 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
            >
              加入
            </button>
          </div>
          <button
            type="button"
            onClick={() => setShowJoin(false)}
            className="mt-2 text-sm text-gray-500 hover:text-gray-700"
          >
            取消
          </button>
        </div>
      )}

      {visibleFamilies.length === 0 ? (
        <div className="rounded-xl border border-gray-200 bg-white p-12 text-center">
          <Users className="mx-auto mb-3 h-12 w-12 text-gray-200" />
          <h3 className="mb-1 text-lg font-medium text-gray-700">还没有家庭</h3>
          {canManageSpace && (
            <button
              type="button"
              onClick={() => setShowCreate(true)}
              className="rounded-lg bg-blue-600 px-5 py-2.5 text-sm font-medium text-white hover:bg-blue-700"
            >
              创建第一个家庭
            </button>
          )}
        </div>
      ) : (
        <div className="space-y-4">
          {visibleFamilies.map((family) => {
            const isExpanded = expandedFamilyId === family.id;
            const memberList = members[family.id] || [];
            const isLoadingFamilyDetails = Boolean(loadingDetails[family.id]);
            const currentMember = memberList.find((member) => member.userId === currentUserId);
            const canManageRoles = canManageSpace && currentMember?.role === 'OWNER';

            return (
              <div
                key={family.id}
                className={`overflow-hidden rounded-xl border bg-white ${
                  currentFamilyId === family.id ? 'border-blue-200 ring-1 ring-blue-100' : 'border-gray-200'
                }`}
              >
                <div className="p-5">
                  <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                    <div className="flex min-w-0 items-center gap-3">
                      <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-purple-100 text-lg font-bold text-purple-700">
                        {family.name.charAt(0)}
                      </div>
                      <div className="min-w-0">
                        <h3 className="truncate text-lg font-semibold text-gray-900">{family.name}</h3>
                        {family.description && <p className="mt-0.5 text-sm text-gray-500">{family.description}</p>}
                      </div>
                    </div>
                    <div className="text-xs text-gray-400 sm:text-right">
                      最多 {family.maxMembers} 位成员
                    </div>
                  </div>

                  <div className="mt-4 flex flex-wrap items-center gap-2 border-t border-gray-100 pt-3">
                    {canManageSpace && family.inviteCode && (
                      <button
                        type="button"
                        onClick={() => { void copyInviteCode(family.inviteCode!); }}
                        className="inline-flex items-center gap-1 rounded-lg bg-gray-50 px-3 py-1.5 text-xs text-gray-600 hover:bg-gray-100"
                      >
                        {copiedCode === family.inviteCode ? (
                          <>
                            <CheckCircle className="h-3.5 w-3.5 text-green-500" />
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
                      className="inline-flex items-center gap-1 rounded-lg px-3 py-1.5 text-xs text-gray-500 hover:bg-gray-50 hover:text-gray-700"
                    >
                      {isExpanded ? <ChevronUp className="h-3.5 w-3.5" /> : <ChevronDown className="h-3.5 w-3.5" />}
                      {isExpanded ? '收起成员' : '查看成员'}
                    </button>

                    {currentFamilyId === family.id && (
                      <span className="rounded-full bg-blue-50 px-2.5 py-1 text-xs font-medium text-blue-700">
                        当前家庭
                      </span>
                    )}

                    <span className="w-full text-xs text-gray-400 sm:ml-auto sm:w-auto">
                      创建于 {new Date(family.createdAt).toLocaleDateString('zh-CN')}
                    </span>
                  </div>
                </div>

                {isExpanded && (
                  <div className="border-t border-gray-100 bg-gray-50 px-5 py-4">
                    {isLoadingFamilyDetails ? (
                      <div className="flex items-center text-sm text-gray-400">
                        <RefreshCw className="mr-2 h-4 w-4 animate-spin" />
                        正在加载成员...
                      </div>
                    ) : memberList.length === 0 ? (
                      <p className="text-sm text-gray-400">这个家庭暂时还没有可展示的成员信息。</p>
                    ) : (
                      <div className="space-y-2">
                        {memberList.map((member) => {
                          const badge = roleBadge(member.role);
                          const BadgeIcon = badge.icon;
                          const updateKey = `${family.id}:${member.userId}`;
                          const careKey = `${family.id}:${member.userId}:care`;
                          const careAuthorized = hasCareAuthorization(family.id, member.userId);
                          const isEditingRelationship = editingRelationshipKey === updateKey;
                          const canEditRelationship = canManageSpace && member.userId !== currentUserId;
                          const canNormalizeRole = canManageRoles && member.role !== 'OWNER' && isLegacyFamilyRole(member.role);

                          return (
                            <div
                              key={member.id}
                              className="flex flex-col gap-3 rounded-lg border border-white bg-white px-3 py-3 sm:flex-row sm:items-center sm:justify-between"
                            >
                              <div className="flex min-w-0 items-start gap-3">
                                <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full border border-gray-200 bg-gray-50 text-sm font-medium text-gray-500">
                                  {memberDisplayName(member).charAt(0).toUpperCase()}
                                </div>

                                {isEditingRelationship ? (
                                  <form
                                    className="flex min-w-0 flex-1 items-center gap-2"
                                    onSubmit={(event) => {
                                      event.preventDefault();
                                      void handleUpdateRelationship(family.id, member);
                                    }}
                                  >
                                    <input
                                      name="relationshipLabel"
                                      value={relationshipDraft}
                                      maxLength={60}
                                      autoFocus
                                      onChange={(event) => setRelationshipDraft(event.target.value)}
                                      placeholder="例如：妈妈、叔叔、小楠"
                                      className="h-9 min-w-0 flex-1 rounded-md border border-gray-200 bg-white px-3 text-sm text-gray-700 outline-none focus:ring-2 focus:ring-blue-500"
                                    />
                                    <button
                                      type="submit"
                                      disabled={updatingRelationshipKey === updateKey || !relationshipDraft.trim()}
                                      className="rounded-md px-2 py-1 text-xs font-medium text-blue-600 hover:bg-blue-50 disabled:opacity-50"
                                    >
                                      保存
                                    </button>
                                    <button
                                      type="button"
                                      onClick={cancelEditRelationship}
                                      className="inline-flex h-8 w-8 items-center justify-center rounded-md text-gray-400 hover:bg-gray-100 hover:text-gray-600"
                                      aria-label="取消编辑关系称呼"
                                    >
                                      <X className="h-4 w-4" />
                                    </button>
                                  </form>
                                ) : (
                                  <div className="min-w-0">
                                    <div className="flex min-w-0 items-center gap-1.5">
                                      <Link
                                        href={`/dashboard/family/member?familyId=${family.id}&userId=${member.userId}`}
                                        className="truncate text-sm font-medium text-gray-900 hover:text-purple-700 hover:underline"
                                      >
                                        {memberDisplayName(member)}
                                      </Link>
                                      {canEditRelationship && (
                                        <button
                                          type="button"
                                          onClick={() => startEditRelationship(family.id, member)}
                                          className="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded-md text-gray-400 hover:bg-gray-100 hover:text-blue-600"
                                          aria-label="编辑关系称呼"
                                        >
                                          <Pencil className="h-3.5 w-3.5" />
                                        </button>
                                      )}
                                    </div>
                                    <p className="mt-1 text-xs text-gray-400">
                                      {member.relationshipLabel?.trim() ? `${memberAccountName(member)} · ` : ''}
                                      {memberProfileLine(member)}
                                    </p>
                                  </div>
                                )}
                              </div>

                              <div className="flex shrink-0 flex-wrap items-center gap-2">
                                <Link
                                  href={`/dashboard/family/member?familyId=${family.id}&userId=${member.userId}`}
                                  className="inline-flex h-8 items-center gap-1.5 rounded-lg bg-purple-600 px-3 text-xs font-semibold text-white hover:bg-purple-700"
                                >
                                  <BookHeart className="h-3.5 w-3.5" />
                                  成员记忆
                                </Link>

                                <span className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-medium ${badge.className}`}>
                                  <BadgeIcon className="h-3 w-3" />
                                  {badge.label}
                                </span>

                                {canManageSpace && member.userId !== currentUserId && (
                                  <button
                                    type="button"
                                    disabled={updatingCareKey === careKey}
                                    onClick={() => { void handleToggleCareAuthorization(family.id, member); }}
                                    className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-medium disabled:opacity-50 ${
                                      careAuthorized
                                        ? 'bg-emerald-50 text-emerald-700 hover:bg-emerald-100'
                                        : 'bg-gray-100 text-gray-500 hover:bg-gray-200'
                                    }`}
                                  >
                                    <Eye className="h-3 w-3" />
                                    {careAuthorized ? '已开启照护' : '开启照护'}
                                  </button>
                                )}

                                {canNormalizeRole && (
                                  <button
                                    type="button"
                                    disabled={updatingRoleKey === updateKey}
                                    onClick={() => { void handleUpdateRole(family.id, member, 'MEMBER'); }}
                                    className="rounded-md border border-gray-200 bg-white px-2 py-1 text-xs text-gray-600 hover:border-blue-200 hover:text-blue-700 disabled:opacity-50"
                                  >
                                  规范为成员
                                  </button>
                                )}
                              </div>
                            </div>
                          );
                        })}
                      </div>
                    )}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}

    </div>
  );
}
