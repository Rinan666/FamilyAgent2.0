'use client';

import { useState, useRef, useEffect } from 'react';
import type { Question } from '@/types';
import { Send, Loader2, FileText } from 'lucide-react';
import { useChat } from '@/hooks/useChat';
import { useChatStore } from '@/stores/chatStore';
import { tutorApi, questionApi } from '@/lib/api';

export default function TutorPage() {
  const [questions, setQuestions] = useState<Question[]>([]);
  const [input, setInput] = useState('');
  const [mode, setMode] = useState<'tutor' | 'test'>('tutor');

  const { messages, isStreaming, currentQuestion, askQuestion, sendMessage } = useChat();
  const addMessage = useChatStore((s) => s.addMessage);

  const chatEndRef = useRef<HTMLDivElement>(null);

  // Auto-scroll
  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  // Load questions on mount
  useEffect(() => {
    questionApi.listQuestions({ page: 1, size: 20 })
      .then((data) => setQuestions(data?.items || []))
      .catch((err: unknown) => {
        console.log('Failed to load questions, using demo data:', err);
        setQuestions([
          {
            id: -1, kpId: 6, subject: 'math', grade: 'grade7',
            type: 'CALCULATION', difficulty: 2,
            content: { stem: '解方程：2x + 5 = 13' },
            answer: { value: 'x = 4', steps: ['移项：2x = 8', 'x = 4'] },
          },
          {
            id: -2, kpId: 7, subject: 'math', grade: 'grade7',
            type: 'CHOICE', difficulty: 2,
            content: { stem: '不等式 2x - 3 > 5 的解集是？', options: ['x > 4', 'x < 4', 'x > 1', 'x < 1'] },
            answer: { value: 'x > 4', steps: ['移项：2x > 8', 'x > 4'] },
          },
        ]);
      });
  }, []);

  // Start tutoring a question
  const startTutor = (question: Question, firstMsg?: string) => {
    setMode('tutor');
    const msg = firstMsg || '老师好，这道题我不会，请帮我讲解一下';
    askQuestion(question, msg);
  };

  // Submit answer in test mode
  const submitAnswer = async (question: Question, studentAnswer: string) => {
    if (!studentAnswer.trim() || !currentQuestion) return;

    addMessage('user', `我的答案：${studentAnswer}`);

    try {
      // Python returns snake_case; the aiRequest wrapper unwraps { success, data }
      // eslint-disable-next-line
      const raw: any = await tutorApi.grade({
        questionContent: question.content.stem,
        answer: question.answer.value,
        steps: (question.answer.steps || []).join('\n'),
        studentAnswer,
        subject: '数学',
        grade: '初中',
      });

      const result = raw?.data || raw;
      if (!result) throw new Error('No result');
      if (!result) throw new Error('No result');

      const parts: string[] = [];
      const score = result.overall_score ?? result.overallScore;
      const correct = result.is_correct ?? result.isCorrect;
      const stepGrades = result.step_grades ?? result.stepGrades ?? [];
      const errorAnalysis = result.error_analysis ?? result.errorAnalysis;
      const overallFeedback = result.overall_feedback ?? result.overallFeedback;

      if (score !== undefined) {
        parts.push(`📊 得分：${score}/100 ${correct ? '✅' : '❌'}`);
      }
      for (const sg of stepGrades) {
        const icon = (sg.is_correct ?? sg.isCorrect) ? '✅' : '❌';
        const name = sg.step_name ?? sg.stepName;
        const scoreVal = sg.score;
        const maxVal = sg.max_score ?? sg.maxScore;
        const fb = sg.feedback;
        parts.push(`${icon} ${name}: ${scoreVal}/${maxVal} — ${fb}`);
      }
      if (errorAnalysis) {
        const errType = errorAnalysis.primary_error_type ?? errorAnalysis.primaryErrorType;
        if (errType && errType !== '无' && errType !== 'None') {
          parts.push(`🔍 错误类型：${errType}`);
        }
        const gaps = errorAnalysis.knowledge_gaps ?? errorAnalysis.knowledgeGaps;
        if (gaps?.length) parts.push(`📝 知识漏洞：${gaps.join('、')}`);
        if (errorAnalysis.suggestion) parts.push(`💡 建议：${errorAnalysis.suggestion}`);
      }
      if (overallFeedback) parts.push(`\n${overallFeedback}`);

      addMessage('assistant', parts.join('\n'));
    } catch (err) {
      addMessage('assistant', `⚠️ 批改失败: ${err instanceof Error ? err.message : 'Unknown error'}`);
    }
  };

  // Send message (handles tutor continuation + test submission)
  const handleSend = () => {
    if (!input.trim() || isStreaming) return;
    const msg = input.trim();
    setInput('');

    if (!currentQuestion) {
      // Free-form question
      const freeQ: Question = {
        id: -Date.now(), kpId: 0, subject: 'math', grade: 'grade7',
        type: 'CALCULATION', difficulty: 3,
        content: { stem: msg },
        answer: { value: '', steps: [] },
      };
      startTutor(freeQ, '请帮我解答这个问题');
      return;
    }

    if (mode === 'test') {
      submitAnswer(currentQuestion, msg);
    } else {
      sendMessage(msg);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const clearChat = () => {
    useChatStore.getState().reset();
    setMode('tutor');
  };

  // ============ Render ============
  return (
    <div className="max-w-6xl mx-auto h-[calc(100vh-8rem)] flex flex-col">
      {/* Header */}
      <div className="flex items-center justify-between mb-3 shrink-0">
        <div>
          <h1 className="text-xl font-bold text-gray-900">AI家教</h1>
          <p className="text-xs text-gray-500">基于 DeepSeek 的苏格拉底式智能辅导</p>
        </div>
        <div className="flex gap-2">
          {messages.length > 0 && (
            <button onClick={clearChat}
              className="px-3 py-1.5 text-xs text-gray-600 bg-white border border-gray-200 rounded-lg hover:bg-gray-50">
              新对话
            </button>
          )}
        </div>
      </div>

      <div className="flex-1 flex gap-3 overflow-hidden">
        {/* Left: Question list */}
        <div className="w-72 shrink-0 overflow-y-auto space-y-2">
          <div className="text-xs font-semibold text-gray-400 mb-2 uppercase tracking-wider">
            题库 ({questions.length}题)
          </div>
          {questions.map((q, i) => (
            <div
              key={q.id || i}
              onClick={() => startTutor(q)}
              className={`p-3 rounded-lg border cursor-pointer transition-all text-sm ${
                currentQuestion?.content?.stem === q.content.stem
                  ? 'border-blue-500 bg-blue-50 ring-1 ring-blue-200'
                  : 'border-gray-200 bg-white hover:border-gray-300 hover:shadow-sm'
              }`}
            >
              <div className="flex items-center gap-1.5 mb-1">
                <span className="text-[10px] px-1.5 py-0.5 bg-blue-100 text-blue-700 rounded font-medium">
                  {q.subject === 'math' ? '数学' : q.subject}
                </span>
                <span className="text-[10px] px-1.5 py-0.5 bg-gray-100 text-gray-500 rounded">
                  {'★'.repeat(q.difficulty)}
                </span>
              </div>
              <p className="text-gray-800 line-clamp-3">{q.content.stem}</p>
              {q.content.options && (
                <div className="mt-1 text-[10px] text-gray-400">
                  {q.content.options.join(' | ')}
                </div>
              )}
            </div>
          ))}

          {/* Freeform input */}
          <div className="pt-2 border-t border-gray-100">
            <p className="text-xs text-gray-400 mb-1">或直接输入题目：</p>
            <textarea
              placeholder="粘贴或输入题目..."
              rows={2}
              className="w-full p-2 border border-gray-200 rounded-lg text-xs resize-none focus:ring-1 focus:ring-blue-500 outline-none"
              onKeyDown={(e) => {
                if (e.key === 'Enter' && !e.shiftKey) {
                  e.preventDefault();
                  const val = (e.target as HTMLTextAreaElement).value.trim();
                  if (val) {
                    const freeQ: Question = {
                      id: -Date.now(), kpId: 0, subject: 'math', grade: 'grade7',
                      type: 'CALCULATION', difficulty: 3,
                      content: { stem: val },
                      answer: { value: '', steps: [] },
                    };
                    (e.target as HTMLTextAreaElement).value = '';
                    startTutor(freeQ);
                  }
                }
              }}
            />
          </div>
        </div>

        {/* Right: Chat area */}
        <div className="flex-1 flex flex-col bg-white rounded-xl border border-gray-200 overflow-hidden">
          {/* Mode toggle */}
          {currentQuestion && (
            <div className="flex items-center gap-1 px-4 py-2 border-b border-gray-100 bg-gray-50">
              <button
                onClick={() => setMode('tutor')}
                className={`px-3 py-1 text-xs rounded-md transition-colors ${
                  mode === 'tutor' ? 'bg-blue-600 text-white' : 'text-gray-600 hover:bg-gray-200'
                }`}
              >
                🧑‍🏫 讲题模式
              </button>
              <button
                onClick={() => setMode('test')}
                className={`px-3 py-1 text-xs rounded-md transition-colors ${
                  mode === 'test' ? 'bg-green-600 text-white' : 'text-gray-600 hover:bg-gray-200'
                }`}
              >
                📝 测试模式
              </button>
              <span className="ml-auto text-[10px] text-gray-400 truncate max-w-[250px]">
                {currentQuestion.content.stem.slice(0, 50)}...
              </span>
            </div>
          )}

          {/* Messages */}
          <div className="flex-1 overflow-y-auto p-4 space-y-4">
            {messages.length === 0 ? (
              <div className="h-full flex items-center justify-center">
                <div className="text-center text-gray-400">
                  <FileText className="w-12 h-12 mx-auto mb-2 opacity-30" />
                  <p className="text-sm">选择左侧题目开始 AI 家教对话</p>
                  <p className="text-xs mt-1">AI家教会用提问引导你思考，不直接给答案</p>
                </div>
              </div>
            ) : (
              messages.map((msg) => (
                <div
                  key={msg.id}
                  className={`flex gap-3 ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}
                >
                  {msg.role === 'assistant' && (
                    <div className="w-7 h-7 bg-blue-600 text-white rounded-full flex items-center justify-center text-xs font-medium shrink-0">
                      AI
                    </div>
                  )}
                  <div
                    className={`max-w-[80%] rounded-2xl px-4 py-2.5 text-sm leading-relaxed whitespace-pre-wrap ${
                      msg.role === 'user'
                        ? 'bg-blue-600 text-white rounded-br-md'
                        : 'bg-gray-100 text-gray-900 rounded-bl-md'
                    } ${msg.role === 'assistant' && !msg.content ? 'animate-pulse' : ''}`}
                  >
                    {msg.content || (msg.role === 'assistant' ? '思考中...' : '')}
                  </div>
                  {msg.role === 'user' && (
                    <div className="w-7 h-7 bg-gray-300 text-gray-600 rounded-full flex items-center justify-center text-xs font-medium shrink-0">
                      U
                    </div>
                  )}
                </div>
              ))
            )}
            <div ref={chatEndRef} />
          </div>

          {/* Input */}
          <div className="border-t border-gray-200 p-3">
            <form
              onSubmit={(e) => { e.preventDefault(); handleSend(); }}
              className="flex gap-2"
            >
              <input
                type="text"
                value={input}
                onChange={(e) => setInput(e.target.value)}
                onKeyDown={handleKeyDown}
                placeholder={
                  mode === 'test'
                    ? '输入你的答案...'
                    : currentQuestion
                      ? '输入你的想法或问题...'
                      : '输入题目或问题开始...'
                }
                disabled={isStreaming}
                className="flex-1 px-4 py-2 border border-gray-200 rounded-lg text-sm focus:ring-2 focus:ring-blue-500 focus:border-transparent outline-none disabled:bg-gray-50"
              />
              <button
                type="submit"
                disabled={!input.trim() || isStreaming}
                className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors flex items-center gap-1"
              >
                {isStreaming ? (
                  <Loader2 className="w-4 h-4 animate-spin" />
                ) : (
                  <Send className="w-4 h-4" />
                )}
              </button>
            </form>
          </div>
        </div>
      </div>
    </div>
  );
}
