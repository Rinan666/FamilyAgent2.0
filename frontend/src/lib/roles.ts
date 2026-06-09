import type { FamilyMember, User } from '@/types';

export type ViewerRole = 'STUDENT' | 'PARENT' | 'ADMIN';

export function isPlatformAdmin(user?: User | null) {
  return (user?.role || '').toUpperCase() === 'ADMIN';
}

export function familyRoleLabel(role?: string) {
  return (role || '').toUpperCase() === 'OWNER' ? '创建者' : '成员';
}

export function viewerRoleLabel(role: ViewerRole) {
  if (role === 'ADMIN') return '管理员视图';
  if (role === 'PARENT') return '成年人视图';
  return '成员视图';
}

export function deriveViewerRole(
  user?: User | null,
  memberships: FamilyMember[] = [],
  activeFamilyId?: number | null,
): ViewerRole {
  if (isPlatformAdmin(user)) return 'ADMIN';

  const activeMembership = activeFamilyId
    ? memberships.find((member) => member.familyId === activeFamilyId)
    : memberships[0];
  const role = (activeMembership?.role || '').toUpperCase();

  if (role === 'OWNER') return 'PARENT';
  return isMinor(activeMembership) ? 'STUDENT' : 'PARENT';
}

export function canViewParentReports(role: ViewerRole) {
  return role === 'PARENT' || role === 'ADMIN';
}

export function canMaintainSystem(role: ViewerRole) {
  return role === 'ADMIN';
}

export function memberAge(member?: FamilyMember | null) {
  if (!member) return 20;

  const metadata = member.metadata || {};
  const birthDate = [
    member.birthDate,
    metadata.birthDate,
    metadata.birthday,
    metadata.dateOfBirth,
  ].find((value): value is string => typeof value === 'string' && value.trim().length > 0);

  if (birthDate) {
    const parsed = new Date(birthDate.slice(0, 10));
    if (!Number.isNaN(parsed.getTime())) {
      const now = new Date();
      let age = now.getFullYear() - parsed.getFullYear();
      const monthDelta = now.getMonth() - parsed.getMonth();
      if (monthDelta < 0 || (monthDelta === 0 && now.getDate() < parsed.getDate())) {
        age -= 1;
      }
      if (age >= 0 && age <= 130) return age;
    }
  }

  const year = Number(member.birthYear || metadata.birthYear || metadata.yearOfBirth);
  if (Number.isFinite(year) && year > 1870 && year <= new Date().getFullYear()) {
    return new Date().getFullYear() - year;
  }

  return 20;
}

function isMinor(member?: FamilyMember | null) {
  return memberAge(member) < 18;
}
