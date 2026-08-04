import type { ReactNode } from 'react';
import { cn } from '@/lib/utils';

type WorkbenchTone = 'neutral' | 'accent' | 'danger' | 'warning' | 'success';

const toneClassNames: Record<WorkbenchTone, string> = {
  neutral: 'border-stone-200/70 bg-white/55 text-stone-700 backdrop-blur',
  accent: 'border-sky-100/80 bg-sky-50/75 text-sky-700 backdrop-blur',
  danger: 'border-red-100/80 bg-red-50/75 text-red-700 backdrop-blur',
  warning: 'border-amber-100/80 bg-amber-50/75 text-amber-800 backdrop-blur',
  success: 'border-sky-100/80 bg-sky-50/75 text-sky-800 backdrop-blur',
};

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
        'glass-panel rounded-[24px] p-4 sm:p-5',
        className,
      )}
    >
      {children}
    </section>
  );
}

export function WorkbenchBadge({
  children,
  icon,
  tone = 'accent',
  className,
}: {
  children: ReactNode;
  icon?: ReactNode;
  tone?: WorkbenchTone;
  className?: string;
}) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-2 rounded-full border px-2.5 py-1 text-xs font-medium',
        toneClassNames[tone],
        className,
      )}
    >
      {icon}
      {children}
    </span>
  );
}

export function WorkbenchButton({
  children,
  variant = 'primary',
  size = 'md',
  className,
  ...props
}: React.ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger';
  size?: 'sm' | 'md' | 'icon';
}) {
  return (
    <button
      type="button"
      className={cn(
        'inline-flex items-center justify-center gap-2 rounded-full text-sm font-medium transition disabled:cursor-not-allowed disabled:opacity-50',
        size === 'sm' && 'h-8 px-3 text-xs',
        size === 'md' && 'h-10 px-4',
        size === 'icon' && 'h-9 w-9 p-0',
        variant === 'primary' && 'bg-gradient-to-r from-sky-700 to-blue-600 text-white shadow-[0_10px_24px_rgba(14,165,233,0.2)] hover:from-sky-600 hover:to-blue-500',
        variant === 'secondary' && 'glass-control text-stone-700 hover:text-stone-950',
        variant === 'ghost' && 'text-stone-600 hover:bg-white/70 hover:text-stone-950',
        variant === 'danger' && 'bg-red-600 text-white hover:bg-red-700',
        className,
      )}
      {...props}
    >
      {children}
    </button>
  );
}

export function WorkbenchAlert({
  children,
  tone = 'neutral',
  className,
}: {
  children: ReactNode;
  tone?: WorkbenchTone;
  className?: string;
}) {
  return (
    <div className={cn('rounded-md border px-4 py-3 text-sm', toneClassNames[tone], className)}>
      {children}
    </div>
  );
}

export const workbenchInputClassName =
  'glass-control h-10 w-full rounded-2xl px-3 text-sm text-stone-900 outline-none transition placeholder:text-stone-400 focus:border-sky-500 focus:bg-white/90 focus:ring-2 focus:ring-sky-100 disabled:cursor-not-allowed disabled:bg-stone-100 disabled:text-stone-500';

export const workbenchTextareaClassName =
  'glass-control w-full resize-none rounded-2xl px-4 py-3 text-sm leading-7 text-stone-800 outline-none transition placeholder:text-stone-400 focus:border-sky-500 focus:bg-white/90 focus:ring-2 focus:ring-sky-100 disabled:cursor-not-allowed disabled:bg-stone-100 disabled:text-stone-500';

export function WorkbenchHero({
  badge,
  title,
  description,
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
    <div className={cn('flex flex-col gap-3 px-1 py-2 lg:flex-row lg:items-end lg:justify-between', className)}>
      <div className="min-w-0 space-y-2">
        {badge ? <div>{badge}</div> : null}
        <h1 className="truncate text-xl font-semibold text-stone-950 sm:text-2xl">{title}</h1>
        {description ? <p className="max-w-3xl text-sm leading-6 text-stone-500">{description}</p> : null}
        {actions ? <div className="flex flex-wrap gap-2">{actions}</div> : null}
      </div>
      {aside ? <div className="w-full max-w-md lg:max-w-sm">{aside}</div> : null}
    </div>
  );
}

export function WorkbenchSectionTitle({
  title,
  description,
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
        {description ? <p className="mt-1 max-w-3xl text-sm leading-6 text-stone-500">{description}</p> : null}
      </div>
      {action ? <div className="shrink-0">{action}</div> : null}
    </div>
  );
}

export function WorkbenchEmptyState({
  icon,
  title,
  description,
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
        <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-md bg-sky-100 text-sky-800">
          {icon}
        </div>
      ) : null}
      <h1 className="mt-4 text-lg font-semibold text-stone-950">{title}</h1>
      {description ? <p className="mx-auto mt-2 max-w-2xl text-sm leading-6 text-stone-500">{description}</p> : null}
      {action ? <div className="mt-6 flex justify-center">{action}</div> : null}
    </WorkbenchSurface>
  );
}

export function WorkbenchTabs<T extends string>({
  items,
  value,
  onChange,
  className,
}: {
  items: readonly { value: T; label: ReactNode; icon?: ReactNode }[];
  value: T;
  onChange: (value: T) => void;
  className?: string;
}) {
  return (
    <WorkbenchSurface className={cn('grid gap-1.5 p-2', className)}>
      {items.map((item) => {
        const active = item.value === value;
        return (
          <button
            key={item.value}
            type="button"
            onClick={() => onChange(item.value)}
            className={cn(
              'inline-flex h-10 min-w-0 items-center justify-center gap-2 rounded-md px-3 text-sm font-medium transition',
              active ? 'bg-stone-950 text-white' : 'bg-stone-50 text-stone-600 hover:bg-stone-100 hover:text-stone-950',
            )}
          >
            {item.icon}
            <span className="truncate">{item.label}</span>
          </button>
        );
      })}
    </WorkbenchSurface>
  );
}
