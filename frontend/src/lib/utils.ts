import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';

/**
 * 合并 Tailwind CSS 类名
 */
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

/**
 * 格式化日期
 */
export function formatDate(dateStr: string): string {
  const date = new Date(dateStr);
  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}

/**
 * 格式化时间间隔
 */
export function timeAgo(dateStr: string): string {
  const now = Date.now();
  const date = new Date(dateStr).getTime();
  const diff = now - date;

  const minutes = Math.floor(diff / 60000);
  const hours = Math.floor(diff / 3600000);
  const days = Math.floor(diff / 86400000);

  if (minutes < 1) return '刚刚';
  if (minutes < 60) return `${minutes}分钟前`;
  if (hours < 24) return `${hours}小时前`;
  if (days < 30) return `${days}天前`;
  return formatDate(dateStr);
}

/**
 * 掌握概率 → 颜色
 */
export function masteryColor(probability: number): string {
  if (probability < 0.3) return 'text-red-500';
  if (probability < 0.6) return 'text-yellow-500';
  if (probability < 0.85) return 'text-blue-500';
  return 'text-green-500';
}

/**
 * 掌握概率 → 等级文字
 */
export function masteryLevel(probability: number): string {
  if (probability < 0.3) return '需要加强';
  if (probability < 0.6) return '基本掌握';
  if (probability < 0.85) return '熟练掌握';
  return '精通';
}

/**
 * 难度 → 文字
 */
export function difficultyLabel(difficulty: number): string {
  const labels: Record<number, string> = {
    1: '基础',
    2: '简单',
    3: '中等',
    4: '较难',
    5: '困难',
  };
  return labels[difficulty] || '未知';
}

/**
 * 生成唯一ID
 */
export function generateId(): string {
  return Math.random().toString(36).substring(2) + Date.now().toString(36);
}
