import { normalizeLoginResponse, normalizeUser, request } from './shared';
import type { ChangePasswordRequest, LoginRequest, LoginResponse, RegisterRequest, UpdateProfileRequest, User } from '@/types';

export const userApi = {
  register: (data: RegisterRequest) => request<User>('/users/register', { method: 'POST', body: JSON.stringify(data) }).then(normalizeUser),
  login: (data: LoginRequest) => request<LoginResponse>('/users/login', { method: 'POST', body: JSON.stringify(data) }).then(normalizeLoginResponse),
  getMe: () => request<User>('/users/me', { cache: 'no-store' }).then(normalizeUser),
  updateProfile: (data: UpdateProfileRequest) =>
    request<User>('/users/me/profile', { method: 'POST', body: JSON.stringify(data) }).then(normalizeUser),
  changePassword: (data: ChangePasswordRequest) =>
    request<void>('/users/change-password', { method: 'POST', body: JSON.stringify(data) }),
  getUser: (id: number) => request<User>(`/users/${id}`).then(normalizeUser),
};

// ============================================
// Families
// ============================================
