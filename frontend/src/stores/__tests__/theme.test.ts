import { describe, it, expect, beforeEach } from 'vitest'
import { useThemeStore } from '../theme'

beforeEach(() => {
  useThemeStore.setState({ isDark: false })
  localStorage.clear()
})

describe('useThemeStore', () => {
  it('starts with light mode by default', () => {
    expect(useThemeStore.getState().isDark).toBe(false)
  })

  it('toggle flips from light to dark', () => {
    useThemeStore.getState().toggle()
    expect(useThemeStore.getState().isDark).toBe(true)
  })

  it('toggle flips from dark back to light', () => {
    useThemeStore.setState({ isDark: true })
    useThemeStore.getState().toggle()
    expect(useThemeStore.getState().isDark).toBe(false)
  })

  it('setDark sets dark mode explicitly', () => {
    useThemeStore.getState().setDark(true)
    expect(useThemeStore.getState().isDark).toBe(true)
  })

  it('setDark(false) restores light mode', () => {
    useThemeStore.setState({ isDark: true })
    useThemeStore.getState().setDark(false)
    expect(useThemeStore.getState().isDark).toBe(false)
  })
})
