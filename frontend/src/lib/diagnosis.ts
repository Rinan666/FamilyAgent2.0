const DIAGNOSIS_COMPLETED_PREFIX = 'familyagent:diagnosis:completed';

export function diagnosisCompletedKey(userId?: number | null): string {
  return userId ? `${DIAGNOSIS_COMPLETED_PREFIX}:${userId}` : DIAGNOSIS_COMPLETED_PREFIX;
}

export function isDiagnosisCompletedLocally(userId?: number | null): boolean {
  if (typeof window === 'undefined') return false;
  return localStorage.getItem(diagnosisCompletedKey(userId)) === '1';
}

export function markDiagnosisCompletedLocally(userId?: number | null): void {
  if (typeof window === 'undefined') return;
  localStorage.setItem(diagnosisCompletedKey(userId), '1');
}
