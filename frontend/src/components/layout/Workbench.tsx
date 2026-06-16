import type { ReactNode } from 'react';
import { cn } from '@/lib/utils';

export function WorkbenchPage({
  children,
  className,
}: {
  children: ReactNode;
  className?: string;
}) {
  return <div className={cn('mx-auto flex w-full max-w-[1440px] flex-col gap-4', className)}>{children}</div>;
}

export function WorkbenchSurface({
  children,
  className,
}: {
  children: ReactNode;
  className?: string;
}) {
  return (
    <section
      className={cn(
        'rounded-[28px] border border-white/80 bg-white/84 p-4 shadow-[0_18px_48px_rgba(24,39,32,0.08)] backdrop-blur-xl sm:p-5',
        className,
      )}
    >
      {children}
    </section>
  );
}

export function WorkbenchHero({
  badge,
  title,
  actions,
  aside,
  className,
}: {
  badge?: ReactNode;
  title: ReactNode;
  description?: ReactNode;
  actions?: ReactNode;
  aside?: ReactNode;
  className?: string;
}) {
  return (
    <WorkbenchSurface className={cn('flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between', className)}>
      <div className="min-w-0">
        {badge ? <div className="mb-3">{badge}</div> : null}
        <h1 className="text-[clamp(1.6rem,2vw,2.1rem)] font-semibold tracking-tight text-stone-950">{title}</h1>
        {actions ? <div className="mt-4 flex flex-wrap gap-2">{actions}</div> : null}
      </div>
      {aside ? <div className="w-full max-w-lg">{aside}</div> : null}
    </WorkbenchSurface>
  );
}

export function WorkbenchSectionTitle({
  title,
  action,
  className,
}: {
  title: ReactNode;
  description?: ReactNode;
  action?: ReactNode;
  className?: string;
}) {
  return (
    <div className={cn('flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between', className)}>
      <div className="min-w-0">
        <h2 className="text-lg font-semibold text-stone-950">{title}</h2>
      </div>
      {action ? <div className="shrink-0">{action}</div> : null}
    </div>
  );
}

export function WorkbenchEmptyState({
  icon,
  title,
  action,
  className,
}: {
  icon?: ReactNode;
  title: ReactNode;
  description?: ReactNode;
  action?: ReactNode;
  className?: string;
}) {
  return (
    <WorkbenchSurface className={cn('px-6 py-10 text-center', className)}>
      {icon ? (
        <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-emerald-100 text-emerald-800">
          {icon}
        </div>
      ) : null}
      <h1 className="mt-5 text-xl font-semibold text-stone-950">{title}</h1>
      {action ? <div className="mt-6 flex justify-center">{action}</div> : null}
    </WorkbenchSurface>
  );
}
