'use client';

import { useState, useEffect, useCallback } from 'react';
import Link from 'next/link';
import { familyApi } from '@/lib/api';
import { familyRoleLabel } from '@/lib/roles';
import { notifyViewerRoleChanged } from '@/hooks/useViewerRole';
import { useAuthStore } from '@/stores/authStore';
import { useFamilyContextStore } from '@/stores/familyContextStore';
import type { CareAuthorization, Family, FamilyMember } from '@/types';
import {
  Users, Plus, Copy, CheckCircle, UserPlus, Crown,
  ChevronDown, ChevronUp, User, RefreshCw,
  Pencil, X, Eye, BookHeart,
} from 'lucide-react';

export default function FamilyPage() {
  const currentUserId = useAuthStore((s) => s.user?.id);
  const activeFamilyId = useFamilyContextStore((s) => s.activeFamilyId);
  const setActiveFamilyId = useFamilyContextStore((s) => s.setActiveFamilyId);
  const [families, setFamilies] = useState<Family[]>([]);
  const [loading, setLoading] = useState(true);
  const [showCreate, setShowCreate] = useState(false);
  const [showJoin, setShowJoin] = useState(false);
  const [newFamilyName, setNewFamilyName] = useState('');
  const [newFamilyDesc, setNewFamilyDesc] = useState('');
  const [inviteCode, setInviteCode] = useState('');
  const [copiedCode, setCopiedCode] = useState<string | null>(null);
  const [error, setError] = useState('');
  const [successMsg, setSuccessMsg] = useState('');
  const [expandedFamily, setExpandedFamily] = useState<number | null>(null);
  const [members, setMembers] = useState<Record<number, FamilyMember[]>>({});
  const [updatingRoleKey, setUpdatingRoleKey] = useState<string | null>(null);
  const [editingRelationshipKey, setEditingRelationshipKey] = useState<string | null>(null);
  const [relationshipDraft, setRelationshipDraft] = useState('');
  const [updatingRelationshipKey, setUpdatingRelationshipKey] = useState<string | null>(null);
  const [careAuthorizations, setCareAuthorizations] = useState<Record<number, CareAuthorization[]>>({});
  const [updatingCareKey, setUpdatingCareKey] = useState<string | null>(null);

  const memberAccountName = (member: FamilyMember) =>
    member.nickname?.trim() || member.username?.trim() || `用户 ${member.userId}`;

  const memberDisplayName = (member: FamilyMember) =>
    member.relationshipLabel?.trim() || memberAccountName(member);

  const memberBirthDate = (member: FamilyMember) => {
    const value = member.birthDate
      || (typeof member.metadata?.birthDate === 'string' ? member.metadata.birthDate : '')
      || (typeof member.metadata?.birthday === 'string' ? member.metadata.birthday : '')
      || (typeof member.metadata?.dateOfBirth === 'string' ? member.metadata.dateOfBirth : '');
    return value ? value.slice(0, 10) : '';
  };

  const memberAge = (member: FamilyMember) => {
    const birthDate = memberBirthDate(member);
    if (birthDate) {
      const date = new Date(birthDate);
      if (!Number.isNaN(date.getTime())) {
        const now = new Date();
        let age = now.getFullYear() - date.getFullYear();
        const monthDelta = now.getMonth() - date.getMonth();
        if (monthDelta < 0 || (monthDelta === 0 && now.getDate() < date.getDate())) age -= 1;
        if (age >= 0 && age <= 130) return { age, isDefault: false };
      }
    }
    const year = Number(member.birthYear || member.metadata?.birthYear || member.metadata?.yearOfBirth);
    if (Number.isFinite(year) && year > 1870 && year <= new Date().getFullYear()) {
      return { age: new Date().getFullYear() - year, isDefault: false };
    }
    return { age: 20, isDefault: true };
  };

  const memberProfileLine = (member: FamilyMember) => {
    const birthDate = memberBirthDate(member);
    const age = memberAge(member);
    return `${birthDate ? `生日：${birthDate}` : '生日未设置'} · 年龄：${age.age} 岁${age.isDefault ? '（默认）' : ''}`;
  };

  const loadFamilies = useCallback(async () => {
    setLoading(true);
    try {
      setError('');
      const data = await familyApi.getMyFamilies();
      setFamilies(Array.isArray(data) ? data : []);
    } catch (err) {
      console.error('Failed to load families', err);
      setError(err instanceof Error ? err.message : '家族列表加载失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { loadFamilies(); }, [loadFamilies]);

  const showMsg = (msg: string) => {
    setSuccessMsg(msg);
    setTimeout(() => setSuccessMsg(''), 3000);
  };

  const handleCreate = async () => {
    if (!newFamilyName.trim()) return;
    setError('');
    try {
      const created = await familyApi.create({ name: newFamilyName, description: newFamilyDesc || undefined });
      setActiveFamilyId(created.id);
      setShowCreate(false);
      setNewFamilyName('');
      setNewFamilyDesc('');
      showMsg('家族创建成功');
      await loadFamilies();
      notifyViewerRoleChanged();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : '创建失败');
    }
  };

  const handleJoin = async () => {
    if (!inviteCode.trim()) return;
    setError('');
    try {
      const joined = await familyApi.join(inviteCode);
      setActiveFamilyId(joined.familyId);
      setShowJoin(false);
      setInviteCode('');
      showMsg('加入成功');
      await loadFamilies();
      notifyViewerRoleChanged();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : '加入失败');
    }
  };

  const handleExpand = async (familyId: number) => {
    setActiveFamilyId(familyId);
    if (expandedFamily === familyId) { setExpandedFamily(null); return; }
    setExpandedFamily(familyId);
    if (!members[familyId] || !careAuthorizations[familyId]) {
      try {
        const [m, auths] = await Promise.all([
          familyApi.getMembers(familyId),
          familyApi.getMyCareAuthorizations(familyId),
        ]);
        setMembers((prev) => ({ ...prev, [familyId]: Array.isArray(m) ? m : [] }));
        setCareAuthorizations((prev) => ({ ...prev, [familyId]: Array.isArray(auths) ? auths : [] }));
      } catch { /* ignore */ }
    }
  };

  const refreshMembers = async (familyId: number) => {
    const [m, auths] = await Promise.all([
      familyApi.getMembers(familyId),
      familyApi.getMyCareAuthorizations(familyId),
    ]);
    setMembers((prev) => ({ ...prev, [familyId]: Array.isArray(m) ? m : [] }));
    setCareAuthorizations((prev) => ({ ...prev, [familyId]: Array.isArray(auths) ? auths : [] }));
  };

  const handleUpdateRole = async (familyId: number, member: FamilyMember, role: FamilyMember['role']) => {
    if (member.role === role) return;
    const key = `${familyId}:${member.userId}`;
    setUpdatingRoleKey(key);
    setError('');
    try {
      const updated = await familyApi.updateMemberRole(familyId, member.userId, role);
      setMembers((prev) => ({
        ...prev,
        [familyId]: (prev[familyId] || []).map((item) => (item.userId === member.userId ? updated : item)),
      }));
      showMsg('成员角色已更新');
      notifyViewerRoleChanged();
    } catch (err) {
      setError(err instanceof Error ? err.message : '角色更新失败');
      await refreshMembers(familyId).catch(() => {});
    } finally {
      setUpdatingRoleKey(null);
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
      setError('称呼不能为空');
      return;
    }
    const key = `${familyId}:${member.userId}`;
    setUpdatingRelationshipKey(key);
    setError('');
    try {
      const updated = await familyApi.upsertRelationshipLabel(familyId, member.userId, { label });
      setMembers((prev) => ({
        ...prev,
        [familyId]: (prev[familyId] || []).map((item) => (
          item.userId === member.userId
            ? {
                ...item,
                relationshipLabel: updated.label,
                reverseRelationshipLabel: updated.reverseLabel,
              }
            : item
        )),
      }));
      showMsg('称呼已更新');
      cancelEditRelationship();
    } catch (err) {
      setError(err instanceof Error ? err.message : '称呼更新失败');
    } finally {
      setUpdatingRelationshipKey(null);
    }
  };

  const hasActiveCareScope = (familyId: number, caregiverUserId: number, scope: 'DIARY' | 'GROWTH_GUARD') => {
    if (!currentUserId) return false;
    return (careAuthorizations[familyId] || []).some((item) => (
      item.subjectUserId === currentUserId
      && item.caregiverUserId === caregiverUserId
      && item.status === 'ACTIVE'
      && (item.scope === 'ALL' || item.scope === scope)
    ));
  };

  const hasCareAuthorization = (familyId: number, caregiverUserId: number) =>
    hasActiveCareScope(familyId, caregiverUserId, 'DIARY')
    && hasActiveCareScope(familyId, caregiverUserId, 'GROWTH_GUARD');

  const handleToggleCareAuthorization = async (familyId: number, caregiver: FamilyMember) => {
    if (!currentUserId || caregiver.userId === currentUserId) return;
    const key = `${familyId}:${caregiver.userId}:care`;
    const active = hasCareAuthorization(familyId, caregiver.userId);
    setUpdatingCareKey(key);
    setError('');
    try {
      const updates = await Promise.all([
        familyApi.upsertCareAuthorization(familyId, currentUserId, caregiver.userId, {
          scope: 'GROWTH_GUARD',
          active: !active,
        }),
        familyApi.upsertCareAuthorization(familyId, currentUserId, caregiver.userId, {
          scope: 'DIARY',
          active: !active,
        }),
      ]);
      setCareAuthorizations((prev) => {
        const existing = prev[familyId] || [];
        const next = existing.filter((item) => !(
          item.subjectUserId === currentUserId
          && item.caregiverUserId === caregiver.userId
          && updates.some((updated) => item.scope === updated.scope)
        ));
        return { ...prev, [familyId]: [...next, ...updates] };
      });
      showMsg(!active ? '已授权查看我的照护类记录' : '已撤销照护类记录授权');
    } catch (err) {
      setError(err instanceof Error ? err.message : '照护授权更新失败');
    } finally {
      setUpdatingCareKey(null);
    }
  };


  const copyInviteCode = (code: string) => {
    navigator.clipboard.writeText(code);
    setCopiedCode(code);
    setTimeout(() => setCopiedCode(null), 2000);
  };

  const roleBadge = (role: string) => {
    switch (role) {
      case 'OWNER': return { icon: Crown, label: familyRoleLabel(role), cls: 'text-yellow-600 bg-yellow-50' };
      default: return { icon: User, label: familyRoleLabel(role), cls: 'text-gray-600 bg-gray-100' };
    }
  };

  const isLegacyFamilyRole = (role?: string) => {
    const normalized = (role || '').toUpperCase();
    return normalized !== 'OWNER' && normalized !== 'MEMBER';
  };

  return (
    <div className="max-w-3xl mx-auto">
      {/* Header */}
      <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-lg font-bold text-gray-900 sm:text-xl">家族空间</h1>
          <p className="text-sm text-gray-500">管理家族，邀请成员一起使用家庭陪伴 AI</p>
        </div>
        <div className="grid grid-cols-2 gap-2 sm:flex">
          <button onClick={() => { setShowJoin(true); setShowCreate(false); setError(''); }}
            className="flex items-center gap-1.5 px-4 py-2 bg-white border border-gray-200 text-gray-700 rounded-lg text-sm hover:bg-gray-50 transition-colors">
            <UserPlus className="w-4 h-4" /> 加入家族
          </button>
          <button onClick={() => { setShowCreate(true); setShowJoin(false); setError(''); }}
            className="flex items-center gap-1.5 px-4 py-2 bg-blue-600 text-white rounded-lg text-sm hover:bg-blue-700 transition-colors">
            <Plus className="w-4 h-4" /> 创建家族
          </button>
        </div>
      </div>

      {/* Messages */}
      {error && <div className="mb-4 bg-red-50 text-red-600 px-4 py-2 rounded-lg text-sm">{error}</div>}
      {successMsg && <div className="mb-4 bg-green-50 text-green-600 px-4 py-2 rounded-lg text-sm">{successMsg}</div>}

      {/* Create form */}
      {showCreate && (
        <div className="mb-6 bg-white border border-gray-200 rounded-xl p-5">
          <h3 className="font-semibold text-gray-900 mb-3">创建新家族</h3>
          <input type="text" value={newFamilyName}
            onChange={(e) => setNewFamilyName(e.target.value)}
            placeholder="家族名称，例如：王家学习小组"
            className="w-full px-4 py-2.5 border border-gray-200 rounded-lg text-sm outline-none focus:ring-2 focus:ring-blue-500 mb-3" autoFocus />
          <textarea value={newFamilyDesc}
            onChange={(e) => setNewFamilyDesc(e.target.value)}
            placeholder="家族说明（选填）" rows={2}
            className="w-full px-4 py-2.5 border border-gray-200 rounded-lg text-sm outline-none focus:ring-2 focus:ring-blue-500 resize-none mb-3" />
          <div className="flex gap-2 justify-end">
            <button onClick={() => setShowCreate(false)} className="px-4 py-2 text-sm text-gray-600 hover:bg-gray-50 rounded-lg">取消</button>
            <button onClick={handleCreate} disabled={!newFamilyName.trim()}
              className="px-5 py-2 text-sm bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 font-medium">创建</button>
          </div>
        </div>
      )}

      {/* Join form */}
      {showJoin && (
        <div className="mb-6 bg-white border border-gray-200 rounded-xl p-5">
          <h3 className="font-semibold text-gray-900 mb-3">通过邀请码加入</h3>
          <div className="flex flex-col gap-2 sm:flex-row">
            <input type="text" value={inviteCode}
              onChange={(e) => setInviteCode(e.target.value.toUpperCase())}
              placeholder="请输入 8 位邀请码" maxLength={8}
              className="min-w-0 flex-1 px-4 py-2.5 border border-gray-200 rounded-lg text-sm outline-none focus:ring-2 focus:ring-blue-500 uppercase tracking-widest font-mono" autoFocus />
            <button onClick={handleJoin} disabled={inviteCode.length < 8}
              className="px-5 py-2.5 bg-blue-600 text-white text-sm rounded-lg hover:bg-blue-700 disabled:opacity-50 font-medium">加入</button>
          </div>
          <button onClick={() => setShowJoin(false)} className="mt-2 text-sm text-gray-500 hover:text-gray-700">取消</button>
        </div>
      )}

      {/* Family list */}
      {loading ? (
        <div className="flex items-center justify-center h-40 text-gray-400">
          <RefreshCw className="w-5 h-5 animate-spin mr-2" /> 加载中...
        </div>
      ) : families.length === 0 ? (
        <div className="bg-white border border-gray-200 rounded-xl p-12 text-center">
          <Users className="w-12 h-12 text-gray-200 mx-auto mb-3" />
          <h3 className="text-lg font-medium text-gray-700 mb-1">还没有家族</h3>
          <p className="text-sm text-gray-400 mb-5">创建一个家族，邀请成员一起使用家庭陪伴 AI</p>
          <button onClick={() => setShowCreate(true)}
            className="px-5 py-2.5 bg-blue-600 text-white text-sm rounded-lg hover:bg-blue-700 font-medium">
            创建第一个家族
          </button>
        </div>
      ) : (
        <div className="space-y-4">
          {families.map((family) => {
            const isExpanded = expandedFamily === family.id;
            const memberList = members[family.id] || [];
            const currentMember = memberList.find((member) => member.userId === currentUserId);
            const canManageRoles = currentMember?.role === 'OWNER';
            return (
              <div key={family.id}
                className={`bg-white border rounded-xl overflow-hidden hover:shadow-sm transition-shadow ${
                  activeFamilyId === family.id ? 'border-blue-200 ring-1 ring-blue-100' : 'border-gray-200'
                }`}>
                <div className="p-5">
                  <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                    <div className="flex min-w-0 items-center gap-3">
                      <div className="w-10 h-10 bg-purple-100 text-purple-600 rounded-xl flex items-center justify-center text-lg font-bold">
                        {family.name.charAt(0)}
                      </div>
                      <div className="min-w-0">
                        <h3 className="truncate font-semibold text-gray-900">{family.name}</h3>
                        {family.description && <p className="text-sm text-gray-500 mt-0.5">{family.description}</p>}
                      </div>
                    </div>
                    <div className="text-xs text-gray-400 sm:text-right">最多 {family.maxMembers} 名成员</div>
                  </div>

                  <div className="flex flex-wrap items-center gap-2 mt-4 pt-3 border-t border-gray-100">
                    {family.inviteCode && (
                      <button onClick={() => copyInviteCode(family.inviteCode!)}
                        className="flex items-center gap-1 px-3 py-1.5 bg-gray-50 text-gray-600 text-xs rounded-lg hover:bg-gray-100 transition-colors">
                        {copiedCode === family.inviteCode
                          ? <><CheckCircle className="w-3.5 h-3.5 text-green-500" /> 已复制</>
                          : <><Copy className="w-3.5 h-3.5" /> {family.inviteCode}</>
                        }
                      </button>
                    )}
                    <button onClick={() => handleExpand(family.id)}
                      className="flex items-center gap-1 px-3 py-1.5 text-xs text-gray-500 hover:text-gray-700 hover:bg-gray-50 rounded-lg transition-colors">
                      {isExpanded ? <ChevronUp className="w-3.5 h-3.5" /> : <ChevronDown className="w-3.5 h-3.5" />}
                      成员 {isExpanded ? '收起' : '查看'}
                    </button>
                    {activeFamilyId === family.id && (
                      <span className="rounded-full bg-blue-50 px-2.5 py-1 text-xs font-medium text-blue-700">
                        当前家族
                      </span>
                    )}
                    <span className="w-full text-xs text-gray-400 sm:ml-auto sm:w-auto">
                      创建于 {new Date(family.createdAt).toLocaleDateString()}
                    </span>
                  </div>
                </div>

                {isExpanded && (
                  <div className="border-t border-gray-100 bg-gray-50 px-5 py-3">
                    {memberList.length === 0 ? (
                      <p className="text-xs text-gray-400 py-2">成员加载中...</p>
                    ) : (
                      <div className="space-y-1.5">
                        {memberList.map((m) => {
                          const badge = roleBadge(m.role || 'MEMBER');
                          const BadgeIcon = badge.icon;
                          const canNormalizeRole = canManageRoles && m.role !== 'OWNER' && isLegacyFamilyRole(m.role);
                          const canEditRelationship = m.userId !== currentUserId;
                          const updateKey = `${family.id}:${m.userId}`;
                          const careKey = `${family.id}:${m.userId}:care`;
                          const isEditingRelationship = editingRelationshipKey === updateKey;
                          const careAuthorized = hasCareAuthorization(family.id, m.userId);
                          const accountName = memberAccountName(m);
                          const displayName = memberDisplayName(m);
                          return (
                            <div key={m.id} className="flex flex-col gap-2 py-1.5 sm:flex-row sm:items-center sm:justify-between">
                              <div className="flex min-w-0 items-center gap-2">
                                <div className="w-6 h-6 bg-white border border-gray-200 rounded-full flex items-center justify-center text-[10px] font-medium text-gray-500">
                                  {displayName.charAt(0).toUpperCase()}
                                </div>
                                {isEditingRelationship ? (
                                  <form
                                    className="flex min-w-0 items-center gap-1"
                                    onSubmit={(event) => {
                                      event.preventDefault();
                                      void handleUpdateRelationship(family.id, m);
                                    }}
                                  >
                                    <input
                                      value={relationshipDraft}
                                      onChange={(event) => setRelationshipDraft(event.target.value)}
                                      maxLength={60}
                                      autoFocus
                                      placeholder="例如：妈妈、二叔、小林"
                                      className="h-8 min-w-0 rounded-md border border-gray-200 bg-white px-2 text-xs text-gray-700 outline-none focus:ring-2 focus:ring-blue-500"
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
                                      className="flex h-7 w-7 items-center justify-center rounded-md text-gray-400 hover:bg-gray-100 hover:text-gray-600"
                                      aria-label="取消"
                                    >
                                      <X className="h-3.5 w-3.5" />
                                    </button>
                                  </form>
                                ) : (
                                  <div className="min-w-0">
                                    <div className="flex min-w-0 items-center gap-1.5">
                                      <Link
                                        href={`/dashboard/family/member?familyId=${family.id}&userId=${m.userId}`}
                                        className="truncate text-sm font-medium text-gray-900 hover:text-purple-700 hover:underline"
                                        title="查看成员经验"
                                      >
                                        {displayName}
                                      </Link>
                                      {canEditRelationship && (
                                        <button
                                          type="button"
                                          onClick={() => startEditRelationship(family.id, m)}
                                          className="flex h-6 w-6 shrink-0 items-center justify-center rounded-md text-gray-400 hover:bg-white hover:text-blue-600"
                                          title="设置我对 TA 的称呼"
                                          aria-label="设置称呼"
                                        >
                                          <Pencil className="h-3.5 w-3.5" />
                                        </button>
                                      )}
                                    </div>
                                    <p className="truncate text-[11px] text-gray-400">
                                      {m.relationshipLabel?.trim() ? `${accountName} · ` : ''}{memberProfileLine(m)}
                                    </p>
                                  </div>
                                )}
                              </div>
                              <div className="flex shrink-0 items-center gap-2">
                                <Link
                                  href={`/dashboard/family/member?familyId=${family.id}&userId=${m.userId}`}
                                  className="inline-flex h-8 shrink-0 items-center gap-1.5 rounded-lg bg-purple-600 px-3 text-xs font-semibold text-white shadow-sm transition-colors hover:bg-purple-700"
                                  title="查看该成员的记忆视图"
                                >
                                  <BookHeart className="h-3.5 w-3.5" />
                                  成员经验
                                </Link>
                                <span className={`inline-flex shrink-0 items-center gap-1 text-[10px] px-2 py-0.5 rounded-full font-medium ${badge.cls}`}>
                                  <BadgeIcon className="w-3 h-3" /> {badge.label}
                                </span>
                                {m.userId !== currentUserId && (
                                  <button
                                    type="button"
                                    disabled={updatingCareKey === careKey}
                                    onClick={() => void handleToggleCareAuthorization(family.id, m)}
                                    className={`inline-flex shrink-0 items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-medium transition-colors disabled:opacity-50 ${
                                      careAuthorized
                                        ? 'bg-emerald-50 text-emerald-700 hover:bg-emerald-100'
                                        : 'bg-gray-100 text-gray-500 hover:bg-gray-200'
                                    }`}
                                    title={careAuthorized ? '撤销 TA 查看我的照护类记录' : '允许 TA 查看我的照护类记录'}
                                  >
                                    <Eye className="h-3 w-3" />
                                    {careAuthorized ? '已授权照护' : '授权照护'}
                                  </button>
                                )}
                                {canNormalizeRole && (
                                  <button
                                    type="button"
                                    disabled={updatingRoleKey === updateKey}
                                    onClick={() => void handleUpdateRole(family.id, m, 'MEMBER')}
                                    className="rounded-md border border-gray-200 bg-white px-2 py-1 text-xs text-gray-600 hover:border-blue-200 hover:text-blue-700 disabled:opacity-50"
                                    title="将历史身份归并为普通成员"
                                  >
                                    归并为成员
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
