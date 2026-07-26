import { normalizeFamilyMembers, request } from './shared';
import type { CareAuthorization, CreatePersonaMemberRequest, DeleteFamilyRequest, Family, FamilyCreationQuota, FamilyMember, FamilyRelationship, PersonaMaterial, PersonaMember, UpdatePersonaMemberRequest, UpsertPersonaMaterialRequest } from '@/types';

export const familyApi = {
  create: (data: { name: string; description?: string }) =>
    request<Family>('/families', { method: 'POST', body: JSON.stringify(data) }),
  join: (inviteCode: string) =>
    request<FamilyMember>(`/families/join?inviteCode=${inviteCode}`, { method: 'POST' }),
  getMyFamilies: () => request<Family[]>('/families/my'),
  getCreationQuota: () => request<FamilyCreationQuota>('/families/creation-quota'),
  getFamily: (id: number) => request<Family>(`/families/${id}`),
  deleteFamily: (familyId: number, data: DeleteFamilyRequest) =>
    request<void>(`/families/${familyId}`, {
      method: 'DELETE',
      body: JSON.stringify(data),
    }),
  getMembers: (familyId: number) =>
    request<FamilyMember[]>(`/families/${familyId}/members`).then(normalizeFamilyMembers),
  getMyRelationshipLabels: (familyId: number) =>
    request<FamilyRelationship[]>(`/families/${familyId}/relationships/my-labels`),
  upsertRelationshipLabel: (
    familyId: number,
    targetUserId: number,
    data: { label: string; reverseLabel?: string; note?: string },
  ) =>
    request<FamilyRelationship>(`/families/${familyId}/members/${targetUserId}/relationship`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),
  getMyCareAuthorizations: (familyId: number) =>
    request<CareAuthorization[]>(`/families/${familyId}/care-authorizations/my`),
  upsertCareAuthorization: (
    familyId: number,
    subjectUserId: number,
    caregiverUserId: number,
    data: { scope?: 'ALL' | 'DIARY' | 'MEMORY' | 'GROWTH_GUARD'; active?: boolean; expiresAt?: string },
  ) =>
    request<CareAuthorization>(`/families/${familyId}/members/${subjectUserId}/caregivers/${caregiverUserId}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),
  updateMemberRole: (familyId: number, userId: number, role: FamilyMember['role']) =>
    request<FamilyMember>(`/families/${familyId}/members/${userId}/role?role=${encodeURIComponent(role)}`, {
      method: 'PUT',
    }),
  transferOwner: (familyId: number, targetUserId: number) =>
    request<void>(`/families/${familyId}/owner/${targetUserId}`, {
      method: 'PUT',
    }),
  listPersonaMembers: (familyId: number) =>
    request<PersonaMember[]>(`/families/${familyId}/persona-members`),
  getPersonaMember: (familyId: number, personaId: number) =>
    request<PersonaMember>(`/families/${familyId}/persona-members/${personaId}`),
  createPersonaMember: (familyId: number, data: CreatePersonaMemberRequest) =>
    request<PersonaMember>(`/families/${familyId}/persona-members`, {
      method: 'POST',
      body: JSON.stringify(data),
    }),
  updatePersonaMember: (familyId: number, personaId: number, data: UpdatePersonaMemberRequest) =>
    request<PersonaMember>(`/families/${familyId}/persona-members/${personaId}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),
  deletePersonaMember: (familyId: number, personaId: number, confirmationWord: string) =>
    request<void>(`/families/${familyId}/persona-members/${personaId}`, {
      method: 'DELETE',
      body: JSON.stringify({ confirmationWord }),
    }),
  listPersonaMaterials: (familyId: number, personaId: number) =>
    request<PersonaMaterial[]>(`/families/${familyId}/persona-members/${personaId}/materials`),
  createPersonaMaterial: (familyId: number, personaId: number, data: UpsertPersonaMaterialRequest) =>
    request<PersonaMaterial>(`/families/${familyId}/persona-members/${personaId}/materials`, {
      method: 'POST',
      body: JSON.stringify(data),
    }),
  updatePersonaMaterial: (familyId: number, personaId: number, materialId: number, data: UpsertPersonaMaterialRequest) =>
    request<PersonaMaterial>(`/families/${familyId}/persona-members/${personaId}/materials/${materialId}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),
  deletePersonaMaterial: (familyId: number, personaId: number, materialId: number) =>
    request<void>(`/families/${familyId}/persona-members/${personaId}/materials/${materialId}`, {
      method: 'DELETE',
    }),
};
