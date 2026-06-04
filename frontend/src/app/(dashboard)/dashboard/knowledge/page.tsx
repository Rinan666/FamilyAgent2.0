'use client';

import { BookOpen, Clock, Sparkles } from 'lucide-react';

export default function KnowledgePage() {
  return (
    <div className="max-w-3xl mx-auto">
      <div className="mb-6">
        <h1 className="text-xl font-bold text-gray-900">知识库</h1>
        <p className="text-sm text-gray-500">家族智慧沉淀与代际传承</p>
      </div>

      <div className="bg-white border border-gray-200 rounded-xl p-12 text-center">
        <BookOpen className="w-16 h-16 text-gray-200 mx-auto mb-4" />
        <h3 className="text-lg font-medium text-gray-700 mb-2">
          家族知识库即将上线
        </h3>
        <p className="text-sm text-gray-400 max-w-md mx-auto mb-6">
          将家族的经验、智慧、技能结构化存储，让每一代人的积累都能被后代检索和使用。
        </p>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 max-w-lg mx-auto text-left">
          <div className="p-4 bg-amber-50 rounded-lg">
            <Sparkles className="w-5 h-5 text-amber-500 mb-2" />
            <h4 className="text-sm font-medium text-gray-900">智慧型</h4>
            <p className="text-xs text-gray-500 mt-1">
              人生经验、决策原则、家训
            </p>
          </div>
          <div className="p-4 bg-blue-50 rounded-lg">
            <BookOpen className="w-5 h-5 text-blue-500 mb-2" />
            <h4 className="text-sm font-medium text-gray-900">技能型</h4>
            <p className="text-xs text-gray-500 mt-1">
              烹饪配方、维修技巧、育儿经验
            </p>
          </div>
          <div className="p-4 bg-green-50 rounded-lg">
            <Clock className="w-5 h-5 text-green-500 mb-2" />
            <h4 className="text-sm font-medium text-gray-900">故事型</h4>
            <p className="text-xs text-gray-500 mt-1">
              家族历史、长辈传记、重要时刻
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
