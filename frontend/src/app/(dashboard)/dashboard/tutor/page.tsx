'use client';

import { useState, useRef, useEffect, useCallback } from 'react';
import type { Question, ChatMessage } from '@/types';
import { Send, Loader2, FileText, CheckCircle, XCircle } from 'lucide-react';

// ============================================
// API configuration
// ============================================
const AI_BASE = 'http://localhost:8000'; // Direct to Python (bypasses Next.js proxy POST issue)

function getAuthHeaders(): Record<string, string> {
  const token = typeof window !== 'undefined' ? localStorage.getItem('token') : null;
  return token
    ? { 'Content-Type': 'application/json;charset=UTF-8', Authorization: token }
    : { 'Content-Type': 'application/json;charset=UTF-8' };
}

async function apiGet<T>(path: string): Promise<T> {
  const res = await fetch(`${path}`, { headers: getAuthHeaders() });
  if (!res.ok) throw new Error(`API ${res.status}`);
  const data = await res.json();
  if (data.code !== 200) throw new Error(data.message || 'Unknown error');
  return data.data as T;
}

// ============================================
// Main Component
// ============================================
export default function TutorPage() {
  const [questions, setQuestions] = useState<Question[]>([]);
  const [currentQuestion, setCurrentQuestion] = useState<Question | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [isStreaming, setIsStreaming] = useState(false);
  const [mode, setMode] = useState<'tutor' | 'test'>('tutor');
  const [studentAnswers, setStudentAnswers] = useState<Record<number, string>>({});

  const chatEndRef = useRef<HTMLDivElement>(null);
  const abortRef = useRef<AbortController | null>(null);

  // Auto-scroll
  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  // Load questions on mount
  useEffect(() => {
    loadQuestions();
  }, []);

  const loadQuestions = async () => {
    try {
      const data = await apiGet<{ items: Question[] }>(
        '/api/questions?page=1&size=20',
      );
      if (data?.items?.length) {
        setQuestions(data.items);
      }
    } catch (err) {
      console.log('Failed to load questions, using demo data:', err);
      // Fallback demo questions
      setQuestions([
        {
          id: 0, kpId: 6, subject: 'math', grade: 'grade7',
          type: 'CALCULATION', difficulty: 2,
          content: { stem: '解方程：2x + 5 = 13' },
          answer: { value: 'x = 4', steps: ['移项：2x = 8', 'x = 4'] },
        },
        {
          id: 0, kpId: 7, subject: 'math', grade: 'grade7',
          type: 'CHOICE', difficulty: 2,
          content: { stem: '不等式 2x - 3 > 5 的解集是？', options: ['x > 4', 'x < 4', 'x > 1', 'x < 1'] },
          answer: { value: 'x > 4', steps: ['移项：2x > 8', 'x > 4'] },
        },
      ]);
    }
  };

  // ============ Tutor Mode: SSE streaming ============
  const startTutor = useCallback(async (question: Question, firstMsg?: string) => {
    if (isStreaming) return;

    const userMsg = firstMsg || '老师好，这道题我不会，请帮我讲解一下';
    setCurrentQuestion(question);
    setMode('tutor');

    const newMsg: ChatMessage = {
      id: Date.now().toString(),
      role: 'user',
      content: userMsg,
      timestamp: new Date().toISOString(),
    };
    const aiMsg: ChatMessage = {
      id: (Date.now() + 1).toString(),
      role: 'assistant',
      content: '',
      timestamp: new Date().toISOString(),
    };

    setMessages((prev) => [...prev, newMsg, aiMsg]);
    setIsStreaming(true);

    const controller = new AbortController();
    abortRef.current = controller;

    try {
      const token = localStorage.getItem('token') || '';
      const res = await fetch(`${AI_BASE}/ai/tutor/explain`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json;charset=UTF-8',
          ...(token ? { Authorization: token } : {}),
        },
        body: JSON.stringify({
          question_content: question.content.stem,
          answer: question.answer.value,
          steps: (question.answer.steps || []).join('\n'),
          student_message: userMsg,
          grade: question.grade === 'grade7' ? '初一' : '初中',
          subject: question.subject === 'math' ? '数学' : question.subject,
          knowledge_point: '',
          mastery_level: '中',
        }),
        signal: controller.signal,
      });

      if (!res.ok) throw new Error(`HTTP ${res.status}`);

      const reader = res.body?.getReader();
      if (!reader) throw new Error('No response stream');

      const decoder = new TextDecoder();
      let buffer = '';
      let fullContent = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop() || '';

        for (const line of lines) {
          if (line.startsWith('data: ')) {
            try {
              const parsed = JSON.parse(line.slice(6));
              if (parsed.done) {
                // Streaming complete
                break;
              }
              if (parsed.error) {
                fullContent += `\n\n[错误] ${parsed.error}`;
                break;
              }
              if (parsed.content) {
                fullContent += parsed.content;
                setMessages((prev) => {
                  const updated = [...prev];
                  const last = updated[updated.length - 1];
                  if (last?.role === 'assistant') {
                    updated[updated.length - 1] = {
                      ...last,
                      content: fullContent,
                    };
                  }
                  return updated;
                });
              }
            } catch {
              // Non-JSON line, skip
            }
          }
        }
      }
    } catch (err: unknown) {
      if (err instanceof DOMException && err.name === 'AbortError') return;
      const errorMsg = err instanceof Error ? err.message : 'Unknown error';
      setMessages((prev) => {
        const updated = [...prev];
        const last = updated[updated.length - 1];
        if (last?.role === 'assistant') {
          updated[updated.length - 1] = {
            ...last,
            content: last.content + `\n\n⚠️ 连接中断: ${errorMsg}`,
          };
        }
        return updated;
      });
    } finally {
      setIsStreaming(false);
    }
  }, [isStreaming]);

  // ============ Test Mode: grading ============
  const submitAnswer = async (question: Question, studentAnswer: string) => {
    if (!studentAnswer.trim()) return;

    try {
      const res = await fetch(`${AI_BASE}/ai/tutor/grade`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json;charset=UTF-8' },
        body: JSON.stringify({
          question_content: question.content.stem,
          answer: question.answer.value,
          steps: (question.answer.steps || []).join('\n'),
          student_answer: studentAnswer,
          subject: '数学',
          grade: '初中',
        }),
      });

      const data = await res.json();
      const result = data.data || data;

      const feedbackParts: string[] = [];
      if (result.overall_score !== undefined) {
        feedbackParts.push(`📊 得分：${result.overall_score}/100 ${result.is_correct ? '✅' : '❌'}`);
      }
      if (result.step_grades) {
        for (const sg of result.step_grades) {
          const icon = sg.is_correct ? '✅' : '❌';
          feedbackParts.push(`${icon} ${sg.step_name}: ${sg.score}/${sg.max_score} — ${sg.feedback}`);
        }
      }
      if (result.error_analysis) {
        const ea = result.error_analysis;
        if (ea.primary_error_type && ea.primary_error_type !== '无' && ea.primary_error_type !== 'None') {
          feedbackParts.push(`🔍 错误类型：${ea.primary_error_type}`);
        }
        if (ea.knowledge_gaps?.length) {
          feedbackParts.push(`📝 知识漏洞：${ea.knowledge_gaps.join('、')}`);
        }
        if (ea.suggestion) {
          feedbackParts.push(`💡 建议：${ea.suggestion}`);
        }
      }
      if (result.overall_feedback) {
        feedbackParts.push(`\n${result.overall_feedback}`);
      }

      const feedbackMsg: ChatMessage = {
        id: Date.now().toString(),
        role: 'assistant',
        content: feedbackParts.join('\n'),
        timestamp: new Date().toISOString(),
      };

      setMessages((prev) => [...prev, feedbackMsg]);
    } catch (err) {
      console.error('Grade error:', err);
    }
  };

  // ============ Send message in active chat ============
  const handleSend = () => {
    if (!input.trim() || isStreaming) return;

    if (!currentQuestion) {
      // No question selected, treat input as a free-form question
      const freeQuestion: Question = {
        id: 0, kpId: 0, subject: 'math', grade: 'grade7',
        type: 'CALCULATION', difficulty: 3,
        content: { stem: input.trim() },
        answer: { value: '', steps: [] },
      };
      setInput('');
      startTutor(freeQuestion, `请帮我解答这个问题`);
      return;
    }

    const msg = input.trim();
    setInput('');

    if (mode === 'test') {
      // In test mode, this is an answer submission
      const answerMsg: ChatMessage = {
        id: Date.now().toString(),
        role: 'user',
        content: `我的答案：${msg}`,
        timestamp: new Date().toISOString(),
      };
      setMessages((prev) => [...prev, answerMsg]);
      submitAnswer(currentQuestion, msg);
    } else {
      // In tutor mode, continue the conversation
      const userMsg: ChatMessage = {
        id: Date.now().toString(),
        role: 'user',
        content: msg,
        timestamp: new Date().toISOString(),
      };
      const aiMsg: ChatMessage = {
        id: (Date.now() + 1).toString(),
        role: 'assistant',
        content: '',
        timestamp: new Date().toISOString(),
      };
      setMessages((prev) => [...prev, userMsg, aiMsg]);
      setIsStreaming(true);

      // Continue conversation with SSE
      const token = localStorage.getItem('token') || '';
      fetch(`${AI_BASE}/ai/tutor/explain`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json;charset=UTF-8',
          ...(token ? { Authorization: token } : {}),
        },
        body: JSON.stringify({
          question_content: currentQuestion.content.stem,
          answer: currentQuestion.answer.value,
          steps: (currentQuestion.answer.steps || []).join('\n'),
          student_message: msg,
          history: messages
            .filter((m) => m.content.length > 0)
            .map((m) => ({ role: m.role, content: m.content })),
          grade: '初中',
          subject: '数学',
          knowledge_point: '',
          mastery_level: '中',
        }),
        signal: abortRef.current?.signal,
      })
        .then(async (res) => {
          if (!res.ok) throw new Error(`HTTP ${res.status}`);
          const reader = res.body?.getReader();
          if (!reader) throw new Error('No stream');
          const decoder = new TextDecoder();
          let buffer = '';
          let full = '';

          while (true) {
            const { done, value } = await reader.read();
            if (done) break;
            buffer += decoder.decode(value, { stream: true });
            const lines = buffer.split('\n');
            buffer = lines.pop() || '';
            for (const line of lines) {
              if (line.startsWith('data: ')) {
                try {
                  const p = JSON.parse(line.slice(6));
                  if (p.done) break;
                  if (p.content) {
                    full += p.content;
                    setMessages((prev) => {
                      const u = [...prev];
                      const l = u[u.length - 1];
                      if (l?.role === 'assistant') u[u.length - 1] = { ...l, content: full };
                      return u;
                    });
                  }
                } catch { /* skip */ }
              }
            }
          }
        })
        .catch((err) => {
          if (err instanceof DOMException && err.name === 'AbortError') return;
          setMessages((prev) => {
            const u = [...prev];
            const l = u[u.length - 1];
            if (l?.role === 'assistant') u[u.length - 1] = { ...l, content: l.content + `\n\n⚠️ ${err}` };
            return u;
          });
        })
        .finally(() => setIsStreaming(false));
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const clearChat = () => {
    abortRef.current?.abort();
    setMessages([]);
    setCurrentQuestion(null);
    setIsStreaming(false);
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
              onClick={() => {
                setCurrentQuestion(q);
                setMode('tutor');
                startTutor(q);
              }}
              className={`p-3 rounded-lg border cursor-pointer transition-all text-sm ${
                currentQuestion?.id === q.id && currentQuestion?.content?.stem === q.content.stem
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
            <p className="text-xs text-gray-400 mb-1">
              或直接输入题目：
            </p>
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
                    setCurrentQuestion(freeQ);
                    setMode('tutor');
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
                  mode === 'tutor'
                    ? 'bg-blue-600 text-white'
                    : 'text-gray-600 hover:bg-gray-200'
                }`}
              >
                🧑‍🏫 讲题模式
              </button>
              <button
                onClick={() => setMode('test')}
                className={`px-3 py-1 text-xs rounded-md transition-colors ${
                  mode === 'test'
                    ? 'bg-green-600 text-white'
                    : 'text-gray-600 hover:bg-gray-200'
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
              onSubmit={(e) => {
                e.preventDefault();
                handleSend();
              }}
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
