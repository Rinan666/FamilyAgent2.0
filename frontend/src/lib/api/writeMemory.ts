import { request } from './shared';
import type { WriteMemoryRequest, WriteMemoryResult } from '@/types';

export const writeMemoryApi = {
  create: (data: WriteMemoryRequest) =>
    request<WriteMemoryResult>('/memories/write', { method: 'POST', body: JSON.stringify(data) }),
};
