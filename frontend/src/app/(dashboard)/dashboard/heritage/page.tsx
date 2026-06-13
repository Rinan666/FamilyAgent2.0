'use client';

import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { ScrollText } from 'lucide-react';
import LegacyWorkpageNotice from '@/components/family/LegacyWorkpageNotice';

interface HeritagePageProps {
  embedded?: boolean;
}

export default function HeritagePage({ embedded = false }: HeritagePageProps) {
  const searchParams = useSearchParams();
  const familyId = searchParams.get('familyId')?.trim() || '';
  const type = searchParams.get('type')?.trim() || '';

  const params = new URLSearchParams();
  params.set('writeCategory', 'EXPERIENCE');
  if (familyId) params.set('familyId', familyId);
  if (type) params.set('memoryType', type);

  const writeHref = `/dashboard/diary?${params.toString()}`;

  return (
    <div className={embedded ? 'w-full' : 'mx-auto w-full max-w-3xl'}>
      {!embedded && <LegacyWorkpageNotice tab="heritage" label="经验沉淀" />}
      <div className="rounded-2xl border border-gray-200 bg-white p-6 text-center sm:p-8">
        <ScrollText className="mx-auto mb-4 h-10 w-10 text-amber-500" />
        <h1 className="text-xl font-semibold text-gray-900">经验沉淀已并入“日记”</h1>
        <p className="mt-2 text-sm leading-6 text-gray-500">
          经验不再单独占一个写入页面。现在统一从“日记”开始，保存后再到记忆库里继续整理和查看。
        </p>
        <div className="mt-6 flex flex-col justify-center gap-3 sm:flex-row">
          <Link
            href={writeHref}
            className="inline-flex h-10 items-center justify-center rounded-lg bg-amber-600 px-4 text-sm font-medium text-white hover:bg-amber-700"
          >
            写经验日记
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
