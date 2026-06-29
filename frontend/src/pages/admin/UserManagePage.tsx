import { useState } from 'react'
import {
  Button,
  Input,
  Select,
  Tag,
  Drawer,
  Form,
  App,
  Space,
  Tooltip,
  TreeSelect,
  Spin,
} from 'antd'
import {
  SearchOutlined,
  EditOutlined,
  DeleteOutlined,
  ReloadOutlined,
  KeyOutlined,
  BankOutlined,
} from '@ant-design/icons'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { adminUserApi, adminRoleApi, adminOrgApi, type AdminUser, type OrgTag } from '@/api/admin'
import PageTable, { type TableColumnType } from '@/components/business/PageTable'
import { PageBar } from '@/components/business'
import UserAvatar from '@/components/UserAvatar'
import styles from './AdminPage.module.css'

// ---- helpers ----

function toTreeSelectData(
  nodes: OrgTag[],
): { value: string; label: string; children?: ReturnType<typeof toTreeSelectData> }[] {
  return nodes.map((n) => ({
    value: n.tagId,
    label: n.name,
    children: n.children?.length ? toTreeSelectData(n.children) : undefined,
  }))
}

/** Derive primary org name from the orgTags list */
function resolvePrimaryOrgName(u: AdminUser): string | null {
  if (!u.primaryOrg) return null
  const found = (u.orgTags ?? []).find((t) => t.tagId === u.primaryOrg)
  return found?.name ?? u.primaryOrg
}

/** Role tag colors by roleCode prefix */
function roleColor(roleCode: string): string {
  if (roleCode === 'ROLE_ADMIN') return 'red'
  if (roleCode === 'ROLE_KB_MANAGER') return 'orange'
  if (roleCode === 'ROLE_VIEWER') return 'default'
  return 'blue'
}

// ---- component ----

