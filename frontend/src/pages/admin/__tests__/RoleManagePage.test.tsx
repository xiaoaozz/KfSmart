import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, act, fireEvent } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { App } from 'antd'
import RoleManagePage, { buildPermTree } from '../RoleManagePage'
import { adminRoleApi, type Permission, type Role } from '@/api/admin'

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------
vi.mock('react-i18next', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-i18next')>()
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string, opts?: Record<string, unknown>) =>
        opts ? `${key}:${JSON.stringify(opts)}` : key,
      i18n: { changeLanguage: vi.fn() },
    }),
  }
})

vi.mock('@/api/admin', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/admin')>()
  return {
    ...actual,
    adminRoleApi: {
      list: vi.fn(),
      permissions: vi.fn(),
      create: vi.fn(),
      update: vi.fn(),
      delete: vi.fn(),
    },
  }
})

const mockApi = vi.mocked(adminRoleApi)

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------
const PERMISSIONS: Permission[] = [
  { id: 1, permCode: 'user:read', permName: 'Read User', resourceType: 'user', action: 'read' },
  { id: 2, permCode: 'user:write', permName: 'Write User', resourceType: 'user', action: 'write' },
  { id: 3, permCode: 'doc:read', permName: 'Read Doc', resourceType: 'doc', action: 'read' },
]

const ROLES: Role[] = [
  {
    id: 1,
    roleCode: 'ROLE_ADMIN',
    roleName: 'Admin',
    description: 'Super admin',
    isSystem: true,
    permissions: [{ permCode: 'user:read', permName: 'Read User' }],
  },
  {
    id: 2,
    roleCode: 'ROLE_EDITOR',
    roleName: 'Editor',
    isSystem: false,
    permissions: [],
  },
]

// ---------------------------------------------------------------------------
// buildPermTree — pure function unit tests
// ---------------------------------------------------------------------------
describe('buildPermTree', () => {
  it('groups permissions by resourceType', () => {
    const tree = buildPermTree(PERMISSIONS)
    expect(tree).toHaveLength(2)

    const userGroup = tree.find((n) => n.key === 'group_user')
    expect(userGroup).toBeDefined()
    expect(userGroup!.children).toHaveLength(2)

    const docGroup = tree.find((n) => n.key === 'group_doc')
    expect(docGroup).toBeDefined()
    expect(docGroup!.children).toHaveLength(1)
  })

  it('uses permCode as the leaf node key', () => {
    const tree = buildPermTree(PERMISSIONS)
    const userGroup = tree.find((n) => n.key === 'group_user')!
    const leafKeys = userGroup.children?.map((c) => c.key)
    expect(leafKeys).toContain('user:read')
    expect(leafKeys).toContain('user:write')
  })

  it('includes permCode and permName in leaf title', () => {
    const tree = buildPermTree(PERMISSIONS)
    const userGroup = tree.find((n) => n.key === 'group_user')!
    const readLeaf = userGroup.children?.find((c) => c.key === 'user:read')
    expect(String(readLeaf?.title)).toContain('user:read')
    expect(String(readLeaf?.title)).toContain('Read User')
  })

  it('returns empty array for empty input', () => {
    expect(buildPermTree([])).toEqual([])
  })

  it('deduplicates resourceType groups', () => {
    const duped: Permission[] = [
      { id: 1, permCode: 'a:r', permName: 'A Read', resourceType: 'a', action: 'r' },
      { id: 2, permCode: 'a:w', permName: 'A Write', resourceType: 'a', action: 'w' },
    ]
    expect(buildPermTree(duped)).toHaveLength(1)
  })
})

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------
function makeClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
}

function renderPage() {
  const client = makeClient()
  return {
    client,
    ...render(
      <QueryClientProvider client={client}>
        <App>
          <RoleManagePage />
        </App>
      </QueryClientProvider>,
    ),
  }
}

beforeEach(() => {
  mockApi.list.mockResolvedValue(ROLES)
  mockApi.permissions.mockResolvedValue(PERMISSIONS)
  mockApi.create.mockResolvedValue({ code: '0', message: 'ok' })
  mockApi.update.mockResolvedValue({ code: '0', message: 'ok' })
  mockApi.delete.mockResolvedValue(undefined)
})

