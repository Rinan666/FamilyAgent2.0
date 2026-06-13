'use client';

import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { HeartPulse } from 'lucide-react';
import LegacyWorkpageNotice from '@/components/family/LegacyWorkpageNotice';

interface GrowthPageProps {
  embedded?: boolean;
}

export default function GrowthPage({ embedded = false }: GrowthPageProps) {
  const searchParams = useSearchParams();
  const familyId = searchParams.get('familyId')?.trim() || '';
  const targetUserId = searchParams.get('targetUserId')?.trim() || '';
  const category = searchParams.get('category')?.trim() || '';
  const prefillTitle = searchParams.get('prefillTitle')?.trim() || '';
  const prefillContent = searchParams.get('prefillContent')?.trim() || '';
  const prefillTags = searchParams.get('prefillTags')?.trim() || '';
  const severity = searchParams.get('severity')?.trim() || '';

  const params = new URLSearchParams();
  params.set('writeCategory', 'OBSERVATION');
  if (familyId) params.set('familyId', familyId);
  if (targetUserId) params.set('relatedUserId', targetUserId);
  if (category) params.set('growthCategory', category);
  if (prefillTitle) params.set('prefillTitle', prefillTitle);
  if (prefillContent) params.set('prefillContent', prefillContent);
  if (prefillTags) params.set('prefillTags', prefillTags);
  if (severity) params.set('severity', severity);

  const targetHref = `/dashboard/diary?${params.toString()}`;

  return (
    <div className={embedded ? 'w-full' : 'mx-auto w-full max-w-3xl'}>
      {!embedded && <LegacyWorkpageNotice tab="growth" label="守护观察" />}
      <div className="rounded-2xl border border-gray-200 bg-white p-6 text-center sm:p-8">
        <HeartPulse className="mx-auto mb-4 h-10 w-10 text-emerald-500" />
        <h1 className="text-xl font-semibold text-gray-900">守护观察已并入“日记”</h1>
        <p className="mt-2 text-sm leading-6 text-gray-500">
          观察类内容现在从日记入口完成。先记录线索，再在记忆库里继续筛选、查看和整理。
        </p>
        <div className="mt-6 flex flex-col justify-center gap-3 sm:flex-row">
          <Link
            href={targetHref}
            className="inline-flex h-10 items-center justify-center rounded-lg bg-emerald-600 px-4 text-sm font-medium text-white hover:bg-emerald-700"
          >
            写观察日记
          </Link>
          <Link
            href={`/dashboard/family?tab=library${familyId ? `&familyId=${familyId}` : ''}`}
            className="inline-flex h-10 items-center justify-center rounded-lg border border-gray-200 bg-white px-4 text-sm font-medium text-gray-600 hover:bg-gray-50"
          >
            前往记忆库
          </Link>
        </div>
      </div>
    </div>
  );
}
