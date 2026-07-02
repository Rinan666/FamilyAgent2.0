import type { FamilyMember } from '@/types';

const avatarToneClasses = [
  'from-emerald-700 to-teal-500 text-white',
  'from-sky-700 to-cyan-500 text-white',
  'from-violet-700 to-fuchsia-500 text-white',
  'from-amber-600 to-orange-500 text-white',
  'from-rose-700 to-pink-500 text-white',
  'from-stone-700 to-stone-500 text-white',
];

export function memberAccountName(member: FamilyMember) {
  return member.nickname?.trim() || member.username?.trim() || `用户 ${member.userId}`;
}

export function memberDisplayName(member: FamilyMember) {
  return member.relationshipLabel?.trim() || memberAccountName(member);
}

export function memberBirthDate(member: FamilyMember) {
  const value = member.birthDate
    || (typeof member.metadata?.birthDate === 'string' ? member.metadata.birthDate : '')
    || (typeof member.metadata?.birthday === 'string' ? member.metadata.birthday : '')
    || (typeof member.metadata?.dateOfBirth === 'string' ? member.metadata.dateOfBirth : '');
  return value ? value.slice(0, 10) : '';
}

export function memberAge(member: FamilyMember) {
  const birthDate = memberBirthDate(member);
  if (birthDate) {
    const date = new Date(birthDate);
    if (!Number.isNaN(date.getTime())) {
      const now = new Date();
      let age = now.getFullYear() - date.getFullYear();
      const monthDelta = now.getMonth() - date.getMonth();
      if (monthDelta < 0 || (monthDelta === 0 && now.getDate() < date.getDate())) age -= 1;
      if (age >= 0 && age <= 130) return age;
    }
  }

  const year = Number(member.birthYear || member.metadata?.birthYear || member.metadata?.yearOfBirth);
  if (Number.isFinite(year) && year > 1870 && year <= new Date().getFullYear()) {
    return new Date().getFullYear() - year;
  }

  return null;
}

export function memberProfileSummary(member: FamilyMember) {
  const birthDate = memberBirthDate(member);
  const age = memberAge(member);
  const birthdayText = birthDate ? `${birthDate.slice(5, 7)}月${birthDate.slice(8, 10)}日` : '';
  const ageText = age == null ? '' : `${age} 岁`;
  return [ageText, birthdayText].filter(Boolean).join(' · ') || '资料待完善';
}

export function memberInitial(member: FamilyMember) {
  return memberDisplayName(member).charAt(0).toUpperCase();
}

export function memberAvatarTone(member: FamilyMember) {
  const seed = memberDisplayName(member)
    .split('')
    .reduce((sum, char) => sum + char.charCodeAt(0), member.userId);
  return avatarToneClasses[Math.abs(seed) % avatarToneClasses.length];
}
