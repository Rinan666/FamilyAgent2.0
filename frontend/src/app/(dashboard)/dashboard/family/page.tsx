'use client';

import { useState, useEffect, useCallback } from 'react';
import { familyApi } from '@/lib/api';
import type { Family, FamilyMember } from '@/types';
import {
  Users, Plus, Copy, CheckCircle, UserPlus, Crown, Shield,
  ChevronDown, ChevronUp, User, RefreshCw,
} from 'lucide-react';

export default function FamilyPage() {
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
      await familyApi.create({ name: newFamilyName, description: newFamilyDesc || undefined });
      setShowCreate(false);
      setNewFamilyName('');
      setNewFamilyDesc('');
      showMsg('家族创建成功');
      await loadFamilies();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : '创建失败');
    }
  };

  const handleJoin = async () => {
    if (!inviteCode.trim()) return;
    setError('');
    try {
      await familyApi.join(inviteCode);
      setShowJoin(false);
      setInviteCode('');
      showMsg('加入成功');
      await loadFamilies();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : '加入失败');
    }
  };

  const handleExpand = async (familyId: number) => {
    if (expandedFamily === familyId) { setExpandedFamily(null); return; }
    setExpandedFamily(familyId);
    if (!members[familyId]) {
      try {
        const m = await familyApi.getMembers(familyId);
        setMembers((prev) => ({ ...prev, [familyId]: Array.isArray(m) ? m : [] }));
      } catch { /* ignore */ }
    }
  };

  const copyInviteCode = (code: string) => {
    navigator.clipboard.writeText(code);
    setCopiedCode(code);
    setTimeout(() => setCopiedCode(null), 2000);
  };

  const roleBadge = (role: string) => {
    switch (role) {
      case 'OWNER': return { icon: Crown, label: '创建者', cls: 'text-yellow-600 bg-yellow-50' };
      case 'ADMIN': return { icon: Shield, label: '管理员', cls: 'text-blue-600 bg-blue-50' };
      default: return { icon: User, label: '成员', cls: 'text-gray-600 bg-gray-100' };
    }
  };

  return (
    <div className="max-w-3xl mx-auto">
      {/* Header */}
      <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-lg font-bold text-gray-900 sm:text-xl">家族空间</h1>
          <p className="text-sm text-gray-500">管理家族，邀请成员一起使用 AI 家教</p>
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
          <p className="text-sm text-gray-400 mb-5">创建一个家族，邀请成员一起使用 AI 家教</p>
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
            return (
              <div key={family.id}
                className="bg-white border border-gray-200 rounded-xl overflow-hidden hover:shadow-sm transition-shadow">
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
                          return (
                            <div key={m.id} className="flex items-center justify-between gap-3 py-1.5">
                              <div className="flex min-w-0 items-center gap-2">
                                <div className="w-6 h-6 bg-white border border-gray-200 rounded-full flex items-center justify-center text-[10px] font-medium text-gray-500">
                                  {(m.nickname || m.username || '?').charAt(0).toUpperCase()}
                                </div>
                                <span className="truncate text-sm text-gray-800">{m.nickname || m.username || `用户 ${m.userId}`}</span>
                              </div>
                              <span className={`inline-flex shrink-0 items-center gap-1 text-[10px] px-2 py-0.5 rounded-full font-medium ${badge.cls}`}>
                                <BadgeIcon className="w-3 h-3" /> {badge.label}
                              </span>
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
