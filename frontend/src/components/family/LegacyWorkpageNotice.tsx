'use client';

import Link from 'next/link';
import { ArrowLeft } from 'lucide-react';

interface LegacyWorkpageNoticeProps {
  tab: 'heritage' | 'library';
  label: string;
}

export default function LegacyWorkpageNotice({ tab, label }: LegacyWorkpageNoticeProps) {
  return (
    <div className="mb-4 rounded-xl border border-blue-100 bg-blue-50 px-4 py-3 text-sm text-blue-800">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <p>这是独立工作页，对应内容已归入 家族空间 &gt; {label}。</p>
        <Link
          href={`/dashboard/family?tab=${tab}`}
          className="inline-flex h-8 items-center gap-1.5 rounded-lg bg-white px-3 text-xs font-medium text-blue-700 hover:bg-blue-100"
        >
          <ArrowLeft className="h-3.5 w-3.5" />
          返回家族空间
        </Link>
      </div>
    </div>
  );
}
