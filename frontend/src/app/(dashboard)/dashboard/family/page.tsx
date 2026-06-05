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
      setError(err instanceof Error ? err.message : 'Failed to load families');
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
      showMsg('Family created!');
      await loadFamilies();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Create failed');
    }
  };

  const handleJoin = async () => {
    if (!inviteCode.trim()) return;
    setError('');
    try {
      await familyApi.join(inviteCode);
      setShowJoin(false);
      setInviteCode('');
      showMsg('Joined successfully!');
      await loadFamilies();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Join failed');
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
      case 'OWNER': return { icon: Crown, label: 'Owner', cls: 'text-yellow-600 bg-yellow-50' };
      case 'ADMIN': return { icon: Shield, label: 'Admin', cls: 'text-blue-600 bg-blue-50' };
      default: return { icon: User, label: 'Member', cls: 'text-gray-600 bg-gray-100' };
    }
  };

  return (
    <div className="max-w-3xl mx-auto">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-xl font-bold text-gray-900">Family Space</h1>
          <p className="text-sm text-gray-500">Manage your families, invite members to grow together</p>
        </div>
        <div className="flex gap-2">
          <button onClick={() => { setShowJoin(true); setShowCreate(false); setError(''); }}
            className="flex items-center gap-1.5 px-4 py-2 bg-white border border-gray-200 text-gray-700 rounded-lg text-sm hover:bg-gray-50 transition-colors">
            <UserPlus className="w-4 h-4" /> Join
          </button>
          <button onClick={() => { setShowCreate(true); setShowJoin(false); setError(''); }}
            className="flex items-center gap-1.5 px-4 py-2 bg-blue-600 text-white rounded-lg text-sm hover:bg-blue-700 transition-colors">
            <Plus className="w-4 h-4" /> Create
          </button>
        </div>
      </div>

      {/* Messages */}
      {error && <div className="mb-4 bg-red-50 text-red-600 px-4 py-2 rounded-lg text-sm">{error}</div>}
      {successMsg && <div className="mb-4 bg-green-50 text-green-600 px-4 py-2 rounded-lg text-sm">{successMsg}</div>}

      {/* Create form */}
      {showCreate && (
        <div className="mb-6 bg-white border border-gray-200 rounded-xl p-5">
          <h3 className="font-semibold text-gray-900 mb-3">Create New Family</h3>
          <input type="text" value={newFamilyName}
            onChange={(e) => setNewFamilyName(e.target.value)}
            placeholder="Family name (e.g. The Smiths)"
            className="w-full px-4 py-2.5 border border-gray-200 rounded-lg text-sm outline-none focus:ring-2 focus:ring-blue-500 mb-3" autoFocus />
          <textarea value={newFamilyDesc}
            onChange={(e) => setNewFamilyDesc(e.target.value)}
            placeholder="Description (optional)" rows={2}
            className="w-full px-4 py-2.5 border border-gray-200 rounded-lg text-sm outline-none focus:ring-2 focus:ring-blue-500 resize-none mb-3" />
          <div className="flex gap-2 justify-end">
            <button onClick={() => setShowCreate(false)} className="px-4 py-2 text-sm text-gray-600 hover:bg-gray-50 rounded-lg">Cancel</button>
            <button onClick={handleCreate} disabled={!newFamilyName.trim()}
              className="px-5 py-2 text-sm bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 font-medium">Create</button>
          </div>
        </div>
      )}

      {/* Join form */}
      {showJoin && (
        <div className="mb-6 bg-white border border-gray-200 rounded-xl p-5">
          <h3 className="font-semibold text-gray-900 mb-3">Join via Invite Code</h3>
          <div className="flex gap-2">
            <input type="text" value={inviteCode}
              onChange={(e) => setInviteCode(e.target.value.toUpperCase())}
              placeholder="Enter 8-char invite code" maxLength={8}
              className="flex-1 px-4 py-2.5 border border-gray-200 rounded-lg text-sm outline-none focus:ring-2 focus:ring-blue-500 uppercase tracking-widest font-mono" autoFocus />
            <button onClick={handleJoin} disabled={inviteCode.length < 8}
              className="px-5 py-2.5 bg-blue-600 text-white text-sm rounded-lg hover:bg-blue-700 disabled:opacity-50 font-medium">Join</button>
          </div>
          <button onClick={() => setShowJoin(false)} className="mt-2 text-sm text-gray-500 hover:text-gray-700">Cancel</button>
        </div>
      )}

      {/* Family list */}
      {loading ? (
        <div className="flex items-center justify-center h-40 text-gray-400">
          <RefreshCw className="w-5 h-5 animate-spin mr-2" /> Loading...
        </div>
      ) : families.length === 0 ? (
        <div className="bg-white border border-gray-200 rounded-xl p-12 text-center">
          <Users className="w-12 h-12 text-gray-200 mx-auto mb-3" />
          <h3 className="text-lg font-medium text-gray-700 mb-1">No families yet</h3>
          <p className="text-sm text-gray-400 mb-5">Create a family and invite members to use AI tutor together</p>
          <button onClick={() => setShowCreate(true)}
            className="px-5 py-2.5 bg-blue-600 text-white text-sm rounded-lg hover:bg-blue-700 font-medium">
            Create your first family
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
                  <div className="flex items-start justify-between">
                    <div className="flex items-center gap-3">
                      <div className="w-10 h-10 bg-purple-100 text-purple-600 rounded-xl flex items-center justify-center text-lg font-bold">
                        {family.name.charAt(0)}
                      </div>
                      <div>
                        <h3 className="font-semibold text-gray-900">{family.name}</h3>
                        {family.description && <p className="text-sm text-gray-500 mt-0.5">{family.description}</p>}
                      </div>
                    </div>
                    <div className="text-xs text-gray-400">Max {family.maxMembers} members</div>
                  </div>

                  <div className="flex items-center gap-2 mt-4 pt-3 border-t border-gray-100">
                    {family.inviteCode && (
                      <button onClick={() => copyInviteCode(family.inviteCode!)}
                        className="flex items-center gap-1 px-3 py-1.5 bg-gray-50 text-gray-600 text-xs rounded-lg hover:bg-gray-100 transition-colors">
                        {copiedCode === family.inviteCode
                          ? <><CheckCircle className="w-3.5 h-3.5 text-green-500" /> Copied</>
                          : <><Copy className="w-3.5 h-3.5" /> {family.inviteCode}</>
                        }
                      </button>
                    )}
                    <button onClick={() => handleExpand(family.id)}
                      className="flex items-center gap-1 px-3 py-1.5 text-xs text-gray-500 hover:text-gray-700 hover:bg-gray-50 rounded-lg transition-colors">
                      {isExpanded ? <ChevronUp className="w-3.5 h-3.5" /> : <ChevronDown className="w-3.5 h-3.5" />}
                      Members {isExpanded ? '(hide)' : '(show)'}
                    </button>
                    <span className="ml-auto text-xs text-gray-400">
                      Created {new Date(family.createdAt).toLocaleDateString()}
                    </span>
                  </div>
                </div>

                {isExpanded && (
                  <div className="border-t border-gray-100 bg-gray-50 px-5 py-3">
                    {memberList.length === 0 ? (
                      <p className="text-xs text-gray-400 py-2">Loading members...</p>
                    ) : (
                      <div className="space-y-1.5">
                        {memberList.map((m) => {
                          const badge = roleBadge(m.role || 'MEMBER');
                          const BadgeIcon = badge.icon;
                          return (
                            <div key={m.id} className="flex items-center justify-between py-1.5">
                              <div className="flex items-center gap-2">
                                <div className="w-6 h-6 bg-white border border-gray-200 rounded-full flex items-center justify-center text-[10px] font-medium text-gray-500">
                                  {(m.nickname || m.username || '?').charAt(0).toUpperCase()}
                                </div>
                                <span className="text-sm text-gray-800">{m.nickname || m.username || `User ${m.userId}`}</span>
                              </div>
                              <span className={`inline-flex items-center gap-1 text-[10px] px-2 py-0.5 rounded-full font-medium ${badge.cls}`}>
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
