import type { AgentMode, FamilyMember, PersonaMember } from '@/types';
import { memberName, parsePositiveNumber } from './agentDisplay';

export type PersonaTargetSelection = `PERSONA:${number}`;
export type AgentTargetSelection = 'NONE' | 'SELF' | number | PersonaTargetSelection;

const PERSONA_PREFIX = 'PERSONA:';

export function personaTargetSelection(personaId: number): PersonaTargetSelection {
  return `${PERSONA_PREFIX}${personaId}` as PersonaTargetSelection;
}

export function isPersonaTargetSelection(selection: AgentTargetSelection | null | undefined): selection is PersonaTargetSelection {
  return typeof selection === 'string' && selection.startsWith(PERSONA_PREFIX);
}

export function normalizeTargetSelection(
  selection: AgentTargetSelection | null | undefined,
  selfUserId?: number | null,
): AgentTargetSelection {
  if (selection === 'SELF') return 'SELF';
  if (selection === 'NONE') return 'NONE';
  if (isPersonaTargetSelection(selection)) {
    const personaId = parsePositiveNumber(selection.slice(PERSONA_PREFIX.length));
    return personaId ? personaTargetSelection(personaId) : 'NONE';
  }
  const value = parsePositiveNumber(selection);
  if (!value) return 'NONE';
  if (selfUserId && value === selfUserId) return 'SELF';
  return value;
}

export function selectionFromRequestedTargetUserId(
  requestedTargetUserId: number | null,
  selfUserId?: number | null,
  members: FamilyMember[] = [],
): AgentTargetSelection {
  if (!requestedTargetUserId) return 'NONE';
  if (selfUserId && requestedTargetUserId === selfUserId) return 'SELF';
  return members.some((member) => member.userId === requestedTargetUserId) ? requestedTargetUserId : 'NONE';
}

export function selectionFromRequestedPersonaId(
  requestedPersonaId: number | null,
  personas: PersonaMember[] = [],
): AgentTargetSelection {
  if (!requestedPersonaId) return 'NONE';
  return personas.some((persona) => persona.id === requestedPersonaId)
    ? personaTargetSelection(requestedPersonaId)
    : 'NONE';
}

export function selectionMirrorTargetUserId(
  selection: AgentTargetSelection,
  selfUserId?: number | null,
): number | null {
  if (selection === 'NONE') return null;
  if (isPersonaTargetSelection(selection)) return null;
  if (selection === 'SELF') return selfUserId ?? null;
  return selection;
}

export function selectionPersonaId(selection: AgentTargetSelection): number | null {
  if (!isPersonaTargetSelection(selection)) return null;
  return parsePositiveNumber(selection.slice(PERSONA_PREFIX.length));
}

export function selectionMode(
  selection: AgentTargetSelection,
  selfUserId?: number | null,
): AgentMode {
  if (isPersonaTargetSelection(selection)) return 'persona';
  return selectionMirrorTargetUserId(selection, selfUserId) ? 'mirror' : 'family';
}

export function selectionTargetMember(
  selection: AgentTargetSelection,
  members: FamilyMember[],
  mirrorTargetUserId: number | null,
  mirrorTargetMember?: FamilyMember | null,
): FamilyMember | null {
  if (selection === 'NONE') return null;
  if (isPersonaTargetSelection(selection)) return null;
  return members.find((member) => member.userId === mirrorTargetUserId) || mirrorTargetMember || null;
}

export function selectionTargetPersona(
  selection: AgentTargetSelection,
  personas: PersonaMember[],
): PersonaMember | null {
  const personaId = selectionPersonaId(selection);
  if (!personaId) return null;
  return personas.find((persona) => persona.id === personaId) || null;
}

export function selectionLabel(
  selection: AgentTargetSelection,
  targetMember?: FamilyMember | null,
  targetPersona?: PersonaMember | null,
): string {
  if (selection === 'NONE') return 'FamilyAgent';
  if (selection === 'SELF') return '镜像自己';
  if (isPersonaTargetSelection(selection)) return targetPersona?.name?.trim() || '精神成员';
  return memberName(targetMember);
}
