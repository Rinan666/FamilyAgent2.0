import type { ReactNode } from 'react';
import { cn } from '@/lib/utils';

export function WorkbenchPage({
  children,
  className,
}: {
  children: ReactNode;
  className?: string;
}) {
  return <div className={cn('mx-auto flex w-full max-w-[1600px] flex-col gap-3', className)}>{children}</div>;
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
        'rounded-md border border-stone-200 bg-white p-4 sm:p-5',
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
    <div className={cn('flex flex-col gap-3 px-1 py-1 lg:flex-row lg:items-end lg:justify-between', className)}>
      <div className="min-w-0 space-y-2">
        {badge ? <div>{badge}</div> : null}
        <h1 className="truncate text-xl font-semibold text-stone-950 sm:text-2xl">{title}</h1>
        {actions ? <div className="flex flex-wrap gap-2">{actions}</div> : null}
      </div>
      {aside ? <div className="w-full max-w-md lg:max-w-sm">{aside}</div> : null}
    </div>
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
        <h2 className="text-base font-semibold text-stone-950">{title}</h2>
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
    <WorkbenchSurface className={cn('px-6 py-8 text-center', className)}>
      {icon ? (
        <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-md bg-emerald-100 text-emerald-800">
          {icon}
        </div>
      ) : null}
      <h1 className="mt-4 text-lg font-semibold text-stone-950">{title}</h1>
      {action ? <div className="mt-6 flex justify-center">{action}</div> : null}
    </WorkbenchSurface>
  );
}
