import { describe, expect, it } from 'vitest';
import type { LoginResponse } from '@/types';
import { toAuthUser } from './auth';

function response(overrides: Partial<LoginResponse> = {}): LoginResponse {
  return {
    userId: 12,
    username: 'alice',
    nickname: 'Alice',
    token: 'token-1',
    tokenName: 'Authorization',
    ...overrides,
  };
}

describe('toAuthUser', () => {
  it('maps login responses into auth store users', () => {
    expect(toAuthUser(response({
      avatarUrl: 'https://example.com/a.png',
      role: 'ADMIN',
      status: 'ACTIVE',
      birthDate: '2010-01-02',
      birthYear: '2010',
      metadata: { inviteSource: 'seed' },
    }))).toEqual({
      id: 12,
      username: 'alice',
      nickname: 'Alice',
      avatarUrl: 'https://example.com/a.png',
      role: 'ADMIN',
      status: 'ACTIVE',
      birthDate: '2010-01-02',
      birthYear: '2010',
      metadata: { inviteSource: 'seed' },
    });
  });

  it('falls back to default role and status when the backend omits them', () => {
    expect(toAuthUser(response())).toMatchObject({
      id: 12,
      role: 'USER',
      status: 'ACTIVE',
    });
  });
});
