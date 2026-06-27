import { create } from 'zustand'

interface UiState {
  loadingOverlay: boolean
  showLoadingOverlay: () => void
  hideLoadingOverlay: () => void
}

export const useUiStore = create<UiState>()((set) => ({
  loadingOverlay: false,
  showLoadingOverlay: () => set({ loadingOverlay: true }),
  hideLoadingOverlay: () => set({ loadingOverlay: false }),
}))
