import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import PermissionButton from '../PermissionButton'
import { usePermission } from '@/hooks/usePermission'

vi.mock('@/hooks/usePermission')

const mockedUsePermission = vi.mocked(usePermission)

function makePermHook(allowed: boolean) {
  return () => ({
    hasPermission: (_p: string) => allowed,
    isAdmin: false,
    isAuthenticated: true,
    user: undefined,
  })
}

beforeEach(() => {
  mockedUsePermission.mockImplementation(makePermHook(true))
})

describe('PermissionButton', () => {
  describe('when user has permission', () => {
    it('renders child element', () => {
      render(
        <PermissionButton permission="kb:write">
          <button>Upload</button>
        </PermissionButton>,
      )
      expect(screen.getByRole('button', { name: 'Upload' })).toBeInTheDocument()
    })

    it('renders children without any wrapping span', () => {
      const { container } = render(
        <PermissionButton permission="kb:write">
          <span>content</span>
        </PermissionButton>,
      )
      // No extra disabled wrapper
      expect(container.querySelector('span[style*="not-allowed"]')).toBeNull()
    })
  })

  describe('when user does NOT have permission', () => {
    beforeEach(() => {
      mockedUsePermission.mockImplementation(makePermHook(false))
    })

    it('hides children when mode=hide', () => {
      render(
        <PermissionButton permission="kb:write" mode="hide">
          <button>Upload</button>
        </PermissionButton>,
      )
      expect(screen.queryByRole('button', { name: 'Upload' })).not.toBeInTheDocument()
    })

    it('still renders children in disable mode (default)', () => {
      render(
        <PermissionButton permission="kb:write">
          <button>Upload</button>
        </PermissionButton>,
      )
      expect(screen.getByRole('button', { name: 'Upload' })).toBeInTheDocument()
    })

    it('wraps children in a not-allowed span in disable mode', () => {
      const { container } = render(
        <PermissionButton permission="kb:write">
          <button>Upload</button>
        </PermissionButton>,
      )
      const disabledWrapper = container.querySelector('span[style]')
      expect(disabledWrapper).toBeTruthy()
      expect(disabledWrapper!.getAttribute('style')).toContain('not-allowed')
    })

    it('renders with reduced opacity in disable mode', () => {
      const { container } = render(
        <PermissionButton permission="kb:write">
          <button>Upload</button>
        </PermissionButton>,
      )
      const disabledWrapper = container.querySelector('span[style]')
      expect(disabledWrapper!.getAttribute('style')).toContain('0.5')
    })
  })
})
