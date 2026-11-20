import { describe, expect, it } from 'vitest';
import {
  agentRunErrorLabel,
  agentRunOperationLabel,
  agentRunStatusLabel,
} from './agentRunDisplay';

describe('agent run display', () => {
  it('maps internal run values to user-facing labels', () => {
    expect(agentRunStatusLabel('WAITING_CONFIRMATION')).toBe('等待确认');
    expect(agentRunStatusLabel('CONFIRMATION_REQUIRED')).toBe('等待确认');
    expect(agentRunOperationLabel({
      id: 1,
      eventType: 'STEP',
      operation: 'memory.recall.authorized',
      status: 'SUCCEEDED',
      degraded: false,
    })).toBe('召回授权记忆');
    expect(agentRunErrorLabel('AI_TIMEOUT')).toBe('AI 服务响应超时。');
  });
});
