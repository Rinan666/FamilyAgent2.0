import { request, sseStreamRequest } from './shared';
import type { ViewerRole } from '@/lib/roles';
import type {
  AgentConfirmationDecision,
  AgentResponseMode,
  AgentToolConfirmationDecisionResult,
} from '@/types';

export const agentApi = {
  decideToolConfirmation: (confirmationId: number, decision: AgentConfirmationDecision) =>
    request<AgentToolConfirmationDecisionResult>(`/agent/tool-confirmations/${confirmationId}/decision`, {
      method: 'POST',
      body: JSON.stringify({ decision }),
    }),
  streamChat: (
    body: {
      message: string;
      familyId?: number | null;
      targetUserId?: number | null;
      targetPersonaId?: number | null;
      history?: { role: string; content: string }[];
      subject?: string;
      contextLabel?: string;
      memoryContext?: string;
      viewerRole?: ViewerRole;
      targetRole?: ViewerRole;
      responseMode?: AgentResponseMode;
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
    family_id: body.familyId || null,
    target_user_id: body.targetUserId || null,
    target_persona_id: body.targetPersonaId || null,
    history: body.history || [],
    subject: body.subject || 'FamilyAgent',
    knowledge_point: body.contextLabel || '',
    memory_context: body.memoryContext || '',
    viewer_role: body.viewerRole || 'MEMBER',
    target_role: body.targetRole || 'MEMBER',
    response_mode: body.responseMode || 'think',
    client_timestamp: body.clientTimestamp || new Date().toISOString(),
    client_timezone: body.clientTimezone || Intl.DateTimeFormat().resolvedOptions().timeZone || '',
  }, onChunk, onDone, onError, onMetadata, onAbort),
};