// ---------------------------------------------------------------------------
// RoleManagePage component tests
// ---------------------------------------------------------------------------
describe('RoleManagePage', () => {
  describe('role list rendering', () => {
    it('renders role names and codes from API', async () => {
      renderPage()
      await screen.findByText('Admin')
      expect(screen.getByText('Editor')).toBeInTheDocument()
      expect(screen.getByText('ROLE_ADMIN')).toBeInTheDocument()
      expect(screen.getByText('ROLE_EDITOR')).toBeInTheDocument()
    })

    it('shows description for roles that have one', async () => {
      renderPage()
      await screen.findByText('Super admin')
    })

    it('shows permission code tags for roles that have permissions', async () => {
      renderPage()
      await screen.findByText('user:read')
    })

    it('shows system tag for system roles', async () => {
      renderPage()
      await screen.findByText('admin.role.system')
    })

    it('disables the delete button for system roles', async () => {
      renderPage()
      await screen.findByText('Admin')

      // There are two delete danger buttons. Admin (system) should be disabled.
      const dangerBtns = screen
        .getAllByRole('button')
        .filter((b) => b.classList.contains('ant-btn-dangerous'))
      const disabledBtn = dangerBtns.find((b) => b.hasAttribute('disabled'))
      expect(disabledBtn).toBeDefined()
    })
  })

  describe('create modal', () => {
    it('opens when create button is clicked', async () => {
      renderPage()
      const btn = await screen.findByRole('button', { name: /admin\.role\.createBtn/i })
      await act(() => userEvent.click(btn))
      expect(await screen.findByText('admin.role.createTitle')).toBeInTheDocument()
    })

    it('starts with no checked permissions (empty state)', async () => {
      renderPage()
      const btn = await screen.findByRole('button', { name: /admin\.role\.createBtn/i })
      await act(() => userEvent.click(btn))
      await screen.findByText('admin.role.createTitle')
      // Only group-level nodes visible with no checks
      const checkedItems = document.querySelectorAll('.ant-tree-checkbox-checked')
      expect(checkedItems).toHaveLength(0)
    })

    it('calls create API with leaf-only permission codes on submit', async () => {
      const user = userEvent.setup()
      renderPage()

      const btn = await screen.findByRole('button', { name: /admin\.role\.createBtn/i })
      await act(() => user.click(btn))
      await screen.findByText('admin.role.createTitle')

      // Fill roleName
      const nameInput = screen.getByLabelText('admin.role.fieldName')
      await act(() => user.type(nameInput, 'Test Role'))

      // Submit form via OK button
      const okBtn = screen
        .getAllByRole('button')
        .find((b) => b.classList.contains('ant-btn-primary') && b.closest('.ant-modal-footer'))
      if (okBtn) await act(() => user.click(okBtn))

      await waitFor(() => expect(mockApi.create).toHaveBeenCalled())
      const createArg = mockApi.create.mock.calls[0][0]
      expect(createArg.roleName).toBe('Test Role')
    })
  })

  describe('edit modal', () => {
    async function openEditForRole(roleName: string) {
      await screen.findByText(roleName)
      // Find the edit icon button next to the role name
      const roleRow = screen.getByText(roleName).closest('[style*="background"]') as HTMLElement
      const editBtn = roleRow?.querySelector('.anticon-edit')?.closest('button') as HTMLElement
      expect(editBtn).toBeTruthy()
      await act(() => fireEvent.click(editBtn))
    }

    it('opens edit modal with role name in title', async () => {
      renderPage()
      await openEditForRole('Editor')
      expect(await screen.findByText(/admin\.role\.editTitle/)).toBeInTheDocument()
    })

    it('calls update API on submit', async () => {
      const user = userEvent.setup()
      renderPage()
      await openEditForRole('Editor')
      await screen.findByText(/admin\.role\.editTitle/)

      const okBtn = screen
        .getAllByRole('button')
        .find((b) => b.classList.contains('ant-btn-primary') && b.closest('.ant-modal-footer'))
      if (okBtn) await act(() => user.click(okBtn))

      await waitFor(() => expect(mockApi.update).toHaveBeenCalledWith(2, expect.any(Object)))
    })

    it('update payload excludes group_ keys', async () => {
      const user = userEvent.setup()
      renderPage()
      await openEditForRole('Admin')
      await screen.findByText(/admin\.role\.editTitle/)

      const okBtn = screen
        .getAllByRole('button')
        .find((b) => b.classList.contains('ant-btn-primary') && b.closest('.ant-modal-footer'))
      if (okBtn) await act(() => user.click(okBtn))

      await waitFor(() => expect(mockApi.update).toHaveBeenCalled())
      const payload = mockApi.update.mock.calls[0][1]
      if (payload.permCodes) {
        expect(payload.permCodes.every((k: string) => !k.startsWith('group_'))).toBe(true)
      }
    })
  })
})
