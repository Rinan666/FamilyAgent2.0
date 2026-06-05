'use client';

import { useState, useRef, useEffect, useMemo, useCallback } from 'react';
import type { ChatMessage, ChatSession, Question, AbilityProfile, KnowledgePoint } from '@/types';
import { History, Loader2, MessageSquareText, Plus, Send, FileText, Trash2 } from 'lucide-react';
import { useChat } from '@/hooks/useChat';
import { useChatStore } from '@/stores/chatStore';
import { useAuthStore } from '@/stores/authStore';
import { questionApi, assessmentApi, sessionApi } from '@/lib/api';
import MathRenderer from '@/components/tutor/MathRenderer';

function formatSessionTime(value?: string) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  return date.toLocaleString('zh-CN', {
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

function getSessionTitle(session: ChatSession) {
  const firstUserMessage = session.messages.find((msg) => msg.role === 'user')?.content;
  const metadata = session.metadata || {};
  const questionContent = metadata.questionContent as { stem?: string } | string | undefined;
  const questionStem = typeof questionContent === 'string' ? questionContent : questionContent?.stem;
  return (questionStem || firstUserMessage || session.summary || '未命名讲题').slice(0, 36);
}

function getSessionModeLabel(session: ChatSession) {
  return session.metadata?.mode === 'explain' ? '讲题辅导' : '自由对话';
}

function buildQuestionFromSession(session: ChatSession): Question | null {
  const metadata = session.metadata || {};
  if (metadata.mode === 'chat') return null;

  const questionContent = metadata.questionContent as { stem?: string; options?: string[]; figures?: string[] } | string | undefined;
  const answer = metadata.answer as { value?: string; steps?: string[]; explanation?: string } | undefined;
  const stem = typeof questionContent === 'string'
    ? questionContent
    : questionContent?.stem || session.messages.find((msg) => msg.role === 'user')?.content;

  if (!stem) return null;

  return {
    id: session.questionId || -session.id,
    kpId: session.knowledgePointId || 0,
    subject: session.subject || 'math',
    grade: typeof metadata.grade === 'string' ? metadata.grade : 'grade7',
    type: 'CALCULATION',
    difficulty: 3,
    content: {
      stem,
      options: typeof questionContent === 'object' ? questionContent.options : undefined,
      figures: typeof questionContent === 'object' ? questionContent.figures : undefined,
    },
    answer: {
      value: answer?.value || '',
      steps: Array.isArray(answer?.steps) ? answer.steps : [],
      explanation: answer?.explanation,
    },
  };
}

export default function TutorPage() {
  const [profiles, setProfiles] = useState<AbilityProfile[]>([]);
  const [kpNames, setKpNames] = useState<Record<number, string>>({});
  const [sessions, setSessions] = useState<ChatSession[]>([]);
  const [isLoadingSessions, setIsLoadingSessions] = useState(false);
  const [sessionError, setSessionError] = useState('');
  const [input, setInput] = useState('');
  const [isComposingQuestion, setIsComposingQuestion] = useState(false);
  const [teachingStyle, setTeachingStyle] = useState<'guided' | 'direct'>('guided');
  const teachingStyleRef = useRef<'guided' | 'direct'>('guided');

  const userId = useAuthStore((s) => s.user?.id);
  const sessionId = useChatStore((s) => s.sessionId);
  const setSessionId = useChatStore((s) => s.setSessionId);
  const setMessages = useChatStore((s) => s.setMessages);
  const setCurrentQuestion = useChatStore((s) => s.setCurrentQuestion);

  const loadSessions = useCallback(() => {
    if (!userId) return;
    setIsLoadingSessions(true);
    setSessionError('');
    sessionApi.getUserSessions(userId, 20)
      .then((data) => setSessions(data || []))
      .catch((err: unknown) => {
        console.log('Sessions not loaded:', err);
        setSessionError('历史会话加载失败');
      })
      .finally(() => setIsLoadingSessions(false));
  }, [userId]);

  const upsertSession = useCallback((session: ChatSession) => {
    setSessions((prev) => {
      const next = [session, ...prev.filter((item) => item.id !== session.id)];
      return next.sort((a, b) => {
        const left = new Date(a.startedAt || 0).getTime();
        const right = new Date(b.startedAt || 0).getTime();
        return right - left;
      });
    });
  }, []);

  const persistSessionMessages = useCallback(async (nextMessages: ChatMessage[], question: Question) => {
    if (nextMessages.length === 0) return;
    const currentSessionId = useChatStore.getState().sessionId;

    const metadata = {
      mode: 'explain',
      questionContent: question.content,
      answer: question.answer,
      grade: question.grade,
      teachingStyle: teachingStyleRef.current,
    };

    const saved = currentSessionId
      ? await sessionApi.updateMessages(currentSessionId, nextMessages)
      : await sessionApi.createSession({
          questionId: question.id > 0 ? question.id : undefined,
          subject: question.subject,
          knowledgePointId: question.kpId || undefined,
          messages: nextMessages,
          visibility: 'PRIVATE',
          source: 'TUTOR',
          metadata,
        });

    setSessionId(saved.id);
    upsertSession({ ...saved, metadata: saved.metadata || metadata });
  }, [setSessionId, upsertSession]);

  const persistFreeChatMessages = useCallback(async (nextMessages: ChatMessage[]) => {
    if (nextMessages.length === 0) return;
    const currentSessionId = useChatStore.getState().sessionId;
    const metadata = {
      mode: 'chat',
      source: 'free_chat',
      teachingStyle: teachingStyleRef.current,
    };

    const saved = currentSessionId
      ? await sessionApi.updateMessages(currentSessionId, nextMessages)
      : await sessionApi.createSession({
          subject: '数学',
          messages: nextMessages,
          visibility: 'PRIVATE',
          source: 'TUTOR_CHAT',
          metadata,
        });

    setSessionId(saved.id);
    setCurrentQuestion(null);
    upsertSession({ ...saved, metadata: saved.metadata || metadata });
  }, [setSessionId, setCurrentQuestion, upsertSession]);

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

  const { messages, isStreaming, currentQuestion, askQuestion, sendMessage, sendFreeMessage } = useChat({
    getMastery: (kpId) => masteryMap[kpId] || '中',
    getKnowledgePoint: (kpId) => kpNames[kpId] || '',
    teachingStyle,
    getTeachingStyle: () => teachingStyleRef.current,
    persistMessages: persistSessionMessages,
    persistChatMessages: persistFreeChatMessages,
  });

  const chatEndRef = useRef<HTMLDivElement>(null);

  // Auto-scroll
  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  useEffect(() => {
    loadSessions();
  }, [loadSessions]);

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

  // Send message (handles tutor continuation + test submission)
  const handleSend = () => {
    if (!input.trim() || isStreaming) return;
    const msg = input.trim();
    setInput('');

    if (isComposingQuestion && !currentQuestion) {
      const question: Question = {
        id: -Date.now(),
        kpId: 0,
        subject: '数学',
        grade: '',
        type: 'CALCULATION',
        difficulty: 3,
        content: { stem: msg },
        answer: { value: '', steps: [] },
      };
      setIsComposingQuestion(false);
      askQuestion(question, msg);
      return;
    }

    if (!currentQuestion) {
      sendFreeMessage(msg);
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
    setInput('');
    setIsComposingQuestion(false);
  };

  const restoreSession = async (session: ChatSession) => {
    if (isStreaming) return;
    const detail = await sessionApi.getSession(session.id);
    setSessionId(detail.id);
    setMessages(detail.messages || []);
    const restoredQuestion = buildQuestionFromSession(detail);
    setCurrentQuestion(restoredQuestion);
    setIsComposingQuestion(false);
    upsertSession(detail);
  };

  const enterTutorMode = () => {
    setIsComposingQuestion(true);
    setCurrentQuestion(null);
  };

  const exitTutorMode = () => {
    setIsComposingQuestion(false);
    setCurrentQuestion(null);
  };

  const deleteSession = async (session: ChatSession, event: React.MouseEvent) => {
    event.stopPropagation();
    if (isStreaming) return;
    await sessionApi.deleteSession(session.id);
    setSessions((prev) => prev.filter((item) => item.id !== session.id));
    if (sessionId === session.id) {
      clearChat();
    }
  };

  const changeTeachingStyle = (style: 'guided' | 'direct') => {
    teachingStyleRef.current = style;
    setTeachingStyle(style);
  };

  const isTutorMode = Boolean(currentQuestion) || isComposingQuestion;

  // ============ Render ============
  return (
    <div className="max-w-7xl mx-auto h-[calc(100vh-8rem)] flex flex-col">
      {/* Header */}
      <div className="flex items-center justify-between mb-3 shrink-0">
        <div>
          <h1 className="text-xl font-bold text-gray-900">AI家教</h1>
          <p className="text-xs text-gray-500">
            {isTutorMode
              ? (teachingStyle === 'guided' ? '讲题辅导：先启发思考，再推进解法' : '讲题辅导：直接给出答案、步骤和总结')
              : '自由对话：可以聊学习问题、计划、情绪、日记和家族知识'}
          </p>
        </div>
        <div className="flex gap-2">
          {isTutorMode ? (
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
          ) : (
            <button
              type="button"
              onClick={enterTutorMode}
              disabled={isStreaming}
              className="px-3 py-1.5 text-xs rounded-lg bg-blue-50 text-blue-700 hover:bg-blue-100 disabled:opacity-50"
            >
              进入讲题辅导
            </button>
          )}
          {isTutorMode && !currentQuestion && (
            <button
              type="button"
              onClick={exitTutorMode}
              disabled={isStreaming}
              className="px-3 py-1.5 text-xs text-gray-500 bg-white border border-gray-200 rounded-lg hover:bg-gray-50 disabled:opacity-50"
            >
              自由对话
            </button>
          )}
          {messages.length > 0 && (
            <button onClick={clearChat}
              className="px-3 py-1.5 text-xs text-gray-600 bg-white border border-gray-200 rounded-lg hover:bg-gray-50">
              新对话
            </button>
          )}
        </div>
      </div>

      <div className="flex-1 overflow-hidden flex gap-3">
        <aside className="hidden md:flex w-72 shrink-0 flex-col bg-white rounded-xl border border-gray-200 overflow-hidden">
          <div className="flex items-center justify-between px-3 py-2 border-b border-gray-100">
            <div className="flex items-center gap-2 text-sm font-medium text-gray-800">
              <History className="w-4 h-4" />
              最近会话
            </div>
            <button
              type="button"
              onClick={clearChat}
              disabled={isStreaming}
              className="w-7 h-7 rounded-md border border-gray-200 text-gray-500 hover:bg-gray-50 disabled:opacity-50 flex items-center justify-center"
              title="新对话"
            >
              <Plus className="w-4 h-4" />
            </button>
          </div>

          <div className="flex-1 overflow-y-auto p-2">
            {isLoadingSessions ? (
              <div className="h-24 flex items-center justify-center text-gray-400">
                <Loader2 className="w-4 h-4 animate-spin" />
              </div>
            ) : sessionError ? (
              <div className="px-3 py-4 text-xs text-red-500">{sessionError}</div>
            ) : sessions.length === 0 ? (
              <div className="h-full flex items-center justify-center text-center text-gray-400">
                <div>
                  <MessageSquareText className="w-8 h-8 mx-auto mb-2 opacity-30" />
                  <p className="text-xs">暂无历史会话</p>
                </div>
              </div>
            ) : (
              <div className="space-y-1">
                {sessions.map((session) => (
                  <button
                    type="button"
                    key={session.id}
                    onClick={() => { void restoreSession(session); }}
                    disabled={isStreaming}
                    className={`group w-full text-left px-3 py-2 rounded-lg transition-colors disabled:opacity-60 ${
                      sessionId === session.id
                        ? 'bg-blue-50 text-blue-700'
                        : 'text-gray-700 hover:bg-gray-50'
                    }`}
                  >
                    <div className="flex items-center gap-2">
                      <div className="min-w-0 flex-1 text-xs font-medium truncate">{getSessionTitle(session)}</div>
                      <span
                        role="button"
                        tabIndex={0}
                        onClick={(event) => { void deleteSession(session, event); }}
                        onKeyDown={(event) => {
                          if (event.key === 'Enter' || event.key === ' ') {
                            void deleteSession(session, event as unknown as React.MouseEvent);
                          }
                        }}
                        className="w-6 h-6 rounded-md text-gray-400 hover:text-red-600 hover:bg-red-50 opacity-0 group-hover:opacity-100 flex items-center justify-center"
                        title="删除会话"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </span>
                    </div>
                    <div className="mt-1 flex items-center justify-between gap-2 text-[10px] text-gray-400">
                      <span>{formatSessionTime(session.startedAt)}</span>
                      <span>{getSessionModeLabel(session)}</span>
                    </div>
                  </button>
                ))}
              </div>
            )}
          </div>
        </aside>

        <div className="h-full flex-1 flex flex-col bg-white rounded-xl border border-gray-200 overflow-hidden">
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
                  <p className="text-sm">
                    {currentQuestion
                      ? '围绕这道题继续提问或说出你的思路'
                      : isComposingQuestion
                        ? '输入一道题目，AI 会进入讲题辅导'
                        : '直接输入想聊的学习问题、计划或困惑'}
                  </p>
                  <p className="text-xs mt-1">
                    {isTutorMode
                      ? (teachingStyle === 'guided'
                        ? '当前为引导式：AI 会用提问引导你思考'
                        : '当前为快速答案式：AI 会直接给出答案和步骤')
                      : '如果你发的是题目，AI 会提示你可以进入讲题模式'}
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
                    : isComposingQuestion
                      ? '输入题目内容，开始讲题辅导...'
                      : '随便聊聊：学习计划、卡点、情绪、日记或家族知识...'
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
