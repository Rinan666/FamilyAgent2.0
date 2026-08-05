import { describe, expect, it } from 'vitest';
import { personalMemoryEvidenceLabel } from './answerEvidence';

describe('personalMemoryEvidenceLabel', () => {
  it('distinguishes my memory from a family member shared memory', () => {
    expect(personalMemoryEvidenceLabel({
      id: 'personal-1',
      sourceType: 'PERSONAL_MEMORY',
      title: 'Mine',
      snippet: '',
      author: {
        name: '我',
        relationshipToViewer: '本人',
        currentViewer: true,
        currentTarget: false,
      },
    })).toBe('我的个人记忆');

    expect(personalMemoryEvidenceLabel({
      id: 'personal-2',
      sourceType: 'PERSONAL_MEMORY',
      title: 'Shared',
      snippet: '',
      author: {
        name: '哥哥',
        relationshipToViewer: '哥哥',
        currentViewer: false,
        currentTarget: false,
      },
    })).toBe('家人分享给我的');

    expect(personalMemoryEvidenceLabel({
      id: 'personal-legacy',
      sourceType: 'PERSONAL_MEMORY',
      title: 'Legacy',
      snippet: '',
    })).toBe('个人记忆');
  });
});
