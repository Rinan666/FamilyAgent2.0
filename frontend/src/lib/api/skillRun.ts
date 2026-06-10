import { request } from './shared';
import type { CreateSkillRunRequest, SkillRun, UpdateSkillRunRequest } from '@/types';

export const skillRunApi = {
  create: (data: CreateSkillRunRequest) =>
    request<SkillRun>('/skill-runs', { method: 'POST', body: JSON.stringify(data) }),
  update: (id: number, data: UpdateSkillRunRequest) =>
    request<SkillRun>(`/skill-runs/${id}`, { method: 'PATCH', body: JSON.stringify(data) }),
  get: (id: number) => request<SkillRun>(`/skill-runs/${id}`),
  listFamilyRuns: (familyId: number, limit = 30) =>
    request<SkillRun[]>(`/skill-runs/family/${familyId}?limit=${limit}`),
};
