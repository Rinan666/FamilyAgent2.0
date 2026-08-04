'use client';

import { useCallback, useEffect, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { Plus, Search, X } from 'lucide-react';
import DiaryComposer from '@/components/diary/DiaryComposer';
import { MobilePageDrawer } from '@/components/layout/Sidebar';
import MemoryLibraryWorkbench from '@/components/memory-library/MemoryLibraryWorkbench';
import PersonalMemoryWorkbench from '@/components/memory-library/PersonalMemoryWorkbench';
import PersonalMemoryComposer from '@/components/memory-library/PersonalMemoryComposer';
import { useViewerRole } from '@/hooks/useViewerRole';
import type { MemoryLibraryItem } from '@/types';

export default function MemoryLibraryPage() {
  const { families, activeFamilyId } = useViewerRole();
  const router = useRouter();
  const searchParams = useSearchParams();
  const searchQuery = searchParams.get('q')?.trim() || '';
  const [composerOpen, setComposerOpen] = useState(false);
  const [editingItem, setEditingItem] = useState<MemoryLibraryItem | null>(null);
  const [refreshSignal, setRefreshSignal] = useState(0);
  const [libraryKind, setLibraryKind] = useState<'PERSONAL' | 'FAMILY'>(() =>
    searchParams.get('library') === 'family' ? 'FAMILY' : 'PERSONAL',
  );

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
      <div className="flex justify-end lg:hidden">
        <MobilePageDrawer showLibrarySearch />
      </div>

      {searchQuery ? (
        <div className="mx-auto w-full max-w-[1500px] space-y-8">
          <div className="glass-panel flex flex-col gap-3 rounded-[22px] px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
            <div className="flex min-w-0 items-center gap-3">
              <Search className="h-5 w-5 shrink-0 text-sky-600" />
              <div className="min-w-0">
                <h1 className="truncate text-lg font-semibold text-stone-950">
                  搜索“{searchQuery}”
                </h1>
              </div>
            </div>
            <button
              type="button"
              onClick={() => router.push('/dashboard/memory-library')}
              className="rounded-full border border-stone-200 bg-white/75 px-4 py-2 text-sm text-stone-600 transition hover:border-sky-200 hover:bg-sky-50 hover:text-sky-700"
            >
              清除搜索
            </button>
          </div>
          <PersonalMemoryWorkbench refreshSignal={refreshSignal} searchQuery={searchQuery} />
          <section className="border-t border-white/70 pt-7">
            <h2 className="mb-4 text-lg font-semibold text-stone-900">家庭记忆</h2>
            <MemoryLibraryWorkbench
              simplified
              searchQuery={searchQuery}
              refreshSignal={refreshSignal}
              onEditEntry={(item) => {
                setEditingItem(item);
                setComposerOpen(true);
              }}
            />
          </section>
        </div>
      ) : (
        <>
          <div className="glass-panel mx-auto flex max-w-6xl rounded-[22px] p-1.5">
            <button
              type="button"
              onClick={() => setLibraryKind('PERSONAL')}
              className={
                libraryKind === 'PERSONAL'
                  ? 'flex-1 rounded-2xl bg-gradient-to-r from-sky-700 to-blue-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm'
                  : 'flex-1 rounded-2xl px-4 py-2.5 text-sm text-stone-500 transition hover:bg-white/70'
              }
            >
              个人记忆库
            </button>
            <button
              type="button"
              onClick={() => setLibraryKind('FAMILY')}
              className={
                libraryKind === 'FAMILY'
                  ? 'flex-1 rounded-2xl bg-gradient-to-r from-sky-700 to-blue-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm'
                  : 'flex-1 rounded-2xl px-4 py-2.5 text-sm text-stone-500 transition hover:bg-white/70'
              }
            >
              家族记忆库
            </button>
          </div>
          {libraryKind === 'PERSONAL' ? (
            <PersonalMemoryWorkbench refreshSignal={refreshSignal} />
          ) : (
            <MemoryLibraryWorkbench
              refreshSignal={refreshSignal}
              onEditEntry={(item) => {
                setEditingItem(item);
                setComposerOpen(true);
              }}
            />
          )}
        </>
      )}

      <button
        type="button"
        onClick={() => {
          setEditingItem(null);
          setComposerOpen(true);
        }}
        className="fixed bottom-[calc(1.5rem+env(safe-area-inset-bottom))] left-1/2 z-40 inline-flex h-14 w-14 -translate-x-1/2 items-center justify-center rounded-full bg-gradient-to-br from-sky-700 to-blue-600 text-white shadow-[0_18px_38px_rgba(14,165,233,0.28)] transition hover:-translate-y-0.5 hover:from-sky-600 hover:to-blue-500 focus:outline-none focus:ring-2 focus:ring-sky-300 focus:ring-offset-2"
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
          <section className="glass-panel-strong relative mx-auto flex h-full max-w-6xl flex-col overflow-hidden rounded-[28px]">
            <div className="flex h-14 shrink-0 items-center justify-between border-b border-white/70 bg-white/50 px-5">
              <h2 className="text-sm font-semibold text-stone-950">
                {editingItem
                  ? '编辑记忆'
                  : libraryKind === 'PERSONAL'
                    ? '新建个人记忆'
                    : '新建家族记忆'}
              </h2>
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
              {libraryKind === 'PERSONAL' ? (
                <PersonalMemoryComposer
                  families={families}
                  activeFamilyId={activeFamilyId}
                  onSaved={handleSaved}
                />
              ) : (
                <DiaryComposer editItem={editingItem} onSaved={handleSaved} />
              )}
            </div>
          </section>
        </div>
      )}
    </div>
  );
}
