'use client';

import { useState, useRef, useEffect, useMemo } from 'react';
import type { Question, AbilityProfile, KnowledgePoint } from '@/types';
import { Send, Loader2, FileText } from 'lucide-react';
import { useChat } from '@/hooks/useChat';
import { useChatStore } from '@/stores/chatStore';
import { useAuthStore } from '@/stores/authStore';
import { questionApi, assessmentApi } from '@/lib/api';
import MathRenderer from '@/components/tutor/MathRenderer';

export default function TutorPage() {
  const [profiles, setProfiles] = useState<AbilityProfile[]>([]);
  const [kpNames, setKpNames] = useState<Record<number, string>>({});
  const [input, setInput] = useState('');
  const [teachingStyle, setTeachingStyle] = useState<'guided' | 'direct'>('guided');
  const teachingStyleRef = useRef<'guided' | 'direct'>('guided');

  const userId = useAuthStore((s) => s.user?.id);

  // Build mastery lookup: kpId → mastery level (Chinese label)
  const masteryMap = useMemo(() => {
    const map: Record<number, string> = {};
    for (const p of profiles) {
      if (p.masteryProbability < 0.30) map[p.kpId] = '弱';
      else if (p.masteryProbability < 0.60) map[p.kpId] = '中';
      else if (p.masteryProbability < 0.85) map[p.kpId] = '强';
      else map[p.kpId] = '精通';
    }
    return map;
  }, [profiles]);

  const { messages, isStreaming, currentQuestion, askQuestion, sendMessage } = useChat({
    getMastery: (kpId) => masteryMap[kpId] || '中',
    getKnowledgePoint: (kpId) => kpNames[kpId] || '',
    teachingStyle,
    getTeachingStyle: () => teachingStyleRef.current,
  });
  const addMessage = useChatStore((s) => s.addMessage);

  const chatEndRef = useRef<HTMLDivElement>(null);

  // Auto-scroll
  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  // Load profiles + knowledge tree on mount
  useEffect(() => {
    if (userId) {
      assessmentApi.getProfiles(userId)
        .then((data) => setProfiles(data || []))
        .catch((err: unknown) => { console.log('Profiles not loaded:', err); });
    }

    questionApi.getKnowledgeTree()
      .then((tree) => {
        const names: Record<number, string> = {};
        const flatten = (nodes: KnowledgePoint[]) => {
          for (const n of nodes) {
            names[n.id] = n.name;
            if ((n as unknown as { children?: KnowledgePoint[] }).children) {
              flatten((n as unknown as { children: KnowledgePoint[] }).children);
            }
          }
        };
        flatten(tree || []);
        setKpNames(names);
      })
      .catch((err: unknown) => { console.log('KP names not loaded:', err); });
  }, [userId]);

  // Start tutoring a question
  const startTutor = (question: Question, firstMsg?: string) => {
    const msg = firstMsg || '老师好，这道题我不会，请帮我讲解一下';
    askQuestion(question, msg);
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

    sendMessage(msg);
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const clearChat = () => {
    useChatStore.getState().reset();
  };

  const changeTeachingStyle = (style: 'guided' | 'direct') => {
    teachingStyleRef.current = style;
    setTeachingStyle(style);
  };

  // ============ Render ============
  return (
    <div className="max-w-6xl mx-auto h-[calc(100vh-8rem)] flex flex-col">
      {/* Header */}
      <div className="flex items-center justify-between mb-3 shrink-0">
        <div>
          <h1 className="text-xl font-bold text-gray-900">AI家教</h1>
          <p className="text-xs text-gray-500">
            {teachingStyle === 'guided' ? '引导式讲题：先启发思考，再推进解法' : '快速答案式：直接给出答案、步骤和总结'}
          </p>
        </div>
        <div className="flex gap-2">
          <div className="flex items-center gap-1 rounded-lg bg-gray-100 p-1">
            <button
              onClick={() => changeTeachingStyle('guided')}
              className={`px-3 py-1 text-xs rounded-md transition-colors ${
                teachingStyle === 'guided'
                  ? 'bg-white text-blue-700 shadow-sm font-medium'
                  : 'text-gray-500 hover:text-gray-700'
              }`}
            >
              引导式
            </button>
            <button
              onClick={() => changeTeachingStyle('direct')}
              className={`px-3 py-1 text-xs rounded-md transition-colors ${
                teachingStyle === 'direct'
                  ? 'bg-white text-green-700 shadow-sm font-medium'
                  : 'text-gray-500 hover:text-gray-700'
              }`}
            >
              快速答案
            </button>
          </div>
          {messages.length > 0 && (
            <button onClick={clearChat}
              className="px-3 py-1.5 text-xs text-gray-600 bg-white border border-gray-200 rounded-lg hover:bg-gray-50">
              新对话
            </button>
          )}
        </div>
      </div>

      <div className="flex-1 overflow-hidden">
        <div className="h-full flex flex-col bg-white rounded-xl border border-gray-200 overflow-hidden">
          {/* Current question */}
          {currentQuestion && (
            <div className="flex items-center gap-1 px-4 py-2 border-b border-gray-100 bg-gray-50">
              <span className="px-3 py-1 text-xs rounded-md bg-blue-600 text-white">讲题对话</span>
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
                  <p className="text-sm">在下方输入题目、卡点或追问开始讲题</p>
                  <p className="text-xs mt-1">
                    {teachingStyle === 'guided'
                      ? '当前为引导式：AI 会用提问引导你思考'
                      : '当前为快速答案式：AI 会直接给出答案和步骤'}
                  </p>
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
                    {msg.content ? <MathRenderer content={msg.content} /> : (msg.role === 'assistant' ? '思考中...' : '')}
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
                  currentQuestion
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
