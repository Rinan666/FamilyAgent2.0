import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';

/**
 * Merge Tailwind CSS class names.
 */
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

/**
 * Format a date string.
 */
export function formatDate(dateStr: string): string {
  const date = new Date(dateStr);
  return date.toLocaleDateString('en-US', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}

/**
 * Format a relative time string.
 */
export function timeAgo(dateStr: string): string {
  const now = Date.now();
  const date = new Date(dateStr).getTime();
  const diff = now - date;

  const minutes = Math.floor(diff / 60000);
  const hours = Math.floor(diff / 3600000);
  const days = Math.floor(diff / 86400000);

  if (minutes < 1) return 'Just now';
  if (minutes < 60) return `${minutes} minute${minutes === 1 ? '' : 's'} ago`;
  if (hours < 24) return `${hours} hour${hours === 1 ? '' : 's'} ago`;
  if (days < 30) return `${days} day${days === 1 ? '' : 's'} ago`;
  return formatDate(dateStr);
}

/**
 * Map mastery probability to a color class.
 */
export function masteryColor(probability: number): string {
  if (probability < 0.3) return 'text-red-500';
  if (probability < 0.6) return 'text-yellow-500';
  if (probability < 0.85) return 'text-blue-500';
  return 'text-green-500';
}

/**
 * Map mastery probability to a user-facing level label.
 */
export function masteryLevel(probability: number): string {
  if (probability < 0.3) return 'Needs work';
  if (probability < 0.6) return 'Basic';
  if (probability < 0.85) return 'Proficient';
  return 'Mastered';
}

/**
 * Map difficulty to a display label.
 */
export function difficultyLabel(difficulty: number): string {
  const labels: Record<number, string> = {
    1: 'Basic',
    2: 'Easy',
    3: 'Medium',
    4: 'Hard',
    5: 'Very hard',
  };
  return labels[difficulty] || 'Unknown';
}

/**
 * Map subject codes to display names.
 */
export function subjectLabel(subject?: string): string {
  const labels: Record<string, string> = {
    math: 'Math',
    chinese: 'Chinese',
    english: 'English',
    science: 'Science',
    family_wisdom: 'Family wisdom',
  };
  return subject ? labels[subject] || subject : 'Unlabeled subject';
}

/**
 * Generate a lightweight unique ID.
 */
export function generateId(): string {
  return Math.random().toString(36).substring(2) + Date.now().toString(36);
}
