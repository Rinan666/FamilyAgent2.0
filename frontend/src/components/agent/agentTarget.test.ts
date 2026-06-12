import { describe, expect, it } from 'vitest';
import type { FamilyMember } from '@/types';
import {
  normalizeTargetSelection,
  selectionFromRequestedTargetUserId,
  selectionLabel,
  selectionMirrorTargetUserId,
  selectionMode,
} from './agentTarget';

function member(userId: number, relationshipLabel: string): FamilyMember {
  return {
    id: userId,
    familyId: 1,
    userId,
    username: `user-${userId}`,
    role: 'MEMBER',
    relationshipLabel,
    createdAt: '2026-06-13T00:00:00.000Z',
  };
}

describe('agentTarget helpers', () => {
  const selfUserId = 100;
  const members = [member(100, '我自己'), member(101, '妈妈'), member(102, '孩子')];

  it('uses NONE when no targetUserId is provided', () => {
    expect(selectionFromRequestedTargetUserId(null, selfUserId, members)).toBe('NONE');
    expect(selectionMode('NONE', selfUserId)).toBe('family');
    expect(selectionMirrorTargetUserId('NONE', selfUserId)).toBeNull();
    expect(selectionLabel('NONE', null)).toBe('FamilyAgent');
  });

  it('maps self targetUserId to explicit SELF selection', () => {
    expect(selectionFromRequestedTargetUserId(selfUserId, selfUserId, members)).toBe('SELF');
    expect(selectionMode('SELF', selfUserId)).toBe('mirror');
    expect(selectionMirrorTargetUserId('SELF', selfUserId)).toBe(selfUserId);
    expect(selectionLabel('SELF', members[0])).toBe('镜像自己');
  });

  it('keeps other family members as mirror targets', () => {
    expect(selectionFromRequestedTargetUserId(101, selfUserId, members)).toBe(101);
    expect(selectionMode(101, selfUserId)).toBe('mirror');
    expect(selectionMirrorTargetUserId(101, selfUserId)).toBe(101);
    expect(selectionLabel(101, members[1])).toBe('妈妈');
  });

  it('normalizes invalid or self numeric selections safely', () => {
    expect(normalizeTargetSelection(null, selfUserId)).toBe('NONE');
    expect(normalizeTargetSelection(0, selfUserId)).toBe('NONE');
    expect(normalizeTargetSelection(selfUserId, selfUserId)).toBe('SELF');
  });
});
