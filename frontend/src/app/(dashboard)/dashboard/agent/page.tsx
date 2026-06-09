'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import {
  BookHeart,
  CheckCircle,
  History,
  Loader2,
  Plus,
  Save,
  Send,
  Sparkles,
  Square,
  Trash2,
} from 'lucide-react';
import type {
  AgentSaveToolPlan,
  ChatMessage,
  ChatSessionDetail,
  ChatSessionSummary,
} from '@/types';
import MathRenderer from '@/components/agent/MathRenderer';
import RagMemoryBadge from '@/components/agent/RagMemoryBadge';
import WebSearchBadge from '@/components/agent/WebSearchBadge';
import VoiceInputButton from '@/components/voice/VoiceInputButton';
import { useChat, type SessionSavedMemory } from '@/hooks/useChat';
import { useViewerRole } from '@/hooks/useViewerRole';
import { diaryApi, growthGuardApi, memoryApi, sessionApi, skillRunApi } from '@/lib/api';
import { loadSessionMessagesChronologically } from '@/lib/sessionHistory';
import {
  buildDiarySaveRequest,
  buildFamilyMemorySaveRequest,
  buildGrowthGuardSaveRequest,
  normalizeSaveToolPlan,
  saveMemorySkillMetadata,
  savePlanDetail,
  savedRecordType,
  todayString,
  toolLabel,
  truncateAuditText,
} from '@/lib/savePlan';
import { useChatStore } from '@/stores/chatStore';

type SaveFeedback = {
  status: 'saving' | 'saved' | 'error';
  detail: string;
  href?: string;
};

type ActivationSceneState = {
  label: string;
  instruction: string;
};

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

function getSessionTitle(session: Pick<ChatSessionSummary, 'title' | 'summary'>) {
  return (session.title || session.summary || 'Untitled session').slice(0, 32);
}

function fallbackSavePlan(content: string): AgentSaveToolPlan {
  const cleaned = content.trim();
  return {
    should_save: true,
    tool: 'DIARY',
    content: cleaned,
    title: cleaned.slice(0, 24) || 'Chat note',
    summary: cleaned.slice(0, 80),
    visibility: 'PRIVATE',
    entry_type: 'DAILY',
    memory_type: 'ELDER_ADVICE',
    scope: 'PRIVATE',
    category: 'OTHER',
    severity: 2,
    importance: 3,
    tags: ['family-agent-save'],
    reason: 'The user explicitly chose to save this message.',
    confirmation_message: 'Saved as a diary entry.',
  };
}

function savedMemoryHref(plan: AgentSaveToolPlan, familyId?: number | null) {
  const familyQuery = familyId ? `?familyId=${familyId}` : '';
  if (plan.tool === 'DIARY') return `/dashboard/diary${familyQuery}`;
  if (plan.tool === 'FAMILY_MEMORY') return `/dashboard/heritage${familyQuery}`;
  if (plan.tool === 'GROWTH_GUARD') {
    return `/dashboard/diary${familyId ? `?familyId=${familyId}&tab=growth` : '?tab=growth'}`;
  }
  return `/dashboard/memory${familyQuery}`;
}

function savedMemoryFromPlan(plan: AgentSaveToolPlan, savedAt: string): SessionSavedMemory | null {
  if (!plan.should_save || plan.tool === 'NONE' || !plan.content.trim()) return null;
  return {
    id: `saved-${plan.tool}-${savedAt}`,
    tool: plan.tool,
    label: toolLabel(plan.tool),
    title: plan.title || toolLabel(plan.tool),
    content: plan.content.trim(),
    visibility: String(plan.visibility || plan.scope || 'PRIVATE'),
    savedAt,
    reason: plan.reason,
  };
}

