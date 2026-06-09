'use client';

import { ChevronLeft, ChevronRight, Search } from 'lucide-react';

type SearchPaginationControlsProps = {
  searchValue: string;
  onSearchChange: (value: string) => void;
  searchPlaceholder: string;
  itemLabel: string;
  currentPage: number;
  pageCount: number;
  onPageChange: (page: number) => void;
  startIndex: number;
  endIndex: number;
  filteredTotal: number;
  total: number;
  className?: string;
};

export default function SearchPaginationControls({
  searchValue,
  onSearchChange,
  searchPlaceholder,
  itemLabel,
  currentPage,
  pageCount,
  onPageChange,
  startIndex,
  endIndex,
  filteredTotal,
  total,
  className = '',
}: SearchPaginationControlsProps) {
  const hasFilter = searchValue.trim().length > 0;
  const rangeText = filteredTotal === 0 ? `0 ${itemLabel}` : `${startIndex}-${endIndex} / ${filteredTotal} ${itemLabel}`;
  const totalText = hasFilter ? `, total ${total} ${itemLabel}` : '';

  return (
    <div className={`flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between ${className}`.trim()}>
      <label className="relative block w-full sm:max-w-sm">
        <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" />
        <input
          value={searchValue}
          onChange={(event) => onSearchChange(event.target.value)}
          placeholder={searchPlaceholder}
          className="h-10 w-full rounded-lg border border-gray-200 bg-white pl-9 pr-3 text-sm text-gray-700 outline-none transition-colors focus:border-blue-300 focus:ring-2 focus:ring-blue-500/20"
        />
      </label>

      <div className="flex flex-col gap-2 sm:items-end">
        <span className="text-xs text-gray-500">
          {rangeText}
          {totalText}
        </span>
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => onPageChange(currentPage - 1)}
            disabled={currentPage <= 1}
            className="inline-flex h-8 w-8 items-center justify-center rounded-lg border border-gray-200 bg-white text-gray-500 transition-colors hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-40"
            aria-label="Previous page"
          >
            <ChevronLeft className="h-4 w-4" />
          </button>
          <span className="min-w-16 text-center text-xs text-gray-500">
            {currentPage} / {pageCount}
          </span>
          <button
            type="button"
            onClick={() => onPageChange(currentPage + 1)}
            disabled={currentPage >= pageCount}
            className="inline-flex h-8 w-8 items-center justify-center rounded-lg border border-gray-200 bg-white text-gray-500 transition-colors hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-40"
            aria-label="Next page"
          >
            <ChevronRight className="h-4 w-4" />
          </button>
        </div>
      </div>
    </div>
  );
}
