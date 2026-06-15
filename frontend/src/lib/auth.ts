import type { LoginResponse, User } from '@/types';

/**
 * Maps login responses into the persisted auth user shape.
 */
export function toAuthUser(result: LoginResponse): User {
  return {
    id: result.userId,
    username: result.username,
    nickname: result.nickname,
    avatarUrl: result.avatarUrl,
    role: result.role || 'USER',
    status: result.status || 'ACTIVE',
    birthDate: result.birthDate,
    birthYear: result.birthYear,
    metadata: result.metadata,
  };
}
