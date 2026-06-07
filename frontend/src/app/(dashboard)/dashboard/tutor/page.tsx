'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { AbilityProfile, ChatMessage, ChatSession, KnowledgePoint, MemoryEntry, Question } from '@/types';
import { Brain, FileText, History, Loader2, MessageSquareText, Paperclip, Plus, Send, Trash2 } from 'lucide-react';
import MathRenderer from '@/components/tutor/MathRenderer';
import { useAuthStore } from '@/stores/authStore';
import { useChatStore } from '@/stores/chatStore';
import { useChat } from '@/hooks/useChat';
import { useViewerRole } from '@/hooks/useViewerRole';
import { assessmentApi, memoryApi, questionApi, sessionApi, tutorApi } from '@/lib/api';

const SESSION_IDLE_LIMIT_MS = 30 * 60 * 1000;

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
  return (questionStem || firstUserMessage || session.summary || '未命名会话').slice(0, 36);
}

function getSessionModeLabel(session: ChatSession) {
  return session.metadata?.mode === 'explain' ? '讲题' : '对话';
}

function getSessionStatusLabel(session: ChatSession) {
  return session.status === 'ACTIVE' ? '进行中' : '已结束';
}

function memoryTypeLabel(type?: string) {
  const labels: Record<string, string> = {
    knowledge_gap: '知识薄弱点',
    misconception: '易错概念',
    learning_preference: '学习偏好',
    study_habit: '学习习惯',
    emotional_state: '学习状态',
    goal: '学习目标',
    summary: '学习总结',
    strength: '优势能力',
    weakness: '薄弱能力',
  };
  if (!type) return '学习记录';
  return labels[type] || labels[type.toLowerCase()] || '学习记录';
}

