'use client';

import { useState, useEffect, useCallback } from 'react';
import { assessmentApi, questionApi } from '@/lib/api';
import { useAuthStore } from '@/stores/authStore';
import { masteryColor, masteryLevel } from '@/lib/utils';
import type { AbilityProfile, KnowledgePoint } from '@/types';
import {
  RadarChart, PolarGrid, PolarAngleAxis, PolarRadiusAxis,
  Radar, ResponsiveContainer, Tooltip,
} from 'recharts';
import { TrendingUp, Target, Zap, AlertTriangle } from 'lucide-react';

export default function AssessmentPage() {
  const [profiles, setProfiles] = useState<AbilityProfile[]>([]);
  const [kpNames, setKpNames] = useState<Record<number, string>>({});
  const [loading, setLoading] = useState(true);

  const userId = useAuthStore((s) => s.user?.id);

  const loadAll = useCallback(async () => {
    setLoading(true);
    try {
      const [profileData, treeData] = await Promise.all([
        assessmentApi.getProfiles(userId!),
        questionApi.getKnowledgeTree(),
      ]);
      setProfiles(profileData || []);
      // Build kpNames from knowledge tree (flatten tree)
      const names: Record<number, string> = {};
      const flattenTree = (nodes: KnowledgePoint[]) => {
        for (const n of nodes) {
          names[n.id] = n.name;
          if ((n as unknown as { children?: KnowledgePoint[] }).children) {
            flattenTree((n as unknown as { children: KnowledgePoint[] }).children);
          }
        }
      };
      flattenTree(treeData || []);
      setKpNames(names);
    } catch (err) {
      console.error('Failed to load assessment data', err);
    } finally {
      setLoading(false);
    }
  }, [userId]);

  useEffect(() => {
    if (userId) {
      loadAll();
    } else {
      setLoading(false);
    }
  }, [userId, loadAll]);

  // Radar chart data
  const radarData = profiles.map((p) => ({
    subject: kpNames[p.kpId] || `KP-${p.kpId}`,
    value: Math.round(p.masteryProbability * 100),
    fullMark: 100,
  }));

  // Statistics
  const mastered = profiles.filter((p) => p.masteryProbability >= 0.85).length;
  const weak = profiles.filter((p) => p.masteryProbability < 0.4).length;
  const zpd = profiles.filter((p) => p.masteryProbability >= 0.3 && p.masteryProbability <= 0.7).length;
  const avgMastery = profiles.length > 0
    ? Math.round((profiles.reduce((sum, p) => sum + p.masteryProbability, 0) / profiles.length) * 100)
    : 0;

  const stats = [
    { label: '平均掌握度', value: `${avgMastery}%`, icon: TrendingUp, color: 'text-blue-600 bg-blue-50' },
    { label: '已掌握', value: `${mastered}个`, icon: Zap, color: 'text-green-600 bg-green-50' },
    { label: '需要加强', value: `${weak}个`, icon: AlertTriangle, color: 'text-orange-600 bg-orange-50' },
    { label: '最近发展区', value: `${zpd}个`, icon: Target, color: 'text-purple-600 bg-purple-50' },
  ];

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64 text-gray-500">
        加载中...
      </div>
    );
  }

  if (!userId) {
    return (
      <div className="flex items-center justify-center h-64 text-gray-400">
        请先登录以查看学力评估
      </div>
    );
  }

  return (
    <div className="max-w-5xl mx-auto">
      <div className="mb-6">
        <h1 className="text-xl font-bold text-gray-900">学力评估</h1>
        <p className="text-sm text-gray-500">知识图谱 + 掌握概率可视化</p>
      </div>

      {/* Stats cards */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
        {stats.map((stat) => {
          const Icon = stat.icon;
          return (
            <div key={stat.label} className="bg-white rounded-xl border border-gray-200 p-4">
              <div className="flex items-center gap-2 mb-2">
                <div className={`p-1.5 rounded-lg ${stat.color}`}>
                  <Icon className="w-4 h-4" />
                </div>
                <span className="text-xs text-gray-500">{stat.label}</span>
              </div>
              <div className="text-2xl font-bold text-gray-900">{stat.value}</div>
            </div>
          );
        })}
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        {/* Radar chart */}
        <div className="md:col-span-1 bg-white rounded-xl border border-gray-200 p-6">
          <h3 className="font-semibold text-gray-900 mb-4">能力雷达图</h3>
          {radarData.length > 0 ? (
            <ResponsiveContainer width="100%" height={300}>
              <RadarChart data={radarData}>
                <PolarGrid />
                <PolarAngleAxis dataKey="subject" tick={{ fontSize: 12 }} />
                <PolarRadiusAxis angle={30} domain={[0, 100]} />
                <Radar name="掌握度" dataKey="value" stroke="#3b82f6" fill="#3b82f6" fillOpacity={0.2} />
                <Tooltip />
              </RadarChart>
            </ResponsiveContainer>
          ) : (
            <div className="h-48 flex items-center justify-center text-gray-400 text-sm">
              暂无数据，完成测试后生成
            </div>
          )}
        </div>

        {/* Knowledge point detail list */}
        <div className="md:col-span-2 bg-white rounded-xl border border-gray-200 p-6">
          <h3 className="font-semibold text-gray-900 mb-4">知识点掌握详情</h3>
          {profiles.length === 0 ? (
            <div className="text-center py-8 text-gray-400">
              <p>还没有评估数据</p>
              <p className="text-sm mt-1">完成一次测试后这里会显示你的学力档案</p>
            </div>
          ) : (
            <div className="space-y-2">
              {profiles.map((profile) => (
                <div key={profile.kpId} className="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
                  <div className="flex-1">
                    <div className="flex items-center gap-2">
                      <span className="text-sm font-medium text-gray-900">
                        {kpNames[profile.kpId] || `知识点${profile.kpId}`}
                      </span>
                      <span className={`text-xs ${masteryColor(profile.masteryProbability)}`}>
                        {masteryLevel(profile.masteryProbability)}
                      </span>
                    </div>
                    <div className="text-xs text-gray-400 mt-0.5">
                      答题 {profile.totalAttempts} 次 · 正确 {profile.correctAttempts} 次
                      {profile.consecutiveCorrect >= 3 && ' · 连续正确！'}
                    </div>
                  </div>
                  <div className="ml-4">
                    <div className="w-32 bg-gray-200 rounded-full h-2">
                      <div
                        className="h-2 rounded-full transition-all"
                        style={{
                          width: `${Math.round(profile.masteryProbability * 100)}%`,
                          backgroundColor:
                            profile.masteryProbability < 0.3 ? '#ef4444'
                            : profile.masteryProbability < 0.6 ? '#eab308'
                            : profile.masteryProbability < 0.85 ? '#3b82f6'
                            : '#22c55e',
                        }}
                      />
                    </div>
                    <div className="text-right text-xs text-gray-400 mt-0.5">
                      {Math.round(profile.masteryProbability * 100)}%
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
