import { describe, it, expect, beforeEach } from 'vitest'
import { useThemeStore } from '../theme'

beforeEach(() => {
  useThemeStore.setState({ themeId: 'light', isDark: false })
  localStorage.clear()
})

describe('useThemeStore', () => {
  it('starts with light theme by default', () => {
    expect(useThemeStore.getState().themeId).toBe('light')
    expect(useThemeStore.getState().isDark).toBe(false)
  })

  it('setTheme switches to dark', () => {
    useThemeStore.getState().setTheme('dark')
    expect(useThemeStore.getState().themeId).toBe('dark')
    expect(useThemeStore.getState().isDark).toBe(true)
  })

  it('setTheme switches to earth', () => {
    useThemeStore.getState().setTheme('earth')
    expect(useThemeStore.getState().themeId).toBe('earth')
    expect(useThemeStore.getState().isDark).toBe(false)
  })

  it('toggle cycles through all themes and wraps back to light', () => {
    const s = useThemeStore.getState()
    s.toggle()
    expect(useThemeStore.getState().themeId).toBe('dark')
    s.toggle()
    expect(useThemeStore.getState().themeId).toBe('earth')
    s.toggle()
    expect(useThemeStore.getState().themeId).toBe('corporate')
    s.toggle()
    expect(useThemeStore.getState().themeId).toBe('neo')
    s.toggle()
    expect(useThemeStore.getState().themeId).toBe('mono')
    s.toggle()
    expect(useThemeStore.getState().themeId).toBe('terminal')
    s.toggle()
    expect(useThemeStore.getState().themeId).toBe('light')
  })

  it('terminal theme is treated as dark', () => {
    useThemeStore.getState().setTheme('terminal')
    expect(useThemeStore.getState().isDark).toBe(true)
  })

  it('mono theme is treated as light', () => {
    useThemeStore.getState().setTheme('mono')
    expect(useThemeStore.getState().isDark).toBe(false)
  })

  it('setDark(true) sets dark theme', () => {
    useThemeStore.getState().setDark(true)
    expect(useThemeStore.getState().themeId).toBe('dark')
    expect(useThemeStore.getState().isDark).toBe(true)
  })

  it('setDark(false) restores light theme', () => {
    useThemeStore.setState({ themeId: 'dark', isDark: true })
    useThemeStore.getState().setDark(false)
    expect(useThemeStore.getState().themeId).toBe('light')
    expect(useThemeStore.getState().isDark).toBe(false)
  })
})
