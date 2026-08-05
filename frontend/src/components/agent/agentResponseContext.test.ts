import { describe, expect, it } from 'vitest';
import { assistantContextLabel, contextSwitchMessage } from './agentResponseContext';

describe('agentResponseContext', () => {
  it('uses the same concise switch wording for manual and command switches', () => {
    expect(contextSwitchMessage('mirror', '大儿子')).toBe('已切换为“大儿子”镜像参考');
    expect(contextSwitchMessage('persona', '外公')).toBe('已切换为精神成员“外公”');
    expect(contextSwitchMessage('family', '')).toBe('已切换为家庭 Agent');
  });

  it('labels each answer with the identity captured in its own metadata', () => {
    expect(assistantContextLabel({ agentMode: 'mirror', targetMemberName: '大儿子' }))
      .toBe('大儿子 · 镜像参考');
    expect(assistantContextLabel({ effectiveContext: 'PERSONA', targetPersonaName: '外公' }))
      .toBe('外公 · 精神成员');
    expect(assistantContextLabel({ agentMode: 'family' })).toBe('家庭 Agent');
  });
});
