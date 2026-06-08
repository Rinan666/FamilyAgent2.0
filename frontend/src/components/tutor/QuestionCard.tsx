'use client';

import type { Question } from '@/types';
import { difficultyLabel, subjectLabel } from '@/lib/utils';

interface QuestionCardProps {
  question: Question;
  onAsk?: () => void;
}

export default function QuestionCard({ question, onAsk }: QuestionCardProps) {
  const content = question.content;

  return (
    <div className="bg-white border border-gray-200 rounded-xl p-4">
      {/* 标签 */}
      <div className="flex items-center gap-2 mb-3">
        <span className="text-xs px-2 py-0.5 bg-blue-100 text-blue-700 rounded">
          {subjectLabel(question.subject)}
        </span>
        <span className="text-xs px-2 py-0.5 bg-gray-100 text-gray-600 rounded">
          {difficultyLabel(question.difficulty)}
        </span>
        <span className="text-xs px-2 py-0.5 bg-gray-100 text-gray-600 rounded">
          {question.type === 'CHOICE'
            ? '选择题'
            : question.type === 'FILL'
              ? '填空题'
              : question.type === 'PROOF'
                ? '证明题'
                : '计算题'}
        </span>
      </div>

      {/* 题干 */}
      <p className="text-gray-900 mb-3 whitespace-pre-wrap">{content.stem}</p>

      {/* 选项 */}
      {content.options && (
        <div className="space-y-1.5 mb-3">
          {content.options.map((opt, i) => (
            <div key={i} className="text-sm text-gray-600 pl-4">
              {String.fromCharCode(65 + i)}. {opt}
            </div>
          ))}
        </div>
      )}

      {/* 操作 */}
      {onAsk && (
        <button
          onClick={onAsk}
          className="mt-2 px-4 py-2 bg-blue-600 text-white text-sm rounded-lg hover:bg-blue-700 transition-colors"
        >
          向学习陪伴提问
        </button>
      )}
    </div>
  );
}
