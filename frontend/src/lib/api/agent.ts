import { request, sseStreamRequest } from './shared';
import type { ViewerRole } from '@/lib/roles';
import type {
  AgentConfirmationDecision,
  AgentSaveMemoryToolRequest,
  AgentToolExecutionResult,
  AgentToolConfirmationDecisionResult,
} from '@/types';

export const agentApi = {
  requestSaveMemoryTool: (data: AgentSaveMemoryToolRequest) =>
    request<AgentToolExecutionResult>('/agent/save-memory-tool', {
      method: 'POST',
      body: JSON.stringify(data),
    }),
  decideToolConfirmation: (confirmationId: number, decision: AgentConfirmationDecision) =>
    request<AgentToolConfirmationDecisionResult>(`/agent/tool-confirmations/${confirmationId}/decision`, {
      method: 'POST',
      body: JSON.stringify({ decision }),
    }),
  streamChat: (
    body: {
      message: string;
      sessionId?: number | null;
      familyId?: number | null;
      targetUserId?: number | null;
      targetPersonaId?: number | null;
      history?: { role: string; content: string }[];
      subject?: string;
      contextLabel?: string;
      memoryContext?: string;
      viewerRole?: ViewerRole;
      targetRole?: ViewerRole;
      clientTimestamp?: string;
      clientTimezone?: string;
    },
    onChunk: (chunk: string) => void,
    onDone: () => void,
    onError: (error: string) => void,
    onMetadata?: (metadata: Record<string, unknown>) => void,
    onAbort?: () => void,
  ) => sseStreamRequest('/agent/chat/stream', {
    member_message: body.message,
    session_id: body.sessionId || null,
    family_id: body.familyId || null,
    target_user_id: body.targetUserId || null,
    target_persona_id: body.targetPersonaId || null,
    history: body.history || [],
    subject: body.subject || 'FamilyAgent',
    knowledge_point: body.contextLabel || '',
    memory_context: body.memoryContext || '',
    viewer_role: body.viewerRole || 'MEMBER',
    target_role: body.targetRole || 'MEMBER',
    client_timestamp: body.clientTimestamp || new Date().toISOString(),
    client_timezone: body.clientTimezone || Intl.DateTimeFormat().resolvedOptions().timeZone || '',
  }, onChunk, onDone, onError, onMetadata, onAbort),
};
