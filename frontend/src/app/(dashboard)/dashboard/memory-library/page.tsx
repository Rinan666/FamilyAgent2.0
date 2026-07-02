'use client';

import { useCallback, useEffect, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { Plus, X } from 'lucide-react';
import DiaryComposer from '@/components/diary/DiaryComposer';
import { MobilePageDrawer } from '@/components/layout/Sidebar';
import MemoryLibraryWorkbench from '@/components/memory-library/MemoryLibraryWorkbench';
import type { MemoryLibraryItem } from '@/types';

export default function MemoryLibraryPage() {
  const searchParams = useSearchParams();
  const [composerOpen, setComposerOpen] = useState(false);
  const [editingItem, setEditingItem] = useState<MemoryLibraryItem | null>(null);
  const [refreshSignal, setRefreshSignal] = useState(0);

  const handleSaved = useCallback(() => {
    setComposerOpen(false);
    setEditingItem(null);
    setRefreshSignal((current) => current + 1);
  }, []);

  const closeComposer = useCallback(() => {
    setComposerOpen(false);
    setEditingItem(null);
  }, []);

  useEffect(() => {
    if (searchParams.get('compose') === '1') {
      setEditingItem(null);
      setComposerOpen(true);
    }
  }, [searchParams]);

  return (
    <div className="space-y-3">
      <div className="lg:hidden">
        <MobilePageDrawer showLibrarySearch />
      </div>

      <MemoryLibraryWorkbench
        refreshSignal={refreshSignal}
        onEditEntry={(item) => {
          setEditingItem(item);
          setComposerOpen(true);
        }}
      />

      <button
        type="button"
        onClick={() => {
          setEditingItem(null);
          setComposerOpen(true);
        }}
        className="fixed bottom-[calc(1.5rem+env(safe-area-inset-bottom))] left-1/2 z-40 inline-flex h-14 w-14 -translate-x-1/2 items-center justify-center rounded-full bg-stone-950 text-white shadow-2xl shadow-stone-900/25 transition hover:-translate-y-0.5 hover:bg-stone-800 focus:outline-none focus:ring-2 focus:ring-stone-400 focus:ring-offset-2"
        aria-label="新建记忆"
      >
        <Plus className="h-6 w-6" />
      </button>

      {composerOpen && (
        <div className="fixed inset-0 z-50 bg-stone-950/35 p-3 backdrop-blur-sm sm:p-5">
          <button
            type="button"
            className="absolute inset-0"
            aria-label="关闭记忆编辑"
            onClick={closeComposer}
          />
          <section className="relative mx-auto flex h-full max-w-6xl flex-col overflow-hidden rounded-md border border-white/60 bg-stone-50 shadow-2xl">
            <div className="flex h-12 shrink-0 items-center justify-between border-b border-stone-200 bg-white px-4">
              <h2 className="text-sm font-semibold text-stone-950">{editingItem ? '编辑记忆' : '新建记忆'}</h2>
              <button
                type="button"
                onClick={closeComposer}
                className="inline-flex h-8 w-8 items-center justify-center rounded-md text-stone-500 transition hover:bg-stone-100 hover:text-stone-950"
                aria-label="关闭记忆编辑"
              >
                <X className="h-4 w-4" />
              </button>
            </div>
            <div className="min-h-0 flex-1 overflow-y-auto p-3 sm:p-4">
              <DiaryComposer editItem={editingItem} onSaved={handleSaved} />
            </div>
          </section>
        </div>
      )}
    </div>
  );
}
