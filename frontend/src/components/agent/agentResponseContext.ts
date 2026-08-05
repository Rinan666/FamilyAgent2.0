import type { AgentMode, ChatMessage } from '@/types';

type AssistantMetadata = NonNullable<ChatMessage['metadata']>;

export function contextSwitchMessage(mode: AgentMode, targetLabel: string) {
  if (mode === 'mirror') return `已切换为“${targetLabel}”镜像参考`;
  if (mode === 'persona') return `已切换为精神成员“${targetLabel}”`;
  return '已切换为家庭 Agent';
}

export function assistantContextLabel(metadata?: AssistantMetadata) {
  if (!metadata) return '家庭 Agent';
  if (metadata.agentMode === 'mirror' || metadata.effectiveContext === 'MIRROR') {
    const target = metadata.targetMemberName?.trim();
    return target ? `${target} · 镜像参考` : '镜像参考';
  }
  if (metadata.agentMode === 'persona' || metadata.effectiveContext === 'PERSONA') {
    const target = metadata.targetPersonaName?.trim() || metadata.targetMemberName?.trim();
    return target ? `${target} · 精神成员` : '精神成员';
  }
  return '家庭 Agent';
}
