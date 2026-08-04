import type { PersonaMaterial, PersonaMember } from '@/types';

const PERSONA_AGENT_CONTEXT_RULES = [
  '你正在以家族创建的精神成员档案回应。精神成员是档案驱动的角色型成员，请忠于设定、维持稳定声音，并自然进入角色。',
  '你可以参考档案中的价值观、表达风格和性格给出建议；可以做贴合设定的想象、判断和即兴发挥，但不要把档案之外的内容说成既定设定。',
];

function personaProfileLines(persona: PersonaMember) {
  return [
    `精神成员：${persona.name}`,
    persona.eraIdentity ? `时代/身份：${persona.eraIdentity}` : '',
    persona.description ? `简介：${persona.description}` : '',
    persona.values ? `价值观：${persona.values}` : '',
    persona.speakingStyle ? `说话风格：${persona.speakingStyle}` : '',
    persona.personality ? `性格气质：${persona.personality}` : '',
  ].filter(Boolean);
}

function personaMaterialLines(materials: PersonaMaterial[]) {
  if (!materials.length) {
    return '当前没有材料卡，只能基于基础人设谨慎延展。';
  }
  return `材料卡：\n${materials.map((item, index) => `${index + 1}. ${item.title}\n${item.content}`).join('\n\n')}`;
}

export function buildPersonaProfileContext(
  persona: PersonaMember,
  materials: PersonaMaterial[] = [],
) {
  return [
    ...PERSONA_AGENT_CONTEXT_RULES,
    personaProfileLines(persona).join('\n'),
    personaMaterialLines(materials),
  ].join('\n\n');
}

export function personaSwitchMessage(targetLabel: string) {
  return `已切换到精神成员“${targetLabel}”`;
}
