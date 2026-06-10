import { request } from './shared';
import type { CreateHeritageTaskRequest, HeritageTask } from '@/types';

export const heritageTaskApi = {
  listFamilyTasks: (familyId: number, limit = 20) =>
    request<HeritageTask[]>(`/heritage-tasks/family/${familyId}?limit=${limit}`),
  create: (data: CreateHeritageTaskRequest) =>
    request<HeritageTask>('/heritage-tasks', { method: 'POST', body: JSON.stringify(data) }),
  complete: (id: number, completionNote: string) =>
    request<HeritageTask>(`/heritage-tasks/${id}/complete`, {
      method: 'PATCH',
      body: JSON.stringify({ completionNote }),
    }),
};
