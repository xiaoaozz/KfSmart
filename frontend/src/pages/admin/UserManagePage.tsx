import { useState } from 'react'
import {
  Button,
  Input,
  Select,
  Tag,
  Drawer,
  Form,
  App,
  Avatar,
  Space,
  Tooltip,
  Popconfirm,
} from 'antd'
import {
  SearchOutlined,
  UserOutlined,
  EditOutlined,
  DeleteOutlined,
  ReloadOutlined,
  KeyOutlined,
  TeamOutlined,
} from '@ant-design/icons'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { adminUserApi, type AdminUser } from '@/api/admin'
import PageTable, { type TableColumnType } from '@/components/business/PageTable'
import styles from './AdminPage.module.css'

// 后端 status：0=管理员, 1=普通用户（status 由 role 派生）
const ROLE_FILTER_OPTIONS = [
  { label: '全部', value: '' },
  { label: '管理员', value: 0 },
  { label: '普通用户', value: 1 },
]

const ROLE_EDIT_OPTIONS = [
  { label: '管理员', value: 'ADMIN' },
  { label: '普通用户', value: 'USER' },
]

function roleFromStatus(status?: number): 'ADMIN' | 'USER' {
  return status === 0 ? 'ADMIN' : 'USER'
}

export default function UserManagePage() {
  const qc = useQueryClient()
  const { message, modal } = App.useApp()
  const [keyword, setKeyword] = useState('')
  const [statusFilter, setStatusFilter] = useState<number | ''>('')
  const [current, setCurrent] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [editUser, setEditUser] = useState<AdminUser | null>(null)
  const [editForm] = Form.useForm<{ role: string; orgTags: string[] }>()

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
      message.success('已更新')
    },
  })

  const resetPwMutation = useMutation({
    mutationFn: (id: number) => adminUserApi.resetPassword(id),
    onSuccess: (res) => {
      modal.info({
        title: '密码已重置',
        content: `新密码：${res.newPassword}，请告知用户尽快登录修改。`,
      })
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => adminUserApi.delete(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin-users'] })
      message.success('已删除')
    },
  })

  const openEdit = (u: AdminUser) => {
    setEditUser(u)
    editForm.setFieldsValue({
      role: roleFromStatus(u.status),
      orgTags: (u.orgTags ?? []).map((t) => t.tagId),
    })
  }

  const columns: TableColumnType<AdminUser>[] = [
    {
      title: '用户',
      dataIndex: 'username',
      render: (_: string, u: AdminUser) => (
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <Avatar size={32} src={u.avatar} icon={<UserOutlined />}>
            {u.username?.[0]?.toUpperCase()}
          </Avatar>
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
      title: '角色',
      dataIndex: 'status',
      width: 90,
      render: (s?: number) => {
        const isAdmin = s === 0
        return <Tag color={isAdmin ? 'red' : 'blue'}>{isAdmin ? '管理员' : '普通用户'}</Tag>
      },
    },
    {
      title: '组织',
      dataIndex: 'orgTags',
      render: (tags?: AdminUser['orgTags']) =>
        tags && tags.length ? (
          tags.map((t) => <Tag key={t.tagId}>{t.name}</Tag>)
        ) : (
          <span style={{ color: 'var(--kf-muted-foreground)' }}>—</span>
        ),
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      width: 160,
      render: (t?: string) => (t ? new Date(t).toLocaleString() : '—'),
    },
    {
      title: '操作',
      width: 140,
      render: (_: unknown, u: AdminUser) => (
        <Space size="small">
          <Tooltip title="编辑">
            <Button size="small" icon={<EditOutlined />} onClick={() => openEdit(u)} />
          </Tooltip>
          <Tooltip title="重置密码">
            <Button
              size="small"
              icon={<KeyOutlined />}
              onClick={() => resetPwMutation.mutate(u.id)}
            />
          </Tooltip>
          <Tooltip title="删除">
            <Popconfirm
              title={`删除用户「${u.username}」？`}
              okType="danger"
              onConfirm={() => deleteMutation.mutate(u.id)}
            >
              <Button size="small" danger icon={<DeleteOutlined />} />
            </Popconfirm>
          </Tooltip>
        </Space>
      ),
    },
  ]

  return (
    <div className={styles.root}>
      <div className={styles.topBar}>
        <h2 className={styles.pageTitle}>
          <TeamOutlined /> 用户管理
        </h2>
        <div className={styles.filters}>
          <Input
            prefix={<SearchOutlined />}
            placeholder="搜索用户名…"
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
        title="编辑用户"
        open={!!editUser}
        onClose={() => setEditUser(null)}
        width={400}
        footer={
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
            <Button onClick={() => setEditUser(null)}>取消</Button>
            <Button
              type="primary"
              loading={updateMutation.isPending}
              style={{ background: 'var(--kf-accent-gradient-r)', border: 'none' }}
              onClick={() => editForm.submit()}
            >
              保存
            </Button>
          </div>
        }
      >
        <Form form={editForm} layout="vertical" onFinish={(v) => updateMutation.mutate(v)}>
          <Form.Item name="role" label="角色">
            <Select options={ROLE_EDIT_OPTIONS} />
          </Form.Item>
          <Form.Item name="orgTags" label="所属组织（标签 ID）">
            <Select mode="tags" placeholder="输入或选择组织标签 ID" />
          </Form.Item>
        </Form>
      </Drawer>
    </div>
  )
}