export default function AgentPage() {
  const searchParams = useSearchParams();
  const routePrompt = searchParams.get('prompt')?.trim() || '';
  const routePromptAppliedRef = useRef('');
  const sessionSavedMemoriesRef = useRef<SessionSavedMemory[]>([]);
  const createSessionPromiseRef = useRef<Promise<ChatSessionDetail> | null>(null);
  const sessionGenerationRef = useRef(0);
  const sessionIdRef = useRef<number | null>(null);
  const activeSessionDetailRef = useRef<ChatSessionDetail | null>(null);

  const [input, setInput] = useState('');
  const [sessions, setSessions] = useState<ChatSessionSummary[]>([]);
  const [activeSessionDetail, setActiveSessionDetail] = useState<ChatSessionDetail | null>(null);
  const [isLoadingSessions, setIsLoadingSessions] = useState(false);
  const [isLoadingMessages, setIsLoadingMessages] = useState(false);
  const [sessionError, setSessionError] = useState('');
  const [activationScene, setActivationScene] = useState<ActivationSceneState | null>(null);
  const [saveFeedback, setSaveFeedback] = useState<Record<string, SaveFeedback>>({});

  const { viewerRole, activeFamilyId, activeFamily, activeMembership, isLoading } = useViewerRole();
  const sessionId = useChatStore((state) => state.sessionId);
  const setSessionId = useChatStore((state) => state.setSessionId);
  const setMessages = useChatStore((state) => state.setMessages);

  useEffect(() => {
    sessionIdRef.current = sessionId;
  }, [sessionId]);

  useEffect(() => {
    activeSessionDetailRef.current = activeSessionDetail;
  }, [activeSessionDetail]);

  const upsertSession = useCallback((session: ChatSessionSummary) => {
    setSessions((current) => {
      const next = current.filter((item) => item.id !== session.id);
      return [session, ...next];
    });
  }, []);

  const ensureSessionHeader = useCallback(async () => {
    if (!activeFamilyId) {
      throw new Error('Please choose a family first.');
    }
    const generation = sessionGenerationRef.current;
    const currentSessionId = sessionIdRef.current;
    const currentSessionDetail = activeSessionDetailRef.current;
    if (currentSessionId && currentSessionDetail?.id === currentSessionId) {
      return currentSessionDetail;
    }
    if (currentSessionId) {
      const detail = await sessionApi.getSession(currentSessionId);
      if (sessionGenerationRef.current === generation) {
        activeSessionDetailRef.current = detail;
        setActiveSessionDetail(detail);
        upsertSession(detail);
      }
      return detail;
    }
    if (!createSessionPromiseRef.current) {
      const createGeneration = generation;
      const createPromise = sessionApi.createSession({
        familyId: activeFamilyId,
        subject: 'FamilyAgent',
        source: 'FAMILY_AGENT',
        metadata: {
          entry: 'agent',
          contextLabel: 'family_memory',
        },
      });
      let createRequest: Promise<ChatSessionDetail>;
      createRequest = createPromise
        .then((detail) => {
          if (sessionGenerationRef.current === createGeneration) {
            sessionIdRef.current = detail.id;
            activeSessionDetailRef.current = detail;
            setSessionId(detail.id);
            setActiveSessionDetail(detail);
            upsertSession(detail);
          }
          return detail;
        })
        .finally(() => {
          if (createSessionPromiseRef.current === createRequest) {
            createSessionPromiseRef.current = null;
          }
        });
      createSessionPromiseRef.current = createRequest;
    }
    return createSessionPromiseRef.current;
  }, [activeFamilyId, setSessionId, upsertSession]);

  const appendSessionMessages = useCallback(async (newMessages: ChatMessage[]) => {
    if (!newMessages.length || !activeFamilyId) return;
    const generation = sessionGenerationRef.current;
    setSessionError('');
    try {
      const detail = await ensureSessionHeader();
      const updated = await sessionApi.appendMessages(detail.id, newMessages);
      if (sessionGenerationRef.current !== generation) {
        return;
      }
      sessionIdRef.current = updated.id;
      activeSessionDetailRef.current = updated;
      setSessionId(updated.id);
      setActiveSessionDetail(updated);
      upsertSession(updated);
    } catch (error) {
      if (sessionGenerationRef.current === generation) {
        setSessionError(error instanceof Error ? error.message : 'Failed to save chat history automatically.');
      }
      throw error;
    }
  }, [activeFamilyId, ensureSessionHeader, setSessionId, upsertSession]);

  const {
    messages,
    isStreaming,
    sendMessage,
    stopStreaming,
    discardStreaming,
    reset,
  } = useChat({
    viewerRole,
    targetRole: 'MEMBER',
    activeFamilyId,
    appendSessionMessages,
    onActivationSceneChange: setActivationScene,
    getSessionSavedMemories: () => sessionSavedMemoriesRef.current,
    subject: 'FamilyAgent',
    contextLabel: 'family_memory',
  });

  const recentMessages = useMemo(
    () => messages.filter((message) => message.role !== 'system').slice(-10),
    [messages],
  );

  const loadSessions = useCallback(async () => {
    setIsLoadingSessions(true);
    setSessionError('');
    try {
      const list = await sessionApi.getUserSessions(undefined, 30);
      const filtered = (list || []).filter((session) => (
        session.familyId === activeFamilyId
          && (!session.source || session.source === 'FAMILY_AGENT' || session.source === 'TUTOR')
      ));
      setSessions(filtered);
    } catch (error) {
      setSessionError(error instanceof Error ? error.message : 'Failed to load session history.');
    } finally {
      setIsLoadingSessions(false);
    }
  }, [activeFamilyId]);

  useEffect(() => {
    sessionGenerationRef.current += 1;
    createSessionPromiseRef.current = null;
    sessionIdRef.current = null;
    activeSessionDetailRef.current = null;
    discardStreaming();
    setSessionId(null);
    setActiveSessionDetail(null);
    setMessages([]);
    setSessionError('');
    setSaveFeedback({});
    setActivationScene(null);
    sessionSavedMemoriesRef.current = [];

    if (!activeFamilyId) {
      setSessions([]);
      return;
    }
    void loadSessions();
  }, [activeFamilyId, discardStreaming, loadSessions, setMessages, setSessionId]);

  useEffect(() => {
    if (!routePrompt || routePromptAppliedRef.current === routePrompt) return;
    routePromptAppliedRef.current = routePrompt;
    setInput(routePrompt);
  }, [routePrompt]);

  const handleNewChat = useCallback(() => {
    discardStreaming();
    sessionGenerationRef.current += 1;
    createSessionPromiseRef.current = null;
    sessionIdRef.current = null;
    activeSessionDetailRef.current = null;
    reset();
    setSessionId(null);
    setActiveSessionDetail(null);
    setInput('');
    setSessionError('');
    setSaveFeedback({});
    setActivationScene(null);
    sessionSavedMemoriesRef.current = [];
  }, [discardStreaming, reset, setSessionId]);

  const handleSubmit = useCallback(async () => {
    const content = input.trim();
    if (!content || isStreaming) return;
    setInput('');
    try {
      await sendMessage(content);
    } catch {
      // The append pipeline already surfaces save failures inline.
    }
  }, [input, isStreaming, sendMessage]);

  const loadAllSessionMessages = useCallback((targetSessionId: number) => (
    loadSessionMessagesChronologically(sessionApi.getSessionMessages, targetSessionId, 40)
  ), []);

  const handleLoadSession = useCallback(async (targetSessionId: number) => {
    discardStreaming();
    const generation = sessionGenerationRef.current + 1;
    sessionGenerationRef.current = generation;
    createSessionPromiseRef.current = null;
    setIsLoadingMessages(true);
    setSessionError('');
    try {
      const detail = await sessionApi.getSession(targetSessionId);
      const restoredMessages = await loadAllSessionMessages(targetSessionId);
      if (sessionGenerationRef.current !== generation) {
        return;
      }
      sessionIdRef.current = detail.id;
      activeSessionDetailRef.current = detail;
      setSessionId(detail.id);
      setMessages(restoredMessages);
      setActiveSessionDetail(detail);
      setSaveFeedback({});
      setActivationScene(null);
      sessionSavedMemoriesRef.current = [];
      upsertSession(detail);
    } catch (error) {
      if (sessionGenerationRef.current === generation) {
        setSessionError(error instanceof Error ? error.message : 'Failed to load the selected session.');
      }
    } finally {
      if (sessionGenerationRef.current === generation) {
        setIsLoadingMessages(false);
      }
    }
  }, [discardStreaming, loadAllSessionMessages, setMessages, setSessionId, upsertSession]);

  const handleDeleteSession = useCallback(async (targetSessionId: number) => {
    try {
      await sessionApi.deleteSession(targetSessionId);
      setSessions((current) => current.filter((session) => session.id !== targetSessionId));
      if (sessionId === targetSessionId) {
        handleNewChat();
      }
    } catch (error) {
      setSessionError(error instanceof Error ? error.message : 'Failed to delete the session.');
    }
  }, [handleNewChat, sessionId]);

  const handleSaveMessage = useCallback(async (message: ChatMessage) => {
    if (!activeFamilyId) {
      setSaveFeedback((current) => ({
        ...current,
        [message.id]: { status: 'error', detail: 'Choose a family before saving.' },
      }));
      return;
    }

    const originalContent = message.content.trim();
    if (!originalContent) return;

    setSaveFeedback((current) => ({
      ...current,
      [message.id]: { status: 'saving', detail: 'Saving...' },
    }));

    let skillRunId: number | null = null;

    try {
      const skillRun = await skillRunApi.create({
        familyId: activeFamilyId,
        skillName: 'save_memory',
        status: 'RUNNING',
        source: 'FAMILY_AGENT_CHAT',
        inputSummary: truncateAuditText(originalContent),
        saved: false,
      });
      skillRunId = skillRun.id;

      const planResult = await memoryApi.planSaveTool({
        message: originalContent,
        familyContext: activeFamily?.name || '',
        conversationContext: recentMessages,
        targetMemberName: activeMembership?.relationshipLabel || '',
        viewerRole,
      });

      const normalized = normalizeSaveToolPlan(planResult.data);
      const plan = normalized.should_save && normalized.tool !== 'NONE'
        ? normalized
        : normalizeSaveToolPlan(fallbackSavePlan(originalContent));

      const savedAt = new Date().toISOString();
      const commonMetadata = {
        source: 'FAMILY_COMPANION_TOOL',
        relationSource: 'FAMILY_AGENT_TOOL',
        savedFromMessageRole: message.role,
        familyName: activeFamily?.name || '',
        viewerRole,
        ...saveMemorySkillMetadata(plan, savedAt),
      };

      let savedRecordId: number | undefined;
      if (plan.tool === 'DIARY') {
        const saved = await diaryApi.create(buildDiarySaveRequest(activeFamilyId, plan, commonMetadata));
        savedRecordId = saved.id;
      } else if (plan.tool === 'FAMILY_MEMORY') {
        const saved = await memoryApi.createFamilyMemory(buildFamilyMemorySaveRequest(activeFamilyId, plan, commonMetadata));
        savedRecordId = saved.id;
      } else if (plan.tool === 'GROWTH_GUARD') {
        const saved = await growthGuardApi.createRecord(
          buildGrowthGuardSaveRequest(activeFamilyId, plan, todayString(), commonMetadata),
        );
        savedRecordId = saved.id;
      }

      if (skillRunId) {
        await skillRunApi.update(skillRunId, {
          status: 'SUCCEEDED',
          saved: true,
          outputSummary: savePlanDetail(plan, savedRecordId),
          metadata: {
            savedRecordType: savedRecordType(plan.tool),
            savedRecordId,
          },
        });
      }

      const savedMemory = savedMemoryFromPlan(plan, savedAt);
      if (savedMemory) {
        sessionSavedMemoriesRef.current = [...sessionSavedMemoriesRef.current, savedMemory].slice(-10);
      }

      setSaveFeedback((current) => ({
        ...current,
        [message.id]: {
          status: 'saved',
          detail: savePlanDetail(plan, savedRecordId),
          href: savedMemoryHref(plan, activeFamilyId),
        },
      }));
    } catch (error) {
      if (skillRunId) {
        try {
          await skillRunApi.update(skillRunId, {
            status: 'FAILED',
            saved: false,
            outputSummary: error instanceof Error ? error.message : 'Save failed',
          });
        } catch {
          // ignore secondary failure
        }
      }

      setSaveFeedback((current) => ({
        ...current,
        [message.id]: {
          status: 'error',
          detail: error instanceof Error ? error.message : 'Save failed. Please retry later.',
        },
      }));
    }
  }, [activeFamily?.name, activeFamilyId, activeMembership?.relationshipLabel, recentMessages, viewerRole]);

  if (isLoading) {
    return (
      <div className="flex min-h-[60vh] items-center justify-center text-sm text-gray-500">
        <Loader2 className="mr-2 h-4 w-4 animate-spin" />
        Loading family context...
      </div>
    );
  }

  if (!activeFamilyId) {
    return (
      <div className="mx-auto max-w-3xl px-4 py-10">
        <div className="rounded-2xl border border-dashed border-gray-300 bg-white p-8 text-center">
          <Sparkles className="mx-auto h-10 w-10 text-blue-600" />
          <h1 className="mt-4 text-xl font-semibold text-gray-900">Choose a family first</h1>
          <p className="mt-2 text-sm text-gray-600">
            FamilyAgent uses family memories, diaries, and growth notes as conversation context.
          </p>
          <Link
            href="/dashboard/family"
            className="mt-5 inline-flex items-center rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
          >
            Open family space
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="flex min-h-[calc(100vh-4rem)] flex-col gap-4 px-4 py-4 lg:flex-row">
      <aside className="w-full rounded-2xl border border-gray-200 bg-white lg:w-80">
        <div className="flex items-center justify-between border-b border-gray-100 px-4 py-3">
          <div>
            <div className="text-sm font-semibold text-gray-900">FamilyAgent</div>
            <div className="text-xs text-gray-500">{activeFamily?.name || 'Current family'}</div>
          </div>
          <button
            type="button"
            onClick={handleNewChat}
            className="inline-flex items-center gap-1 rounded-lg border border-gray-200 px-3 py-1.5 text-xs text-gray-700 hover:bg-gray-50"
          >
            <Plus className="h-3.5 w-3.5" />
            New chat
          </button>
        </div>

        <div className="border-b border-gray-100 px-4 py-3">
          <div className="rounded-xl bg-blue-50 px-3 py-3 text-sm text-blue-900">
            <div className="flex items-center gap-2 font-medium">
              <BookHeart className="h-4 w-4" />
              Family memory companion
            </div>
            <p className="mt-2 text-xs leading-5 text-blue-800">
              Chat freely, restore long histories seamlessly, and save important moments back into family assets.
            </p>
          </div>
        </div>

        <div className="px-4 py-3">
          <div className="mb-2 flex items-center gap-2 text-sm font-medium text-gray-900">
            <History className="h-4 w-4" />
            Session history
          </div>

          {sessionError && (
            <div className="mb-3 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-700">
              {sessionError}
            </div>
          )}

          {isLoadingSessions ? (
            <div className="py-8 text-center text-xs text-gray-500">
              <Loader2 className="mx-auto mb-2 h-4 w-4 animate-spin" />
              Loading sessions...
            </div>
          ) : sessions.length === 0 ? (
            <div className="rounded-xl border border-dashed border-gray-200 px-3 py-6 text-center text-xs text-gray-500">
              No saved sessions yet. Start the first family conversation.
            </div>
          ) : (
            <div className="space-y-2">
              {sessions.map((session) => (
                <div
                  key={session.id}
                  className={`rounded-xl border px-3 py-2 ${sessionId === session.id ? 'border-blue-200 bg-blue-50' : 'border-gray-200 bg-white'}`}
                >
                  <button
                    type="button"
                    onClick={() => { void handleLoadSession(session.id); }}
                    className="w-full text-left"
                  >
                    <div className="text-sm font-medium text-gray-900">{getSessionTitle(session)}</div>
                    {session.summary && (
                      <div className="mt-1 line-clamp-2 text-xs text-gray-500">{session.summary}</div>
                    )}
                    <div className="mt-2 flex items-center justify-between text-[11px] text-gray-500">
                      <span>{formatSessionTime(session.lastMessageAt || session.startedAt)}</span>
                      <span>{session.messageCount || 0} messages</span>
                    </div>
                  </button>
                  <div className="mt-2 flex justify-end">
                    <button
                      type="button"
                      onClick={() => { void handleDeleteSession(session.id); }}
                      className="inline-flex items-center gap-1 text-xs text-gray-500 hover:text-rose-600"
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                      Delete
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </aside>

      <section className="flex min-h-[70vh] flex-1 flex-col overflow-hidden rounded-2xl border border-gray-200 bg-white">
        <div className="border-b border-gray-100 px-5 py-4">
          <div className="flex items-start justify-between gap-4">
            <div>
              <h1 className="text-lg font-semibold text-gray-900">Family conversation</h1>
              <p className="mt-1 text-sm text-gray-600">
                One continuous timeline now includes both recent messages and archived history.
              </p>
            </div>
            <div className="flex max-w-xs flex-col gap-2">
              {activeSessionDetail && (
                <div className="rounded-xl border border-gray-200 bg-gray-50 px-3 py-2 text-xs text-gray-600">
                  <div className="font-medium text-gray-800">{getSessionTitle(activeSessionDetail)}</div>
                  <div className="mt-1">
                    {activeSessionDetail.messageCount || 0} messages
                    {activeSessionDetail.archives?.length ? ` · ${activeSessionDetail.archives.length} archived ranges` : ''}
                  </div>
                </div>
              )}
              {activationScene && (
                <div className="rounded-xl border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-800">
                  <div className="font-medium">Activated context: {activationScene.label}</div>
                  <div className="mt-1 leading-5">{activationScene.instruction}</div>
                </div>
              )}
            </div>
          </div>
        </div>

        <div className="flex-1 space-y-4 overflow-y-auto bg-gray-50/60 px-4 py-4">
          {isLoadingMessages && (
            <div className="py-6 text-center text-sm text-gray-500">
              <Loader2 className="mx-auto mb-2 h-4 w-4 animate-spin" />
              Restoring session...
            </div>
          )}

          {!isLoadingMessages && messages.length === 0 ? (
            <div className="mx-auto max-w-2xl rounded-2xl border border-dashed border-gray-200 bg-white px-6 py-10 text-center">
              <Sparkles className="mx-auto h-10 w-10 text-blue-600" />
              <h2 className="mt-4 text-lg font-semibold text-gray-900">Start a family conversation</h2>
              <p className="mt-2 text-sm leading-6 text-gray-600">
                Try topics like family memories, caregiving, values, growth notes, or something worth saving.
              </p>
            </div>
          ) : (
            messages.map((message) => {
              const feedback = saveFeedback[message.id];
              const isAssistant = message.role === 'assistant';
              return (
                <div
                  key={message.id}
                  className={`mx-auto max-w-3xl rounded-2xl border px-4 py-3 shadow-sm ${isAssistant ? 'border-blue-100 bg-white' : 'border-gray-200 bg-gray-900 text-white'}`}
                >
                  <div className="mb-2 flex items-center justify-between gap-3">
                    <div className={`text-xs font-medium ${isAssistant ? 'text-blue-700' : 'text-gray-200'}`}>
                      {isAssistant ? 'FamilyAgent' : 'You'}
                    </div>
                    <div className={`text-[11px] ${isAssistant ? 'text-gray-400' : 'text-gray-300'}`}>
                      {formatSessionTime(message.timestamp)}
                    </div>
                  </div>

                  <div className={`text-sm leading-7 ${isAssistant ? 'text-gray-800' : 'text-white'}`}>
                    {isAssistant ? <MathRenderer content={message.content} /> : message.content}
                  </div>

                  {isAssistant && <RagMemoryBadge metadata={message.metadata} />}
                  {isAssistant && <WebSearchBadge metadata={message.metadata} />}

                  <div className="mt-3 flex items-center justify-between gap-3">
                    <button
                      type="button"
                      onClick={() => { void handleSaveMessage(message); }}
                      disabled={feedback?.status === 'saving'}
                      className={`inline-flex items-center gap-1 rounded-lg px-3 py-1.5 text-xs font-medium ${
                        isAssistant
                          ? 'border border-gray-200 text-gray-700 hover:bg-gray-50'
                          : 'border border-white/20 text-white hover:bg-white/10'
                      }`}
                    >
                      {feedback?.status === 'saving' ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Save className="h-3.5 w-3.5" />}
                      Save this message
                    </button>

                    {feedback && (
                      <div className={`text-xs ${feedback.status === 'error' ? 'text-rose-600' : 'text-emerald-600'}`}>
                        {feedback.status === 'saved' && <CheckCircle className="mr-1 inline h-3.5 w-3.5" />}
                        {feedback.detail}
                        {feedback.href && (
                          <Link href={feedback.href} className="ml-2 underline underline-offset-2">
                            Open
                          </Link>
                        )}
                      </div>
                    )}
                  </div>
                </div>
              );
            })
          )}
        </div>

        <form
          onSubmit={(event) => {
            event.preventDefault();
            if (isStreaming) {
              stopStreaming();
              return;
            }
            void handleSubmit();
          }}
          className="border-t border-gray-100 bg-white px-4 py-3"
        >
          <div className="mx-auto max-w-3xl">
            <div className="mb-2 flex justify-end">
              <VoiceInputButton
                onTranscript={(text) => setInput((current) => (current ? `${current}\n${text}` : text))}
                disabled={isStreaming}
              />
            </div>
            <div className="flex items-end gap-3">
              <textarea
                value={input}
                onChange={(event) => setInput(event.target.value)}
                placeholder="Ask about family memories, growth notes, caregiving, values, or something worth preserving..."
                disabled={isStreaming}
                rows={4}
                className="min-h-[96px] flex-1 resize-none rounded-2xl border border-gray-200 px-4 py-3 text-sm outline-none transition focus:border-blue-400 focus:ring-2 focus:ring-blue-100 disabled:bg-gray-50"
              />
              <button
                type="submit"
                disabled={isStreaming ? false : !input.trim()}
                className="inline-flex h-12 items-center justify-center rounded-xl bg-blue-600 px-4 text-sm font-medium text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
              >
                {isStreaming ? <Square className="h-4 w-4" /> : <Send className="h-4 w-4" />}
              </button>
            </div>
          </div>
        </form>
      </section>
    </div>
  );
}
