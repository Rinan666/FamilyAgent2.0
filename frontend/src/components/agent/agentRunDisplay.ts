import type { AgentRunEventView, AgentRunStatus } from '@/types';

export function agentRunStatusLabel(status: string) {
  const labels: Record<string, string> = {
    RUNNING: '执行中',
    WAITING_CONFIRMATION: '等待确认',
    SUCCEEDED: '已完成',
    FAILED: '执行失败',
    CANCELED: '已取消',
    CONFIRMATION_REQUIRED: '等待确认',
    DENIED: '权限拒绝',
  };
  return labels[status] || status;
}

export function agentRunStatusClass(status: AgentRunStatus) {
  if (status === 'SUCCEEDED') return 'bg-emerald-100 text-emerald-800';
  if (status === 'FAILED') return 'bg-rose-100 text-rose-800';
  if (status === 'WAITING_CONFIRMATION') return 'bg-amber-100 text-amber-800';
  if (status === 'CANCELED') return 'bg-stone-200 text-stone-600';
  return 'bg-sky-100 text-sky-800';
}

export function agentRunOperationLabel(event: AgentRunEventView) {
  const labels: Record<string, string> = {
    'llm.chat_stream': '生成回答',
    'web_search.public': '检索公开信息',
    'memory.recall.authorized': '召回授权记忆',
    'embedding.recall_query': '准备语义检索',
    recall_family_memory: '读取家庭记忆',
    create_personal_memory: '保存个人记忆',
    create_family_memory: '保存家庭记忆',
    organize_draft: '整理单条草稿',
    organize_batch_draft: '整理长文本候选',
    save_memory_plan: '准备保存草稿',
  };
  return labels[event.operation] || event.operation.replaceAll('_', ' ').replaceAll('.', ' / ');
}

export function agentRunErrorLabel(errorCode?: string) {
  if (!errorCode) return '执行未完成，请稍后重试。';
  const labels: Record<string, string> = {
    AI_TIMEOUT: 'AI 服务响应超时。',
    AI_PROVIDER_ERROR: 'AI 服务暂时不可用。',
    AI_SERVICE_UNAVAILABLE: 'AI 服务暂时不可用。',
    PERMISSION_DENIED: '当前账号没有执行该步骤的权限。',
    CONFIRMATION_REJECTED: '用户取消了待确认操作。',
  };
  return labels[errorCode] || `执行失败（${errorCode}）。`;
}
