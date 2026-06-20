import { describe, expect, it } from 'vitest';
import type { PersonaMember } from '@/types';
import { buildPersonaProfileContext, personaSwitchMessage } from './personaContext';

function persona(overrides: Partial<PersonaMember> = {}): PersonaMember {
  return {
    id: 7,
    familyId: 1,
    name: '外公',
    createdBy: 100,
    description: '像山一样稳，但说话不绕。',
    values: '重视家风和担当。',
    speakingStyle: '短句，有分寸。',
    personality: '克制、锋利、护短。',
    eraIdentity: '家族精神成员',
    hasMaterial: true,
    createdAt: '2026-06-13T00:00:00.000Z',
    ...overrides,
  };
}

describe('personaContext', () => {
  it('builds immersive persona context around setting consistency', () => {
    const context = buildPersonaProfileContext(persona(), [{
      id: 11,
      familyId: 1,
      personaId: 7,
      title: '做事风格',
      content: '先把责任扛起来，再谈委屈。',
      tags: ['价值观'],
      createdBy: 100,
      createdAt: '2026-06-13T00:00:00.000Z',
    }]);

    expect(context).toContain('档案驱动的角色型成员');
    expect(context).toContain('忠于设定');
    expect(context).toContain('即兴发挥');
    expect(context).toContain('做事风格');
    expect(context).not.toContain('不代表真实成员本人');
  });

  it('uses role voice in persona switch copy', () => {
    expect(personaSwitchMessage('外公')).toContain('稳定角色声音');
    expect(personaSwitchMessage('外公')).not.toContain('真实成员');
  });
});