function getSessionLastActivity(session: ChatSession) {
  const messageTimes = session.messages
    .map((message) => new Date(message.timestamp).getTime())
    .filter((time) => Number.isFinite(time));
  if (messageTimes.length > 0) return Math.max(...messageTimes);
  const fallback = new Date(session.endedAt || session.startedAt || 0).getTime();
  return Number.isFinite(fallback) ? fallback : 0;
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
  const [memories, setMemories] = useState<MemoryEntry[]>([]);
  const [isLoadingSessions, setIsLoadingSessions] = useState(false);
  const [isLoadingMemories, setIsLoadingMemories] = useState(false);
  const [sessionError, setSessionError] = useState('');
  const [input, setInput] = useState('');
  const [isComposingQuestion, setIsComposingQuestion] = useState(false);
  const [isExtractingFile, setIsExtractingFile] = useState(false);
  const [extractMessage, setExtractMessage] = useState('');
  const [teachingStyle, setTeachingStyle] = useState<'guided' | 'direct'>('guided');
  const { viewerRole, activeFamilyId } = useViewerRole();

  const idleEndInFlightRef = useRef<Set<number>>(new Set());
  const teachingStyleRef = useRef<'guided' | 'direct'>('guided');
  const fileInputRef = useRef<HTMLInputElement>(null);
  const chatEndRef = useRef<HTMLDivElement>(null);

  const userId = useAuthStore((s) => s.user?.id);
  const sessionId = useChatStore((s) => s.sessionId);
  const setSessionId = useChatStore((s) => s.setSessionId);
  const setMessages = useChatStore((s) => s.setMessages);
  const setCurrentQuestion = useChatStore((s) => s.setCurrentQuestion);

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

  const loadMemories = useCallback(() => {
    if (!userId) return;
    setIsLoadingMemories(true);
    memoryApi.listMyMemories(20)
      .then((data) => setMemories(data || []))
      .catch((err: unknown) => {
        console.log('Learning memories not loaded:', err);
      })
      .finally(() => setIsLoadingMemories(false));
  }, [userId]);

  const endCurrentSession = useCallback(async (options: { resetChat?: boolean } = {}) => {
    const currentSessionId = useChatStore.getState().sessionId;
    if (!currentSessionId) {
      if (options.resetChat) {
        useChatStore.getState().reset();
        setInput('');
        setIsComposingQuestion(false);
      }
      return;
    }

    try {
      const ended = await sessionApi.endSession(currentSessionId);
      upsertSession(ended);
      loadMemories();
    } catch (error) {
      console.log('Session not ended:', error);
    } finally {
      if (options.resetChat) {
        useChatStore.getState().reset();
        setInput('');
        setIsComposingQuestion(false);
      }
    }
  }, [loadMemories, upsertSession]);

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
          subject: 'math',
          messages: nextMessages,
          visibility: 'PRIVATE',
          source: 'TUTOR_CHAT',
          metadata,
        });

    setSessionId(saved.id);
    setCurrentQuestion(null);
    upsertSession({ ...saved, metadata: saved.metadata || metadata });
  }, [setSessionId, setCurrentQuestion, upsertSession]);

  const masteryMap = useMemo(() => {
    const map: Record<number, string> = {};
    for (const profile of profiles) {
      if (profile.masteryProbability < 0.30) map[profile.kpId] = 'weak';
      else if (profile.masteryProbability < 0.60) map[profile.kpId] = 'medium';
      else if (profile.masteryProbability < 0.85) map[profile.kpId] = 'strong';
      else map[profile.kpId] = 'excellent';
    }
    return map;
  }, [profiles]);

  const { messages, isStreaming, currentQuestion, askQuestion, sendMessage, sendFreeMessage } = useChat({
    getMastery: (kpId) => masteryMap[kpId] || 'medium',
    getKnowledgePoint: (kpId) => kpNames[kpId] || '',
    teachingStyle,
    getTeachingStyle: () => teachingStyleRef.current,
    viewerRole,
    targetRole: 'STUDENT',
    activeFamilyId,
    persistMessages: persistSessionMessages,
    persistChatMessages: persistFreeChatMessages,
  });

  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  useEffect(() => {
    loadSessions();
    loadMemories();
  }, [loadSessions, loadMemories]);

  useEffect(() => {
    const now = Date.now();
    for (const session of sessions) {
      if (session.status !== 'ACTIVE') continue;
      if (idleEndInFlightRef.current.has(session.id)) continue;
      if (now - getSessionLastActivity(session) < SESSION_IDLE_LIMIT_MS) continue;

      idleEndInFlightRef.current.add(session.id);
      void sessionApi.endSession(session.id)
        .then((ended) => {
          upsertSession(ended);
          loadMemories();
        })
        .catch((error: unknown) => {
          console.log('Idle session not ended:', error);
        })
        .finally(() => {
          idleEndInFlightRef.current.delete(session.id);
        });
    }
  }, [sessions, loadMemories, upsertSession]);

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
          for (const node of nodes) {
            names[node.id] = node.name;
            if (node.children) flatten(node.children);
          }
        };
        flatten(tree || []);
        setKpNames(names);
      })
      .catch((err: unknown) => { console.log('KP names not loaded:', err); });
  }, [userId]);

  const handleSend = () => {
    if (!input.trim() || isStreaming) return;
    const msg = input.trim();
    setInput('');

    if (isComposingQuestion && !currentQuestion) {
      const question: Question = {
        id: -Date.now(),
        kpId: 0,
        subject: 'math',
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

  const handleKeyDown = (event: React.KeyboardEvent) => {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      handleSend();
    }
  };

  const handleFileUpload = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file || isStreaming || isExtractingFile) return;

    setIsExtractingFile(true);
    setExtractMessage('');

    try {
      const result = await tutorApi.extractContent(file);
      const extracted = result.data;
      if (!extracted.supported) {
        setExtractMessage(extracted.message);
        return;
      }
      const extractedText = extracted.structuredText || extracted.text;
      const prompt = isComposingQuestion && !currentQuestion
        ? `文件：${extracted.filename}\n\n${extractedText}`
        : `我上传了文件「${extracted.filename}」。请先核对解析内容，再用适合学生的方式讲解或整理：\n\n${extractedText}`;
      setInput((prev) => (prev.trim() ? `${prev.trim()}\n\n${prompt}` : prompt));
      setExtractMessage(extracted.message);
    } catch (error) {
      console.log('File extraction failed:', error);
      const message = error instanceof Error ? error.message : '';
      setExtractMessage(message ? `文件解析失败：${message}` : '文件解析失败，请稍后重试。');
    } finally {
      setIsExtractingFile(false);
      if (event.target) event.target.value = '';
    }
  };

  const startNewSession = () => {
    void endCurrentSession({ resetChat: true });
  };

  const restoreSession = async (session: ChatSession) => {
    if (isStreaming) return;
    const currentSessionId = useChatStore.getState().sessionId;
    if (currentSessionId && currentSessionId !== session.id) {
      await endCurrentSession();
    }
    const detail = await sessionApi.getSession(session.id);
    setSessionId(detail.status === 'ACTIVE' ? detail.id : null);
    setMessages(detail.messages || []);
    setCurrentQuestion(buildQuestionFromSession(detail));
    setIsComposingQuestion(false);
    upsertSession(detail);
  };

  const enterTutorMode = () => {
    if (useChatStore.getState().sessionId) {
      void endCurrentSession({ resetChat: true });
    }
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
      useChatStore.getState().reset();
      setInput('');
      setIsComposingQuestion(false);
    }
  };

  const deleteMemory = async (memory: MemoryEntry) => {
    if (isStreaming) return;
    await memoryApi.deleteMemory(memory.id);
    setMemories((prev) => prev.filter((item) => item.id !== memory.id));
  };

  const changeTeachingStyle = (style: 'guided' | 'direct') => {
    teachingStyleRef.current = style;
    setTeachingStyle(style);
  };

  const isTutorMode = Boolean(currentQuestion) || isComposingQuestion;

  const renderSessionList = (compact = false) => (
    <div className="space-y-1">
      {sessions.map((session) => (
        <button
          type="button"
          key={session.id}
          onClick={() => { void restoreSession(session); }}
          disabled={isStreaming}
          className={`group w-full rounded-lg px-3 py-2 text-left transition-colors disabled:opacity-60 ${
            sessionId === session.id ? 'bg-blue-50 text-blue-700' : 'text-gray-700 hover:bg-gray-50'
          }`}
        >
          <div className="flex items-center gap-2">
            <div className="min-w-0 flex-1 truncate text-xs font-medium">{getSessionTitle(session)}</div>
            {!compact && (
              <span
                role="button"
                tabIndex={0}
                onClick={(event) => { void deleteSession(session, event); }}
                onKeyDown={(event) => {
                  if (event.key === 'Enter' || event.key === ' ') {
                    void deleteSession(session, event as unknown as React.MouseEvent);
                  }
                }}
                className="flex h-6 w-6 items-center justify-center rounded-md text-gray-400 opacity-0 hover:bg-red-50 hover:text-red-600 group-hover:opacity-100"
                title="删除会话"
              >
                <Trash2 className="h-3.5 w-3.5" />
              </span>
            )}
          </div>
          <div className="mt-1 flex items-center justify-between gap-2 text-[10px] text-gray-400">
            <span>{formatSessionTime(session.startedAt)}</span>
            <span className={session.status === 'ACTIVE' ? 'text-green-600' : undefined}>
              {getSessionModeLabel(session)} · {getSessionStatusLabel(session)}
            </span>
          </div>
        </button>
      ))}
    </div>
  );

  return (
    <div className="mx-auto flex h-[calc(100dvh-8rem)] max-w-7xl flex-col sm:h-[calc(100dvh-11rem)] lg:h-[calc(100vh-8rem)]">
      <div className="mb-2 flex shrink-0 flex-col gap-2 sm:mb-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="min-w-0">
          <h1 className="text-lg font-bold text-gray-900 sm:text-xl">家庭陪伴 AI</h1>
          <p className="text-xs text-gray-500">
            {isTutorMode ? '学习陪伴 · 讲题模式' : '自由对话 · 家庭上下文陪伴'}
          </p>
        </div>
        <div className="flex w-full flex-wrap gap-2 sm:w-auto sm:justify-end">
          {isTutorMode ? (
            <div className="flex items-center gap-1 rounded-lg bg-gray-100 p-1">
              <button
                type="button"
                onClick={() => changeTeachingStyle('guided')}
                className={`rounded-md px-3 py-1 text-xs transition-colors ${
                  teachingStyle === 'guided' ? 'bg-white font-medium text-blue-700 shadow-sm' : 'text-gray-500 hover:text-gray-700'
                }`}
              >
                引导式
              </button>
              <button
                type="button"
                onClick={() => changeTeachingStyle('direct')}
                className={`rounded-md px-3 py-1 text-xs transition-colors ${
                  teachingStyle === 'direct' ? 'bg-white font-medium text-green-700 shadow-sm' : 'text-gray-500 hover:text-gray-700'
                }`}
              >
                直接讲解
              </button>
            </div>
          ) : (
            <button
              type="button"
              onClick={enterTutorMode}
              disabled={isStreaming}
              className="rounded-lg bg-blue-50 px-3 py-2 text-xs text-blue-700 hover:bg-blue-100 disabled:opacity-50"
            >
              进入讲题
            </button>
          )}
          {isTutorMode && !currentQuestion && (
            <button
              type="button"
              onClick={exitTutorMode}
              disabled={isStreaming}
              className="rounded-lg border border-gray-200 bg-white px-3 py-2 text-xs text-gray-500 hover:bg-gray-50 disabled:opacity-50"
            >
              自由对话
            </button>
          )}
          {messages.length > 0 && (
            <button
              type="button"
              onClick={startNewSession}
              className="rounded-lg border border-gray-200 bg-white px-3 py-2 text-xs text-gray-600 hover:bg-gray-50"
            >
              结束并新建
            </button>
          )}
        </div>
      </div>

      <details className="mb-2 shrink-0 overflow-hidden rounded-xl border border-gray-200 bg-white sm:mb-3 lg:hidden">
        <summary className="flex cursor-pointer list-none items-center gap-2 px-3 py-2 text-sm font-medium text-gray-800">
          <History className="h-4 w-4" />
          最近会话
          <span className="ml-auto text-xs text-gray-400">{sessions.length}</span>
        </summary>
        <div className="max-h-56 overflow-y-auto border-t border-gray-100 p-2">
          {isLoadingSessions ? (
            <div className="flex h-16 items-center justify-center text-gray-400">
              <Loader2 className="h-4 w-4 animate-spin" />
            </div>
          ) : sessionError ? (
            <div className="px-3 py-3 text-xs text-red-500">{sessionError}</div>
          ) : sessions.length === 0 ? (
            <div className="px-3 py-3 text-xs text-gray-400">暂无历史会话</div>
          ) : renderSessionList(true)}
        </div>
      </details>

      <div className="flex min-h-0 flex-1 gap-3 overflow-hidden">
        <aside className="hidden w-72 shrink-0 flex-col overflow-hidden rounded-xl border border-gray-200 bg-white lg:flex">
          <div className="flex items-center justify-between border-b border-gray-100 px-3 py-2">
            <div className="flex items-center gap-2 text-sm font-medium text-gray-800">
              <History className="h-4 w-4" />
              最近会话
            </div>
            <button
              type="button"
              onClick={startNewSession}
              disabled={isStreaming}
              className="flex h-7 w-7 items-center justify-center rounded-md border border-gray-200 text-gray-500 hover:bg-gray-50 disabled:opacity-50"
              title="结束当前会话并新建"
            >
              <Plus className="h-4 w-4" />
            </button>
          </div>
          <div className="flex-1 overflow-y-auto p-2">
            {isLoadingSessions ? (
              <div className="flex h-24 items-center justify-center text-gray-400">
                <Loader2 className="h-4 w-4 animate-spin" />
              </div>
            ) : sessionError ? (
              <div className="px-3 py-4 text-xs text-red-500">{sessionError}</div>
            ) : sessions.length === 0 ? (
              <div className="flex h-full items-center justify-center text-center text-gray-400">
                <div>
                  <MessageSquareText className="mx-auto mb-2 h-8 w-8 opacity-30" />
                  <p className="text-xs">暂无历史会话</p>
                </div>
              </div>
            ) : renderSessionList()}
          </div>
        </aside>

        <aside className="hidden w-72 shrink-0 flex-col overflow-hidden rounded-xl border border-gray-200 bg-white xl:flex">
          <div className="flex items-center justify-between border-b border-gray-100 px-3 py-2">
            <div className="flex items-center gap-2 text-sm font-medium text-gray-800">
              <Brain className="h-4 w-4" />
              学习记忆
            </div>
            <span className="text-xs text-gray-400">{memories.length}</span>
          </div>
          <div className="flex-1 overflow-y-auto p-2">
            {isLoadingMemories ? (
              <div className="flex h-20 items-center justify-center text-gray-400">
                <Loader2 className="h-4 w-4 animate-spin" />
              </div>
            ) : memories.length === 0 ? (
              <div className="flex h-full items-center justify-center px-4 text-center text-xs text-gray-400">
                结束一次陪伴对话后，小智会在这里沉淀几条学习记录。
              </div>
            ) : (
              <div className="space-y-2">
                {memories.map((memory) => (
                  <div key={memory.id} className="group rounded-lg border border-gray-100 p-2">
                    <div className="mb-1 flex items-center gap-2">
                      <span className="rounded bg-blue-50 px-1.5 py-0.5 text-[10px] font-medium text-blue-700">
                        {memoryTypeLabel(memory.type)}
                      </span>
                      <span className="text-[10px] text-gray-400">重要度 {memory.importance}</span>
                      <button
                        type="button"
                        onClick={() => { void deleteMemory(memory); }}
                        disabled={isStreaming}
                        className="ml-auto flex h-6 w-6 items-center justify-center rounded-md text-gray-400 opacity-0 hover:bg-red-50 hover:text-red-600 disabled:opacity-40 group-hover:opacity-100"
                        title="删除记忆"
                      >
                        <Trash2 className="h-3.5 w-3.5" />
                      </button>
                    </div>
                    <p className="text-xs leading-5 text-gray-700">{memory.content}</p>
                  </div>
                ))}
              </div>
            )}
          </div>
        </aside>

        <div className="flex h-full min-w-0 flex-1 flex-col overflow-hidden rounded-xl border border-gray-200 bg-white">
          {currentQuestion && (
            <div className="flex items-center gap-1 border-b border-gray-100 bg-gray-50 px-4 py-2">
              <span className="rounded-md bg-blue-600 px-3 py-1 text-xs text-white">讲题中</span>
              <span className="ml-auto max-w-[250px] truncate text-[10px] text-gray-400">
                {currentQuestion.content.stem.slice(0, 50)}...
              </span>
            </div>
          )}

          <div className="flex-1 space-y-3 overflow-y-auto p-2.5 sm:space-y-4 sm:p-4">
            {messages.length === 0 ? (
              <div className="flex h-full items-center justify-center">
                <div className="text-center text-gray-400">
                  <FileText className="mx-auto mb-2 h-12 w-12 opacity-30" />
                  <p className="text-sm">
                    {currentQuestion
                      ? '围绕这道题继续提问或说出你的思路'
                      : isComposingQuestion
                        ? '粘贴一道题，开始讲题辅导'
                        : '可以聊学习计划、卡点、情绪、家族经验或一道具体题目'}
                  </p>
                  <p className="mt-1 text-xs">
                    {isTutorMode
                      ? (teachingStyle === 'guided' ? '当前为引导式：AI 会一步步提问推进。' : '当前为直接讲解：AI 会给出答案和步骤。')
                      : '我会结合可见的学习记忆、家族经验和成长守护摘要来回应。'}
                  </p>
                </div>
              </div>
            ) : (
              messages.map((msg) => (
                <div
                  key={msg.id}
                  className={`flex gap-1.5 sm:gap-3 ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}
                >
                  {msg.role === 'assistant' && (
                    <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-blue-600 text-xs font-medium text-white">
                      AI
                    </div>
                  )}
                  <div
                    className={`max-w-[90%] overflow-hidden rounded-2xl px-3 py-2.5 text-sm leading-relaxed whitespace-pre-wrap sm:max-w-[80%] sm:px-4 ${
                      msg.role === 'user'
                        ? 'rounded-br-md bg-blue-600 text-white'
                        : 'rounded-bl-md bg-gray-100 text-gray-900'
                    } ${msg.role === 'assistant' && !msg.content ? 'animate-pulse' : ''}`}
                  >
                    {msg.content ? <MathRenderer content={msg.content} /> : (msg.role === 'assistant' ? '思考中...' : '')}
                  </div>
                  {msg.role === 'user' && (
                    <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-gray-300 text-xs font-medium text-gray-600">
                      U
                    </div>
                  )}
                </div>
              ))
            )}
            <div ref={chatEndRef} />
          </div>

          <div className="border-t border-gray-200 p-1.5 pb-[max(env(safe-area-inset-bottom),0.375rem)] sm:p-3">
            <form
              onSubmit={(event) => { event.preventDefault(); handleSend(); }}
              className="flex items-end gap-1.5 sm:gap-2"
            >
              <input
                ref={fileInputRef}
                type="file"
                accept=".txt,.md,.markdown,.csv,.json,.tex,.pdf,.docx,image/*"
                onChange={(event) => { void handleFileUpload(event); }}
                className="hidden"
              />
              <button
                type="button"
                onClick={() => fileInputRef.current?.click()}
                disabled={isStreaming || isExtractingFile}
                className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border border-gray-200 text-gray-500 hover:bg-gray-50 disabled:opacity-50 sm:h-10 sm:w-10"
                title="上传题目或学习资料"
              >
                {isExtractingFile ? <Loader2 className="h-4 w-4 animate-spin" /> : <Paperclip className="h-4 w-4" />}
              </button>
              <textarea
                rows={1}
                value={input}
                onChange={(event) => setInput(event.target.value)}
                onKeyDown={handleKeyDown}
                placeholder={
                  currentQuestion
                    ? '输入你的想法或问题...'
                    : isComposingQuestion
                      ? '把题目粘贴到这里...'
                      : '聊学习计划、卡点、情绪、家族经验，或直接发一道题...'
                }
                disabled={isStreaming || isExtractingFile}
                className="min-h-9 max-h-28 min-w-0 flex-1 resize-none overflow-y-auto rounded-lg border border-gray-200 px-2.5 py-2 text-sm outline-none focus:border-transparent focus:ring-2 focus:ring-blue-500 disabled:bg-gray-50 sm:min-h-10 sm:max-h-32 sm:px-4"
              />
              <button
                type="submit"
                disabled={!input.trim() || isStreaming || isExtractingFile}
                className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-blue-600 text-white transition-colors hover:bg-blue-700 disabled:opacity-50 sm:h-10 sm:w-auto sm:gap-1 sm:px-4"
                aria-label="发送"
              >
                {isStreaming ? <Loader2 className="h-4 w-4 animate-spin" /> : <Send className="h-4 w-4" />}
              </button>
            </form>
            {extractMessage && (
              <p className="mt-2 text-xs text-gray-500">{extractMessage}</p>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
