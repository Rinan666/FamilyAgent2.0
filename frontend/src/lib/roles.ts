import type { FamilyMember, User } from '@/types';

export type ViewerRole = 'STUDENT' | 'PARENT' | 'ADMIN';

export function isPlatformAdmin(user?: User | null) {
  return (user?.role || '').toUpperCase() === 'ADMIN';
}

export function familyRoleLabel(role?: string) {
  switch ((role || '').toUpperCase()) {
    case 'OWNER':
      return '创建者';
    case 'ADMIN':
      return '管理员';
    case 'GUARDIAN':
      return '照护者';
    case 'STUDENT':
      return '学习者';
    case 'GUEST':
      return '访客';
    default:
      return '成员';
  }
}

export function viewerRoleLabel(role: ViewerRole) {
  if (role === 'ADMIN') return '管理员视图';
  if (role === 'PARENT') return '照护者视图';
  return '学生视图';
}

export function deriveViewerRole(
  user?: User | null,
  memberships: FamilyMember[] = [],
  activeFamilyId?: number | null,
): ViewerRole {
  const activeMembership = activeFamilyId
    ? memberships.find((member) => member.familyId === activeFamilyId)
    : memberships[0];
  const role = (activeMembership?.role || '').toUpperCase();
  if (role === 'OWNER' || role === 'ADMIN' || role === 'GUARDIAN') return 'PARENT';
  if (role === 'STUDENT' || role === 'MEMBER' || role === 'GUEST') return 'STUDENT';
  if (isPlatformAdmin(user)) return 'ADMIN';
  return 'STUDENT';
}

export function canViewParentReports(role: ViewerRole) {
  return role === 'PARENT' || role === 'ADMIN';
}

export function canMaintainSystem(role: ViewerRole) {
  return role === 'ADMIN';
}
