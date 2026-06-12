import type { AgentMode, FamilyMember } from '@/types';
import { memberName, parsePositiveNumber } from './agentDisplay';

export type AgentTargetSelection = 'NONE' | 'SELF' | number;

export function normalizeTargetSelection(
  selection: AgentTargetSelection | null | undefined,
  selfUserId?: number | null,
): AgentTargetSelection {
  if (selection === 'SELF') return 'SELF';
  if (selection === 'NONE') return 'NONE';
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

export function selectionMirrorTargetUserId(
  selection: AgentTargetSelection,
  selfUserId?: number | null,
): number | null {
  if (selection === 'NONE') return null;
  if (selection === 'SELF') return selfUserId ?? null;
  return selection;
}

export function selectionMode(
  selection: AgentTargetSelection,
  selfUserId?: number | null,
): AgentMode {
  return selectionMirrorTargetUserId(selection, selfUserId) ? 'mirror' : 'family';
}

export function selectionTargetMember(
  selection: AgentTargetSelection,
  members: FamilyMember[],
  mirrorTargetUserId: number | null,
  mirrorTargetMember?: FamilyMember | null,
): FamilyMember | null {
  if (selection === 'NONE') return null;
  return members.find((member) => member.userId === mirrorTargetUserId) || mirrorTargetMember || null;
}

export function selectionLabel(
  selection: AgentTargetSelection,
  targetMember?: FamilyMember | null,
): string {
  if (selection === 'NONE') return 'FamilyAgent';
  if (selection === 'SELF') return '镜像自己';
  return memberName(targetMember);
}