export default function UserManagePage() {
  const qc = useQueryClient()
  const { message, modal } = App.useApp()
  const { t } = useTranslation()
  const [keyword, setKeyword] = useState('')
  const [statusFilter, setStatusFilter] = useState<number | ''>('')
  const [current, setCurrent] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [editUser, setEditUser] = useState<AdminUser | null>(null)
  const [editForm] = Form.useForm<{ primaryOrg: string; roleCodes: string[] }>()

  const ROLE_FILTER_OPTIONS = [
    { label: t('admin.users.filterAll'), value: '' },
    { label: t('admin.users.tagAdmin'), value: 0 },
    { label: t('admin.users.tagUser'), value: 1 },
  ]

  // ---- queries ----

  const { data, isLoading } = useQuery({
    queryKey: ['admin-users', current, pageSize, keyword, statusFilter],
    queryFn: () =>
      adminUserApi.list({
        page: current,
        size: pageSize,
        keyword: keyword || undefined,
        status: statusFilter === '' ? undefined : statusFilter,
      }),
  })

  const { data: allRoles } = useQuery({
    queryKey: ['admin-roles'],
    queryFn: () => adminRoleApi.list(),
  })

  const { data: orgTree } = useQuery({
    queryKey: ['admin-org-tree'],
    queryFn: () => adminOrgApi.tree(),
  })

  // Fetch the current user's assigned RBAC roles when drawer opens
  const { data: userRoles, isLoading: userRolesLoading } = useQuery({
    queryKey: ['admin-user-roles', editUser?.id],
    queryFn: () => adminUserApi.getRoles(editUser!.id),
    enabled: !!editUser,
  })

  const orgTreeData = toTreeSelectData(orgTree ?? [])
  const roleOptions = (allRoles ?? []).map((r) => ({
    label: r.roleName,
    value: r.roleCode,
  }))

  // Sync form roleCodes once userRoles loads
  const drawerOpen = !!editUser
  if (drawerOpen && userRoles && !userRolesLoading) {
    const cur = editForm.getFieldValue('roleCodes') as string[] | undefined
    const fetched = userRoles.map((r) => r.roleCode)
    if (JSON.stringify(cur?.slice().sort()) !== JSON.stringify(fetched.slice().sort())) {
      editForm.setFieldValue('roleCodes', fetched)
    }
  }

  // ---- mutations ----

  const updateMutation = useMutation({
    mutationFn: async (v: { primaryOrg: string; roleCodes: string[] }) => {
      const originalOrg = editUser!.primaryOrg ?? ''
      const originalRoleCodes = (userRoles ?? []).map((r) => r.roleCode)

      const orgChanged = originalOrg !== (v.primaryOrg ?? '')
      const rolesChanged =
        originalRoleCodes.length !== v.roleCodes.length ||
        originalRoleCodes.some((c) => !v.roleCodes.includes(c))

      const tasks: Promise<unknown>[] = []
      if (orgChanged) {
        tasks.push(adminUserApi.assignOrgTags(editUser!.id, v.primaryOrg ? [v.primaryOrg] : []))
      }
      if (rolesChanged) {
        tasks.push(adminUserApi.assignRoles(editUser!.id, v.roleCodes))
      }
      if (tasks.length) await Promise.all(tasks)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin-users'] })
      qc.invalidateQueries({ queryKey: ['admin-user-roles', editUser?.id] })
      setEditUser(null)
      message.success(t('admin.users.updateSuccess'))
    },
    onError: () => {
      message.error(t('common.requestFailed'))
    },
  })

  const resetPwMutation = useMutation({
    mutationFn: (id: number) => adminUserApi.resetPassword(id),
    onSuccess: (res) => {
      modal.info({
        title: t('admin.users.resetPwTitle'),
        content: t('admin.users.resetPwContent', { password: res.newPassword }),
      })
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => adminUserApi.delete(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin-users'] })
      message.success(t('admin.users.deleteSuccess'))
    },
    onError: () => {
      message.error(t('admin.users.deleteFailed'))
    },
  })

  // ---- countdown modal helpers ----

  const makeCountdown = (opts: {
    title: string
    content: React.ReactNode
    okType?: 'danger' | 'primary'
    onOk: () => Promise<unknown>
  }) => {
    let count = 5
    let timerId: number | null = null

    const instance = modal.confirm({
      title: opts.title,
      content: opts.content,
      okText: t('admin.users.deleteConfirmCountdown', { n: count }),
      okType: opts.okType ?? 'danger',
      okButtonProps: { disabled: true },
      cancelText: t('common.cancel'),
      onOk: opts.onOk,
      afterClose: () => {
        if (timerId !== null) window.clearInterval(timerId)
      },
    })

    timerId = window.setInterval(() => {
      count -= 1
      if (count <= 0) {
        window.clearInterval(timerId!)
        timerId = null
        instance.update({
          okText: t('admin.users.deleteConfirmReady'),
          okButtonProps: { disabled: false },
        })
      } else {
        instance.update({ okText: t('admin.users.deleteConfirmCountdown', { n: count }) })
      }
    }, 1000)
  }

  const handleDelete = (u: AdminUser) =>
    makeCountdown({
      title: t('admin.users.deleteModalTitle'),
      content: (
        <div>
          <p style={{ marginBottom: 12, color: 'var(--kf-foreground)' }}>
            {t('admin.users.deleteModalDesc')}
          </p>
          <div
            style={{
              padding: '8px 12px',
              background: 'var(--kf-muted)',
              borderLeft: '3px solid var(--kf-danger)',
              borderRadius: 'var(--kf-radius-xs)',
              fontFamily: 'var(--kf-font-mono)',
              fontSize: 13,
              color: 'var(--kf-foreground)',
              wordBreak: 'break-all',
            }}
          >
            {u.username}
          </div>
        </div>
      ),
      onOk: () => deleteMutation.mutateAsync(u.id),
    })

  const handleResetPw = (u: AdminUser) =>
    makeCountdown({
      title: t('admin.users.resetPwModalTitle'),
      content: (
        <div>
          <p style={{ marginBottom: 12, color: 'var(--kf-foreground)' }}>
            {t('admin.users.resetPwModalDesc')}
          </p>
          <div
            style={{
              padding: '8px 12px',
              background: 'var(--kf-muted)',
              borderLeft: '3px solid var(--kf-accent)',
              borderRadius: 'var(--kf-radius-xs)',
              fontFamily: 'var(--kf-font-mono)',
              fontSize: 13,
              color: 'var(--kf-foreground)',
              wordBreak: 'break-all',
            }}
          >
            {u.username}
          </div>
        </div>
      ),
      okType: 'primary',
      onOk: () => resetPwMutation.mutateAsync(u.id),
    })

  const openEdit = (u: AdminUser) => {
    setEditUser(u)
    editForm.setFieldsValue({
      primaryOrg: u.primaryOrg ?? undefined,
      roleCodes: [], // will be overwritten once userRoles query resolves
    })
  }

  // ---- table columns ----

  const columns: TableColumnType<AdminUser>[] = [
    {
      title: t('admin.users.colUser'),
      dataIndex: 'username',
      render: (_: string, u: AdminUser) => (
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <UserAvatar size={32} avatar={u.avatar} username={u.username} />
          <div>
            <div style={{ fontWeight: 600, fontSize: 13 }}>{u.username}</div>
            {u.email && (
              <div style={{ fontSize: 11, color: 'var(--kf-muted-foreground)' }}>{u.email}</div>
            )}
          </div>
        </div>
      ),
    },
    {
      title: t('admin.users.colRole'),
      dataIndex: 'roles',
      width: 180,
      render: (_: unknown, u: AdminUser) => {
        const rbac = u.roles ?? []
        if (rbac.length > 0) {
          return (
            <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
              {rbac.slice(0, 3).map((r) => (
                <Tag key={r.roleCode} color={roleColor(r.roleCode)} style={{ fontSize: 11 }}>
                  {r.roleName}
                </Tag>
              ))}
              {rbac.length > 3 && <Tag style={{ fontSize: 11 }}>+{rbac.length - 3}</Tag>}
            </div>
          )
        }
        // Fallback to legacy status
        const isAdmin = u.status === 0
        return (
          <Tag color={isAdmin ? 'red' : 'blue'}>
            {isAdmin ? t('admin.users.tagAdmin') : t('admin.users.tagUser')}
          </Tag>
        )
      },
    },
    {
      title: t('admin.users.colOrg'),
      dataIndex: 'primaryOrg',
      width: 130,
      render: (_: unknown, u: AdminUser) => {
        const name = resolvePrimaryOrgName(u)
        return name ? (
          <Tag icon={<BankOutlined />} style={{ fontSize: 11 }}>
            {name}
          </Tag>
        ) : (
          <span style={{ color: 'var(--kf-muted-foreground)' }}>—</span>
        )
      },
    },
    {
      title: t('admin.users.colCreatedAt'),
      dataIndex: 'createdAt',
      width: 160,
      render: (t?: string) => (t ? new Date(t).toLocaleString() : '—'),
    },
    {
      title: t('admin.users.colActions'),
      width: 140,
      render: (_: unknown, u: AdminUser) => (
        <Space size="small">
          <Tooltip title={t('admin.users.tooltipEdit')}>
            <Button size="small" icon={<EditOutlined />} onClick={() => openEdit(u)} />
          </Tooltip>
          <Tooltip title={t('admin.users.tooltipResetPw')}>
            <Button size="small" icon={<KeyOutlined />} onClick={() => handleResetPw(u)} />
          </Tooltip>
          <Tooltip title={t('admin.users.tooltipDelete')}>
            <Button size="small" danger icon={<DeleteOutlined />} onClick={() => handleDelete(u)} />
          </Tooltip>
        </Space>
      ),
    },
  ]

  // ---- render ----

  return (
    <div className={styles.root}>
      <div className={styles.topBar}>
        <div className={styles.filters}>
          <Input
            prefix={<SearchOutlined />}
            placeholder={t('admin.users.searchPlaceholder')}
            value={keyword}
            onChange={(e) => {
              setKeyword(e.target.value)
              setCurrent(1)
            }}
            style={{ width: 200 }}
            allowClear
          />
          <Select
            value={statusFilter}
            onChange={(v: number | '') => {
              setStatusFilter(v)
              setCurrent(1)
            }}
            options={ROLE_FILTER_OPTIONS}
            style={{ width: 120 }}
          />
          <Button
            icon={<ReloadOutlined />}
            onClick={() => qc.invalidateQueries({ queryKey: ['admin-users'] })}
          />
        </div>
      </div>

      <PageTable<AdminUser>
        rowKey="id"
        columns={columns}
        dataSource={data?.records}
        loading={isLoading}
        showPagination={false}
      />
      {(data?.total ?? 0) > 0 && (
        <div
          style={{
            display: 'flex',
            justifyContent: 'flex-end',
            alignItems: 'center',
            flexShrink: 0,
            padding: '12px 20px',
            borderTop: '1px solid var(--kf-border)',
          }}
        >
          <PageBar
            current={current}
            pageSize={pageSize}
            total={data!.total}
            onChange={(p, s) => {
              setCurrent(p)
              setPageSize(s)
            }}
          />
        </div>
      )}

      {/* Edit drawer */}
      <Drawer
        title={
          editUser
            ? `${t('admin.users.editDrawer')} — ${editUser.username}`
            : t('admin.users.editDrawer')
        }
        open={drawerOpen}
        onClose={() => setEditUser(null)}
        width={440}
        destroyOnClose
        footer={
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
            <Button onClick={() => setEditUser(null)}>{t('common.cancel')}</Button>
            <Button
              type="primary"
              loading={updateMutation.isPending}
              style={{ background: 'var(--kf-accent-gradient-r)', border: 'none' }}
              onClick={() => editForm.submit()}
            >
              {t('common.save')}
            </Button>
          </div>
        }
      >
        <Spin spinning={userRolesLoading}>
          {/* User info header */}
          {editUser && (
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 12,
                marginBottom: 24,
                padding: '12px 16px',
                background: 'var(--kf-muted)',
                borderRadius: 'var(--kf-radius-md)',
              }}
            >
              <UserAvatar size={40} avatar={editUser.avatar} username={editUser.username} />
              <div>
                <div style={{ fontWeight: 600 }}>{editUser.username}</div>
                {editUser.email && (
                  <div style={{ fontSize: 12, color: 'var(--kf-muted-foreground)' }}>
                    {editUser.email}
                  </div>
                )}
              </div>
            </div>
          )}

          <Form form={editForm} layout="vertical" onFinish={(v) => updateMutation.mutate(v)}>
            {/* Organization — single TreeSelect */}
            <Form.Item
              name="primaryOrg"
              label={t('admin.users.fieldPrimaryOrg')}
              extra={
                <span style={{ fontSize: 11, color: 'var(--kf-muted-foreground)' }}>
                  {t('admin.users.primaryOrgHint')}
                </span>
              }
            >
              <TreeSelect
                treeData={orgTreeData}
                placeholder={t('admin.users.primaryOrgPlaceholder')}
                allowClear
                showSearch
                treeNodeFilterProp="label"
                style={{ width: '100%' }}
              />
            </Form.Item>

            {/* RBAC Roles — multi select */}
            <Form.Item
              name="roleCodes"
              label={t('admin.users.fieldAssignedRoles')}
              extra={
                <span style={{ fontSize: 11, color: 'var(--kf-muted-foreground)' }}>
                  {t('admin.users.rolesHint')}
                </span>
              }
            >
              <Select
                mode="multiple"
                options={roleOptions}
                placeholder={t('admin.users.rolesPlaceholder')}
                optionFilterProp="label"
                allowClear
              />
            </Form.Item>
          </Form>
        </Spin>
      </Drawer>
    </div>
  )
}
