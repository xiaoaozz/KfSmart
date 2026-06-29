import { create } from 'zustand'

const STORAGE_KEY = 'kf-auth'

function readStorage(): { token: string | null; refreshToken: string | null } {
  const raw = localStorage.getItem(STORAGE_KEY) ?? sessionStorage.getItem(STORAGE_KEY)
  if (!raw) return { token: null, refreshToken: null }
  try {
    const parsed = JSON.parse(raw)
    // Migrate old Zustand persist format: { state: { token, refreshToken }, version: 0 }
    if (parsed.state) {
      return { token: parsed.state.token ?? null, refreshToken: parsed.state.refreshToken ?? null }
    }
    return { token: parsed.token ?? null, refreshToken: parsed.refreshToken ?? null }
  } catch {
    return { token: null, refreshToken: null }
  }
}

function writeStorage(token: string, refreshToken: string, remember: boolean): void {
  const data = JSON.stringify({ token, refreshToken })
  if (remember) {
    localStorage.setItem(STORAGE_KEY, data)
    sessionStorage.removeItem(STORAGE_KEY)
  } else {
    sessionStorage.setItem(STORAGE_KEY, data)
    localStorage.removeItem(STORAGE_KEY)
  }
}

export function clearAuthStorage(): void {
  localStorage.removeItem(STORAGE_KEY)
  sessionStorage.removeItem(STORAGE_KEY)
}

// Used by http.ts to read tokens without importing the Zustand store
export function getStoredToken(): string | null {
  return readStorage().token
}

export function getStoredRefreshToken(): string | null {
  return readStorage().refreshToken
}

// Update only the access token in whichever storage is active (used by token refresh logic)
export function updateStoredToken(newToken: string): void {
  const lsRaw = localStorage.getItem(STORAGE_KEY)
  const ssRaw = sessionStorage.getItem(STORAGE_KEY)
  if (lsRaw) {
    try {
      const parsed = JSON.parse(lsRaw)
      const refreshToken = parsed.state?.refreshToken ?? parsed.refreshToken
      localStorage.setItem(STORAGE_KEY, JSON.stringify({ token: newToken, refreshToken }))
    } catch {
      /* ignore */
    }
  } else if (ssRaw) {
    try {
      const parsed = JSON.parse(ssRaw)
      const refreshToken = parsed.state?.refreshToken ?? parsed.refreshToken
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify({ token: newToken, refreshToken }))
    } catch {
      /* ignore */
    }
  }
}

interface AuthState {
  token: string | null
  refreshToken: string | null
  setTokens: (token: string, refreshToken: string, remember?: boolean) => void
  clearTokens: () => void
}

export const useAuthStore = create<AuthState>()((set) => ({
  ...readStorage(),
  setTokens: (token, refreshToken, remember = true) => {
    writeStorage(token, refreshToken, remember)
    set({ token, refreshToken })
  },
  clearTokens: () => {
    clearAuthStorage()
    set({ token: null, refreshToken: null })
  },
}))
