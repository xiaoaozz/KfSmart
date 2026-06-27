import { useState } from 'react'
import {
  Button,
  Modal,
  Form,
  Input,
  Select,
  DatePicker,
  Tag,
  App,
  Table,
  Space,
  Tooltip,
  Descriptions,
} from 'antd'
import {
  PlusOutlined,
  DeleteOutlined,
  StopOutlined,
  CheckCircleOutlined,
  KeyOutlined,
  CopyOutlined,
} from '@ant-design/icons'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { adminApiKeyApi, type ApiKey } from '@/api/admin'
import styles from './AdminPage.module.css'

const SCOPE_OPTIONS = [
  { label: 'chat:read', value: 'chat:read' },
  { label: 'chat:write', value: 'chat:write' },
  { label: 'kb:read', value: 'kb:read' },
  { label: 'kb:write', value: 'kb:write' },
  { label: 'agent:run', value: 'agent:run' },
  { label: 'workflow:run', value: 'workflow:run' },
  { label: 'admin:read', value: 'admin:read' },
]

const STATUS_CFG = {
  active: { color: 'success', label: '活跃', icon: <CheckCircleOutlined /> },
  disabled: { color: 'default', label: '禁用', icon: <StopOutlined /> },
  expired: { color: 'error', label: '已过期', icon: <StopOutlined /> },
}

export default function ApiKeyPage() {
  const qc = useQueryClient()
  const { message, modal } = App.useApp()
  const [createOpen, setCreateOpen] = useState(false)
  const [createdKey, setCreatedKey] = useState<string | null>(null)
  const [form] = Form.useForm<{ name: string; scopes: string[]; expiresAt?: unknown }>()

  const { data: keys, isLoading } = useQuery({
    queryKey: ['admin-api-keys'],
    queryFn: () => adminApiKeyApi.list(),
  })

  const createMutation = useMutation({
    mutationFn: (v: { name: string; scopes: string[]; expiresAt?: string }) =>
      adminApiKeyApi.create(v),
    onSuccess: (res) => {
      qc.invalidateQueries({ queryKey: ['admin-api-keys'] })
      setCreateOpen(false)
      form.resetFields()
      setCreatedKey(res.fullKey)
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, status }: { id: number; status: string }) =>
      adminApiKeyApi.update(id, { status }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin-api-keys'] })
      message.success('已更新')
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => adminApiKeyApi.delete(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin-api-keys'] })
      message.success('已删除')
    },
  })

  const handleDelete = (k: ApiKey) => {
    modal.confirm({
      title: `删除 API Key「${k.name}」？`,
      content: '删除后无法恢复，使用此 Key 的服务将立即失效。',
      okType: 'danger',
      onOk: () => deleteMutation.mutateAsync(k.id),
    })
  }

  const columns = [
    {
      title: 'Key',
      dataIndex: 'keyPrefix',
      render: (prefix: string) => <span className={styles.keyMask}>{prefix}••••••••••••••••</span>,
    },
    {
      title: '名称',
      dataIndex: 'name',
      render: (n: string) => <span style={{ fontWeight: 600 }}>{n}</span>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (s: ApiKey['status']) => {
        const cfg = STATUS_CFG[s]
        return (
          <Tag color={cfg.color} icon={cfg.icon}>
            {cfg.label}
          </Tag>
        )
      },
    },
    {
      title: '权限范围',
      dataIndex: 'scopes',
      render: (scopes: string[]) => (
        <>
          {scopes.map((s) => (
            <Tag key={s} style={{ fontFamily: 'var(--kf-font-mono)', fontSize: 11 }}>
              {s}
            </Tag>
          ))}
        </>
      ),
    },
    {
      title: '最后使用',
      dataIndex: 'lastUsedTime',
      width: 150,
      render: (t?: string) => (t ? new Date(t).toLocaleString() : '—'),
    },
    {
      title: '到期',
      dataIndex: 'expiresAt',
      width: 120,
      render: (t?: string) => (t ? new Date(t).toLocaleDateString() : '永久'),
    },
    {
      title: '操作',
      width: 110,
      render: (_: unknown, k: ApiKey) => (
        <Space size="small">
          {k.status === 'active' ? (
            <Tooltip title="禁用">
              <Button
                size="small"
                icon={<StopOutlined />}
                onClick={() => updateMutation.mutate({ id: k.id, status: 'disabled' })}
              />
            </Tooltip>
          ) : (
            <Tooltip title="启用">
              <Button
                size="small"
                type="primary"
                icon={<CheckCircleOutlined />}
                onClick={() => updateMutation.mutate({ id: k.id, status: 'active' })}
                style={{ background: 'var(--kf-primary)', border: 'none' }}
              />
            </Tooltip>
          )}
          <Tooltip title="删除">
            <Button size="small" danger icon={<DeleteOutlined />} onClick={() => handleDelete(k)} />
          </Tooltip>
        </Space>
      ),
    },
  ]

  return (
    <div className={styles.root}>
      <div className={styles.topBar}>
        <h2 className={styles.pageTitle}>
          <KeyOutlined /> API Key 管理
        </h2>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          style={{ background: 'var(--kf-accent-gradient-r)', border: 'none' }}
          onClick={() => setCreateOpen(true)}
        >
          创建 API Key
        </Button>
      </div>

      <Table
        rowKey="id"
        columns={columns}
        dataSource={keys}
        loading={isLoading}
        pagination={false}
        size="middle"
      />

      {/* Create Modal */}
      <Modal
        title="创建 API Key"
        open={createOpen}
        onCancel={() => {
          setCreateOpen(false)
          form.resetFields()
        }}
        onOk={() => form.submit()}
        confirmLoading={createMutation.isPending}
        destroyOnClose
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={(v) => {
            const expiresAt = v.expiresAt
              ? (v.expiresAt as { format: (f: string) => string }).format('YYYY-MM-DD')
              : undefined
            createMutation.mutate({ name: v.name, scopes: v.scopes, expiresAt })
          }}
        >
          <Form.Item name="name" label="名称" rules={[{ required: true }]}>
            <Input placeholder="例：生产服务账号" />
          </Form.Item>
          <Form.Item name="scopes" label="权限范围" rules={[{ required: true }]}>
            <Select mode="multiple" options={SCOPE_OPTIONS} placeholder="选择权限" />
          </Form.Item>
          <Form.Item name="expiresAt" label="到期时间（可选）">
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>

      {/* Show full key once after creation */}
      <Modal
        title="API Key 已创建"
        open={!!createdKey}
        onOk={() => setCreatedKey(null)}
        onCancel={() => setCreatedKey(null)}
        cancelButtonProps={{ style: { display: 'none' } }}
        okText="我已复制，关闭"
      >
        <Descriptions column={1} bordered size="small">
          <Descriptions.Item label="Key">
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <span className={styles.keyMask} style={{ flex: 1, wordBreak: 'break-all' }}>
                {createdKey}
              </span>
              <Button
                size="small"
                icon={<CopyOutlined />}
                onClick={() => {
                  navigator.clipboard.writeText(createdKey ?? '')
                  message.success('已复制')
                }}
              />
            </div>
          </Descriptions.Item>
        </Descriptions>
        <p style={{ marginTop: 12, color: 'var(--kf-muted-foreground)', fontSize: 13 }}>
          ⚠ 此 Key 只显示一次，请立即保存到安全的地方。
        </p>
      </Modal>
    </div>
  )
}
