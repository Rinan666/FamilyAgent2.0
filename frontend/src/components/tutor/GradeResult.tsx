'use client';

import type { GradeResult as GradeResultType } from '@/types';
import { cn } from '@/lib/utils';
import { CheckCircle, XCircle, AlertCircle } from 'lucide-react';

interface GradeResultProps {
  result: GradeResultType;
  onClose?: () => void;
}

export default function GradeResult({ result, onClose }: GradeResultProps) {
  const scoreColor =
    result.overallScore >= 90
      ? 'text-green-600'
      : result.overallScore >= 60
        ? 'text-yellow-600'
        : 'text-red-600';

  return (
    <div className="bg-white border border-gray-200 rounded-xl p-6 space-y-4">
      {/* 总分 */}
      <div className="flex items-center justify-between">
        <h3 className="font-semibold text-lg">批改结果</h3>
        <button
          onClick={onClose}
          className="text-sm text-gray-400 hover:text-gray-600"
        >
          关闭
        </button>
      </div>

      <div className="flex items-center gap-4">
        <div className={cn('text-4xl font-bold', scoreColor)}>
          {result.overallScore}
          <span className="text-lg text-gray-400 font-normal">/100</span>
        </div>
        <div>
          {result.isCorrect ? (
            <span className="flex items-center gap-1 text-green-600 text-sm">
              <CheckCircle className="w-4 h-4" />
              答案正确
            </span>
          ) : (
            <span className="flex items-center gap-1 text-red-600 text-sm">
              <XCircle className="w-4 h-4" />
              答案有误
            </span>
          )}
        </div>
      </div>

      {/* 步骤评分 */}
      <div className="space-y-2">
        <h4 className="text-sm font-medium text-gray-700">步骤评分</h4>
        {result.stepGrades.map((step) => (
          <div
            key={step.stepNumber}
            className={cn(
              'flex items-center justify-between p-3 rounded-lg text-sm',
              step.isCorrect ? 'bg-green-50' : 'bg-red-50',
            )}
          >
            <div className="flex-1">
              <div className="font-medium">
                步骤{step.stepNumber}：{step.stepName}
              </div>
              <div className="text-gray-500 mt-0.5">{step.feedback}</div>
              {step.errorType && step.errorType !== '无' && (
                <span className="inline-block mt-1 text-xs px-2 py-0.5 bg-red-100 text-red-700 rounded">
                  {step.errorType}
                </span>
              )}
            </div>
            <div className="text-right ml-4">
              <span className="font-bold">{step.score}</span>
              <span className="text-gray-400">/{step.maxScore}</span>
            </div>
          </div>
        ))}
      </div>

      {/* 错误分析 */}
      {result.errorAnalysis && (
        <div className="bg-orange-50 rounded-lg p-4">
          <div className="flex items-start gap-2">
            <AlertCircle className="w-5 h-5 text-orange-500 mt-0.5 shrink-0" />
            <div>
              <div className="font-medium text-sm text-orange-800">
                {result.errorAnalysis.primaryErrorType !== '无'
                  ? `主要错误：${result.errorAnalysis.primaryErrorType}`
                  : '无主要错误'}
              </div>
              {result.errorAnalysis.knowledgeGaps.length > 0 && (
                <div className="text-sm text-orange-700 mt-1">
                  知识漏洞：{result.errorAnalysis.knowledgeGaps.join('、')}
                </div>
              )}
              {result.errorAnalysis.suggestion && (
                <div className="text-sm text-orange-700 mt-1">
                  建议：{result.errorAnalysis.suggestion}
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* 整体反馈 */}
      <p className="text-sm text-gray-600 border-t pt-4">
        {result.overallFeedback}
      </p>
    </div>
  );
}
