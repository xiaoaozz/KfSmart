import { useState } from 'react'
import { Button, Input, Select, Tag, Drawer, Form, App, Space, Tooltip } from 'antd'
import {
  SearchOutlined,
  EditOutlined,
  DeleteOutlined,
  ReloadOutlined,
  KeyOutlined,
  TeamOutlined,
} from '@ant-design/icons'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { adminUserApi, type AdminUser } from '@/api/admin'
import PageTable, { type TableColumnType } from '@/components/business/PageTable'
import UserAvatar from '@/components/UserAvatar'
import styles from './AdminPage.module.css'

function roleFromStatus(status?: number): 'ADMIN' | 'USER' {
  return status === 0 ? 'ADMIN' : 'USER'
}

export default function UserManagePage() {
  const qc = useQueryClient()
  const { message, modal } = App.useApp()
  const { t } = useTranslation()
  const [keyword, setKeyword] = useState('')
  const [statusFilter, setStatusFilter] = useState<number | ''>('')
  const [current, setCurrent] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [editUser, setEditUser] = useState<AdminUser | null>(null)
  const [editForm] = Form.useForm<{ role: string; orgTags: string[] }>()

  const ROLE_FILTER_OPTIONS = [
    { label: t('admin.users.filterAll'), value: '' },
    { label: t('admin.users.tagAdmin'), value: 0 },
    { label: t('admin.users.tagUser'), value: 1 },
  ]

  const ROLE_EDIT_OPTIONS = [
    { label: t('admin.users.tagAdmin'), value: 'ADMIN' },
    { label: t('admin.users.tagUser'), value: 'USER' },
  ]

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

  const updateMutation = useMutation({
    mutationFn: async (v: { role: string; orgTags: string[] }) => {
      const roleChanged = roleFromStatus(editUser!.status) !== v.role
      const originalTagIds = (editUser!.orgTags ?? []).map((t) => t.tagId)
      const tagsChanged =
        originalTagIds.length !== v.orgTags.length ||
        originalTagIds.some((id, i) => id !== v.orgTags[i])
      const tasks: Promise<unknown>[] = []
      if (roleChanged) tasks.push(adminUserApi.update(editUser!.id, { role: v.role }))
      if (tagsChanged) tasks.push(adminUserApi.assignOrgTags(editUser!.id, v.orgTags))
      if (!tasks.length) return Promise.resolve()
      await Promise.all(tasks)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin-users'] })
      setEditUser(null)
      message.success(t('admin.users.updateSuccess'))
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

  const handleDelete = (u: AdminUser) => {
    let count = 5
    let timerId: number | null = null

    const instance = modal.confirm({
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
      okText: t('admin.users.deleteConfirmCountdown', { n: count }),
      okType: 'danger',
      okButtonProps: { disabled: true },
      cancelText: t('common.cancel'),
      onOk: () => deleteMutation.mutateAsync(u.id),
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
        instance.update({
          okText: t('admin.users.deleteConfirmCountdown', { n: count }),
        })
      }
    }, 1000)
  }

  const openEdit = (u: AdminUser) => {
    setEditUser(u)
    editForm.setFieldsValue({
      role: roleFromStatus(u.status),
      orgTags: (u.orgTags ?? []).map((t) => t.tagId),
    })
  }

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
      dataIndex: 'status',
      width: 90,
      render: (s?: number) => {
        const isAdmin = s === 0
        return (
          <Tag color={isAdmin ? 'red' : 'blue'}>
            {isAdmin ? t('admin.users.tagAdmin') : t('admin.users.tagUser')}
          </Tag>
        )
      },
    },
    {
      title: t('admin.users.colOrg'),
      dataIndex: 'orgTags',
      render: (tags?: AdminUser['orgTags']) =>
        tags && tags.length ? (
          tags.map((t) => <Tag key={t.tagId}>{t.name}</Tag>)
        ) : (
          <span style={{ color: 'var(--kf-muted-foreground)' }}>—</span>
        ),
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
            <Button
              size="small"
              icon={<KeyOutlined />}
              onClick={() => resetPwMutation.mutate(u.id)}
            />
          </Tooltip>
          <Tooltip title={t('admin.users.tooltipDelete')}>
            <Button size="small" danger icon={<DeleteOutlined />} onClick={() => handleDelete(u)} />
          </Tooltip>
        </Space>
      ),
    },
  ]

  return (
    <div className={styles.root}>
      <div className={styles.topBar}>
        <h2 className={styles.pageTitle}>
          <TeamOutlined /> {t('admin.users.title')}
        </h2>
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
        total={data?.total}
        current={current}
        pageSize={pageSize}
        onPageChange={(p, s) => {
          setCurrent(p)
          setPageSize(s)
        }}
      />

      <Drawer
        title={t('admin.users.editDrawer')}
        open={!!editUser}
        onClose={() => setEditUser(null)}
        width={400}
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
        <Form form={editForm} layout="vertical" onFinish={(v) => updateMutation.mutate(v)}>
          <Form.Item name="role" label={t('admin.users.fieldRole')}>
            <Select options={ROLE_EDIT_OPTIONS} />
          </Form.Item>
          <Form.Item name="orgTags" label={t('admin.users.fieldOrgTags')}>
            <Select mode="tags" placeholder={t('admin.users.orgTagPlaceholder')} />
          </Form.Item>
        </Form>
      </Drawer>
    </div>
  )
}
