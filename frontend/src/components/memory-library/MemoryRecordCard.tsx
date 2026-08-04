import type { ReactNode } from 'react';
import { cn } from '@/lib/utils';

interface MemoryRecordCardProps {
  header: ReactNode;
  title: ReactNode;
  body: ReactNode;
  badges?: ReactNode;
  actions?: ReactNode;
  className?: string;
}

interface MemoryRecordHeaderProps {
  relationshipLabel: string;
  memberName: string;
  typeLabel: string;
  createdAt?: string;
}

export default function MemoryRecordCard({
  header,
  title,
  body,
  badges,
  actions,
  className,
}: MemoryRecordCardProps) {
  return (
    <article
      className={cn(
        'relative rounded-lg bg-white px-6 py-6 shadow-sm ring-1 ring-stone-100 transition hover:-translate-y-0.5 hover:shadow-md sm:px-9 sm:py-8',
        className,
      )}
    >
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2 text-sm text-stone-400">{header}</div>
          {badges ? <div className="mt-4 flex flex-wrap gap-2">{badges}</div> : null}
        </div>
        {actions}
      </div>
      <h2 className="mt-8 text-2xl font-semibold leading-snug text-stone-950">{title}</h2>
      <div className="mt-5 whitespace-pre-wrap text-base leading-8 text-stone-800 sm:text-xl sm:leading-10">
        {body}
      </div>
    </article>
  );
}

export function formatMemoryDateTime(value?: string) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function MemoryRecordHeader({
  relationshipLabel,
  memberName,
  typeLabel,
  createdAt,
}: MemoryRecordHeaderProps) {
  return (
    <>
      <span className="rounded-full bg-sky-100 px-2.5 py-1 font-medium text-sky-800">
        {relationshipLabel}
      </span>
      <span className="font-medium text-sky-800">{memberName}</span>
      <span>·</span>
      <span className="text-sky-800">{typeLabel}</span>
      <span>{formatMemoryDateTime(createdAt)}</span>
    </>
  );
}
