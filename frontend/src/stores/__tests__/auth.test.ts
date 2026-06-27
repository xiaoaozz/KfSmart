import { describe, it, expect, beforeEach } from 'vitest'
import { useAuthStore } from '../auth'

beforeEach(() => {
  useAuthStore.setState({ token: null, refreshToken: null })
  localStorage.clear()
})

describe('useAuthStore', () => {
  it('has null tokens initially', () => {
    const { token, refreshToken } = useAuthStore.getState()
    expect(token).toBeNull()
    expect(refreshToken).toBeNull()
  })

  it('setTokens stores both tokens', () => {
    useAuthStore.getState().setTokens('access_123', 'refresh_456')
    const { token, refreshToken } = useAuthStore.getState()
    expect(token).toBe('access_123')
    expect(refreshToken).toBe('refresh_456')
  })

  it('clearTokens resets both tokens to null', () => {
    useAuthStore.getState().setTokens('access_123', 'refresh_456')
    useAuthStore.getState().clearTokens()
    const { token, refreshToken } = useAuthStore.getState()
    expect(token).toBeNull()
    expect(refreshToken).toBeNull()
  })

  it('setTokens overwrites previously stored tokens', () => {
    useAuthStore.getState().setTokens('old_access', 'old_refresh')
    useAuthStore.getState().setTokens('new_access', 'new_refresh')
    expect(useAuthStore.getState().token).toBe('new_access')
    expect(useAuthStore.getState().refreshToken).toBe('new_refresh')
  })
})
