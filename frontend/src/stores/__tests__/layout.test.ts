import { describe, it, expect, beforeEach } from 'vitest'
import { useLayoutStore } from '../layout'

beforeEach(() => {
  useLayoutStore.setState({ siderCollapsed: false })
  localStorage.clear()
})

describe('useLayoutStore', () => {
  it('sider is expanded by default', () => {
    expect(useLayoutStore.getState().siderCollapsed).toBe(false)
  })

  it('toggleSider collapses the sider', () => {
    useLayoutStore.getState().toggleSider()
    expect(useLayoutStore.getState().siderCollapsed).toBe(true)
  })

  it('toggleSider expands a collapsed sider', () => {
    useLayoutStore.setState({ siderCollapsed: true })
    useLayoutStore.getState().toggleSider()
    expect(useLayoutStore.getState().siderCollapsed).toBe(false)
  })

  it('setSiderCollapsed sets explicitly to collapsed', () => {
    useLayoutStore.getState().setSiderCollapsed(true)
    expect(useLayoutStore.getState().siderCollapsed).toBe(true)
  })

  it('setSiderCollapsed(false) expands explicitly', () => {
    useLayoutStore.setState({ siderCollapsed: true })
    useLayoutStore.getState().setSiderCollapsed(false)
    expect(useLayoutStore.getState().siderCollapsed).toBe(false)
  })
})
