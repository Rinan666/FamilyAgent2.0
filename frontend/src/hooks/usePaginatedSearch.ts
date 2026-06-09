'use client';

import { useDeferredValue, useMemo } from 'react';

type UsePaginatedSearchOptions<T> = {
  items: T[];
  query: string;
  page: number;
  pageSize: number;
  getSearchText: (item: T) => string;
};

function normalizeSearchText(value: string) {
  return value.trim().toLocaleLowerCase();
}

export function usePaginatedSearch<T>({
  items,
  query,
  page,
  pageSize,
  getSearchText,
}: UsePaginatedSearchOptions<T>) {
  const deferredQuery = useDeferredValue(query);
  const normalizedQuery = normalizeSearchText(deferredQuery);

  const filteredItems = useMemo(() => {
    if (!normalizedQuery) return items;
    return items.filter((item) => normalizeSearchText(getSearchText(item)).includes(normalizedQuery));
  }, [getSearchText, items, normalizedQuery]);

  const total = filteredItems.length;
  const pageCount = Math.max(1, Math.ceil(total / pageSize));
  const currentPage = Math.min(Math.max(page, 1), pageCount);
  const startIndex = total === 0 ? 0 : (currentPage - 1) * pageSize + 1;
  const endIndex = total === 0 ? 0 : Math.min(currentPage * pageSize, total);

  const pagedItems = useMemo(() => {
    const start = (currentPage - 1) * pageSize;
    return filteredItems.slice(start, start + pageSize);
  }, [currentPage, filteredItems, pageSize]);

  return {
    items: pagedItems,
    filteredItems,
    total,
    pageCount,
    currentPage,
    startIndex,
    endIndex,
    hasQuery: normalizedQuery.length > 0,
    normalizedQuery,
  };
}
