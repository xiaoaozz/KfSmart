import { create } from 'zustand'
import { persist } from 'zustand/middleware'

interface LayoutState {
  siderCollapsed: boolean
  toggleSider: () => void
  setSiderCollapsed: (v: boolean) => void
}

export const useLayoutStore = create<LayoutState>()(
  persist(
    (set) => ({
      siderCollapsed: false,
      toggleSider: () => set((s) => ({ siderCollapsed: !s.siderCollapsed })),
      setSiderCollapsed: (v) => set({ siderCollapsed: v }),
    }),
    { name: 'kf-layout' },
  ),
)
