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
import { useTranslation } from 'react-i18next'
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

export default function ApiKeyPage() {
  const qc = useQueryClient()
  const { message, modal } = App.useApp()
  const { t } = useTranslation()
  const [createOpen, setCreateOpen] = useState(false)
  const [createdKey, setCreatedKey] = useState<string | null>(null)
  const [form] = Form.useForm<{ name: string; scopes: string[]; expiresAt?: unknown }>()

  const STATUS_CFG = {
    active: {
      color: 'success',
      label: t('admin.apiKey.statusActive'),
      icon: <CheckCircleOutlined />,
    },
    disabled: { color: 'default', label: t('admin.apiKey.statusDisabled'), icon: <StopOutlined /> },
    expired: { color: 'error', label: t('admin.apiKey.statusExpired'), icon: <StopOutlined /> },
  }

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
      message.success(t('admin.apiKey.updateSuccess'))
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => adminApiKeyApi.delete(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin-api-keys'] })
      message.success(t('admin.apiKey.deleteSuccess'))
    },
  })

  const handleDelete = (k: ApiKey) => {
    modal.confirm({
      title: t('admin.apiKey.deleteConfirmTitle', { name: k.name }),
      content: t('admin.apiKey.deleteConfirmContent'),
      okType: 'danger',
      onOk: () => deleteMutation.mutateAsync(k.id),
    })
  }

  const columns = [
    {
      title: t('admin.apiKey.colKey'),
      dataIndex: 'keyPrefix',
      render: (prefix: string) => <span className={styles.keyMask}>{prefix}••••••••••••••••</span>,
    },
    {
      title: t('admin.apiKey.colName'),
      dataIndex: 'name',
      render: (n: string) => <span style={{ fontWeight: 600 }}>{n}</span>,
    },
    {
      title: t('admin.apiKey.colStatus'),
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
      title: t('admin.apiKey.colScopes'),
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
      title: t('admin.apiKey.colLastUsed'),
      dataIndex: 'lastUsedTime',
      width: 150,
      render: (v?: string) => (v ? new Date(v).toLocaleString() : '—'),
    },
    {
      title: t('admin.apiKey.colExpiry'),
      dataIndex: 'expiresAt',
      width: 120,
      render: (v?: string) => (v ? new Date(v).toLocaleDateString() : t('common.permanent')),
    },
    {
      title: t('admin.apiKey.colActions'),
      width: 110,
      render: (_: unknown, k: ApiKey) => (
        <Space size="small">
          {k.status === 'active' ? (
            <Tooltip title={t('admin.apiKey.tooltipDisable')}>
              <Button
                size="small"
                icon={<StopOutlined />}
                onClick={() => updateMutation.mutate({ id: k.id, status: 'disabled' })}
              />
            </Tooltip>
          ) : (
            <Tooltip title={t('admin.apiKey.tooltipEnable')}>
              <Button
                size="small"
                type="primary"
                icon={<CheckCircleOutlined />}
                onClick={() => updateMutation.mutate({ id: k.id, status: 'active' })}
                style={{ background: 'var(--kf-primary)', border: 'none' }}
              />
            </Tooltip>
          )}
          <Tooltip title={t('admin.apiKey.tooltipDelete')}>
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
          <KeyOutlined /> {t('admin.apiKey.title')}
        </h2>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          style={{ background: 'var(--kf-accent-gradient-r)', border: 'none' }}
          onClick={() => setCreateOpen(true)}
        >
          {t('admin.apiKey.createBtn')}
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

      <Modal
        title={t('admin.apiKey.createModalTitle')}
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
          <Form.Item name="name" label={t('admin.apiKey.fieldName')} rules={[{ required: true }]}>
            <Input placeholder={t('admin.apiKey.namePlaceholder')} />
          </Form.Item>
          <Form.Item
            name="scopes"
            label={t('admin.apiKey.fieldScopes')}
            rules={[{ required: true }]}
          >
            <Select
              mode="multiple"
              options={SCOPE_OPTIONS}
              placeholder={t('admin.apiKey.scopesPlaceholder')}
            />
          </Form.Item>
          <Form.Item name="expiresAt" label={t('admin.apiKey.fieldExpiry')}>
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={t('admin.apiKey.revealTitle')}
        open={!!createdKey}
        onOk={() => setCreatedKey(null)}
        onCancel={() => setCreatedKey(null)}
        cancelButtonProps={{ style: { display: 'none' } }}
        okText={t('admin.apiKey.copiedClose')}
      >
        <Descriptions column={1} bordered size="small">
          <Descriptions.Item label={t('admin.apiKey.revealKeyLabel')}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <span className={styles.keyMask} style={{ flex: 1, wordBreak: 'break-all' }}>
                {createdKey}
              </span>
              <Button
                size="small"
                icon={<CopyOutlined />}
                onClick={() => {
                  navigator.clipboard.writeText(createdKey ?? '')
                  message.success(t('admin.apiKey.copySuccess'))
                }}
              />
            </div>
          </Descriptions.Item>
        </Descriptions>
        <p style={{ marginTop: 12, color: 'var(--kf-muted-foreground)', fontSize: 13 }}>
          {t('admin.apiKey.revealWarning')}
        </p>
      </Modal>
    </div>
  )
}
