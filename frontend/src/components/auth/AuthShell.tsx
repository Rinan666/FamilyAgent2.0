import type { ReactNode } from 'react';
import { BookHeart, type LucideIcon } from 'lucide-react';

export const authLabelClassName = 'mb-2 block text-sm font-medium text-stone-700';
export const authInputClassName =
  'h-12 w-full rounded-xl border border-stone-200 bg-white px-4 text-sm text-stone-900 outline-none transition placeholder:text-stone-400 focus:border-emerald-600 focus:ring-4 focus:ring-emerald-100';
export const authInputWithIconClassName =
  'h-12 w-full rounded-xl border border-stone-200 bg-white py-0 pl-11 pr-4 text-sm text-stone-900 outline-none transition placeholder:text-stone-400 focus:border-emerald-600 focus:ring-4 focus:ring-emerald-100';
export const authInputIconClassName =
  'pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-stone-400';
export const authPrimaryButtonClassName =
  'inline-flex h-12 w-full items-center justify-center rounded-xl bg-gradient-to-r from-emerald-800 to-teal-700 text-sm font-semibold text-white shadow-[0_14px_30px_rgba(13,148,136,0.22)] transition hover:from-emerald-700 hover:to-teal-600 hover:shadow-[0_18px_38px_rgba(13,148,136,0.28)] active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-55';

interface HighlightItem {
  icon: LucideIcon;
  title: string;
  description: string;
}

interface AuthShellProps {
  badge: string;
  heroTitle: string;
  heroDescription: string;
  highlights: readonly HighlightItem[];
  formTitle: string;
  formDescription: string;
  children: ReactNode;
  footer: ReactNode;
}

export default function AuthShell({
  badge,
  heroTitle,
  heroDescription,
  highlights,
  formTitle,
  formDescription,
  children,
  footer,
}: AuthShellProps) {
  return (
    <div className="flex min-h-dvh items-center justify-center bg-[linear-gradient(160deg,#fdfcf8_0%,#f6f1e8_45%,#edf7f1_100%)] px-4 py-6 text-stone-900 sm:px-6">
      <div className="grid w-full max-w-6xl overflow-hidden rounded-[28px] border border-white/80 bg-white/90 shadow-[0_28px_90px_rgba(24,39,32,0.14)] backdrop-blur-xl lg:grid-cols-[1.05fr_0.95fr]">
        <section className="hidden min-h-[680px] flex-col justify-between border-r border-stone-200/70 bg-[linear-gradient(155deg,rgba(253,252,248,0.98),rgba(245,240,232,0.96)_58%,rgba(237,247,241,0.94))] p-10 lg:flex xl:p-12">
          <div>
            <span className="inline-flex items-center rounded-full border border-emerald-200/80 bg-white/75 px-3 py-1 text-xs font-semibold tracking-[0.16em] text-emerald-800 shadow-sm">
              {badge}
            </span>
            <h1 className="mt-8 max-w-lg text-4xl font-semibold leading-tight text-stone-950">
              {heroTitle}
            </h1>
            <p className="mt-5 max-w-xl text-base leading-8 text-stone-600">
              {heroDescription}
            </p>
          </div>

          <div className="space-y-4">
            {highlights.map((item) => {
              const Icon = item.icon;
              return (
                <div
                  key={item.title}
                  className="rounded-2xl border border-white/80 bg-white/75 p-4 shadow-[0_14px_30px_rgba(24,39,32,0.06)] transition duration-200 hover:-translate-y-1 hover:bg-white/90 hover:shadow-[0_18px_40px_rgba(24,39,32,0.1)]"
                >
                  <div className="flex items-start gap-3">
                    <div className="mt-0.5 flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-emerald-100/90 text-emerald-800 ring-1 ring-emerald-200/70">
                      <Icon className="h-4 w-4" />
                    </div>
                    <div>
                      <p className="text-sm font-semibold text-stone-900">{item.title}</p>
                      <p className="mt-1 text-sm leading-6 text-stone-600">{item.description}</p>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        </section>

        <section className="flex min-h-[680px] flex-col justify-between p-6 sm:p-8 lg:p-10 xl:p-12">
          <div>
            <div className="flex items-center justify-between gap-4">
              <div className="flex items-center gap-3">
                <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-emerald-900 text-white shadow-[0_12px_24px_rgba(6,95,70,0.18)]">
                  <BookHeart className="h-5 w-5" />
                </div>
                <div>
                  <p className="text-sm font-semibold tracking-[0.14em] text-stone-900">FAMILYAGENT</p>
                  <p className="text-sm text-stone-500">家庭记忆与协作空间</p>
                </div>
              </div>
              <span className="rounded-full bg-emerald-50 px-3 py-1 text-xs font-medium text-emerald-800 ring-1 ring-emerald-100">
                {badge}
              </span>
            </div>

            <div className="mt-10 max-w-md">
              <h2 className="text-3xl font-semibold tracking-tight text-stone-950">{formTitle}</h2>
              <p className="mt-3 text-sm leading-7 text-stone-500">{formDescription}</p>
            </div>

            <div className="mt-8">{children}</div>
          </div>

          <div className="mt-8 border-t border-stone-200/70 pt-5 text-sm text-stone-500">{footer}</div>
        </section>
      </div>
    </div>
  );
}
