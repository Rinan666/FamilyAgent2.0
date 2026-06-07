import { create } from 'zustand';

interface FamilyContextState {
  activeFamilyId: number | null;
  hasHydrated: boolean;
  hydrate: () => void;
  setActiveFamilyId: (familyId: number | null) => void;
}

const STORAGE_KEY = 'activeFamilyId';

function readStoredFamilyId() {
  if (typeof window === 'undefined') return null;
  const raw = localStorage.getItem(STORAGE_KEY);
  if (!raw) return null;
  const id = Number(raw);
  return Number.isFinite(id) ? id : null;
}

export const useFamilyContextStore = create<FamilyContextState>((set) => ({
  activeFamilyId: null,
  hasHydrated: false,

  hydrate: () => {
    set({ activeFamilyId: readStoredFamilyId(), hasHydrated: true });
  },

  setActiveFamilyId: (familyId) => {
    if (typeof window !== 'undefined') {
      if (familyId == null) {
        localStorage.removeItem(STORAGE_KEY);
      } else {
        localStorage.setItem(STORAGE_KEY, String(familyId));
      }
    }
    set({ activeFamilyId: familyId });
  },
}));
