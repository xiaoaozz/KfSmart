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

const ROLE_OPTIONS = [
  { label: '全部', value: '' },
  { label: '管理员', value: 'admin' },
  { label: '普通用户', value: 'user' },
]
const STATUS_OPTIONS = [
  { label: '全部', value: '' },
  { label: '正常', value: 'active' },
  { label: '禁用', value: 'disabled' },
]

export default function UserManagePage() {
  const qc = useQueryClient()
  const { message, modal } = App.useApp()
  const [keyword, setKeyword] = useState('')
  const [role, setRole] = useState('')
  const [status, setStatus] = useState('')
  const [current, setCurrent] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [editUser, setEditUser] = useState<AdminUser | null>(null)
  const [editForm] = Form.useForm<{ role: string; status: string; organizationTags: string[] }>()

  const { data, isLoading } = useQuery({
    queryKey: ['admin-users', current, pageSize, keyword, role, status],
    queryFn: () =>
      adminUserApi.list({
        current,
        size: pageSize,
        keyword: keyword || undefined,
        role: role || undefined,
        status: status || undefined,
      }),
  })

  const updateMutation = useMutation({
    mutationFn: (v: { role: string; status: string; organizationTags: string[] }) =>
      adminUserApi.update(editUser!.id, v),
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
        content: `临时密码：${res.tempPassword}，请告知用户尽快修改。`,
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
      role: u.role,
      status: u.status,
      organizationTags: u.organizationTags,
    })
  }

  const columns: TableColumnType<AdminUser>[] = [
    {
      title: '用户',
      dataIndex: 'username',
      render: (_: string, u: AdminUser) => (
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <Avatar size={32} src={u.avatar} icon={<UserOutlined />} />
          <div>
            <div style={{ fontWeight: 600, fontSize: 13 }}>{u.username}</div>
            <div style={{ fontSize: 11, color: 'var(--kf-muted-foreground)' }}>{u.email}</div>
          </div>
        </div>
      ),
    },
    {
      title: '角色',
      dataIndex: 'role',
      width: 90,
      render: (r: string) => (
        <Tag color={r === 'admin' ? 'red' : 'blue'}>{r === 'admin' ? '管理员' : '用户'}</Tag>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      render: (s: string) => (
        <Tag color={s === 'active' ? 'success' : 'default'}>{s === 'active' ? '正常' : '禁用'}</Tag>
      ),
    },
    {
      title: '组织',
      dataIndex: 'organizationTags',
      render: (tags: string[]) => tags.map((t) => <Tag key={t}>{t}</Tag>),
    },
    {
      title: '最后登录',
      dataIndex: 'lastLoginTime',
      width: 150,
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
            placeholder="搜索用户名/邮箱…"
            value={keyword}
            onChange={(e) => {
              setKeyword(e.target.value)
              setCurrent(1)
            }}
            style={{ width: 200 }}
            allowClear
          />
          <Select
            value={role}
            onChange={(v) => {
              setRole(v)
              setCurrent(1)
            }}
            options={ROLE_OPTIONS}
            style={{ width: 110 }}
          />
          <Select
            value={status}
            onChange={(v) => {
              setStatus(v)
              setCurrent(1)
            }}
            options={STATUS_OPTIONS}
            style={{ width: 110 }}
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
            <Select
              options={[
                { label: '管理员', value: 'admin' },
                { label: '普通用户', value: 'user' },
              ]}
            />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select
              options={[
                { label: '正常', value: 'active' },
                { label: '禁用', value: 'disabled' },
              ]}
            />
          </Form.Item>
          <Form.Item name="organizationTags" label="所属组织">
            <Select mode="tags" placeholder="输入或选择组织标签" />
          </Form.Item>
        </Form>
      </Drawer>
    </div>
  )
}
