import { useState } from 'react'
import { Button, Input, Select, Modal, Form, Tag, App, Space, Tooltip, AutoComplete } from 'antd'
import {
  PlusOutlined,
  DeleteOutlined,
  EditOutlined,
  CheckCircleOutlined,
  SearchOutlined,
  ReloadOutlined,
  EyeInvisibleOutlined,
} from '@ant-design/icons'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { adminApiKeyApi, type AiModelConfig } from '@/api/admin'
import PageTable, { type TableColumnType } from '@/components/business/PageTable'
import { PageBar } from '@/components/business'
import styles from './AdminPage.module.css'

// ── Provider metadata ────────────────────────────────────────
interface ProviderMeta {
  label: string
  defaultUrl: string
  defaultAuthType: string
  models: string[]
  color: string
}

const PROVIDERS: Record<string, ProviderMeta> = {
  deepseek: {
    label: 'DeepSeek',
    defaultUrl: 'https://api.deepseek.com/v1/chat/completions',
    defaultAuthType: 'openai',
    models: ['deepseek-chat', 'deepseek-reasoner'],
    color: '#2563eb',
  },
  openai: {
    label: 'OpenAI',
    defaultUrl: 'https://api.openai.com/v1/chat/completions',
    defaultAuthType: 'openai',
    models: ['gpt-4o', 'gpt-4o-mini', 'gpt-4-turbo', 'gpt-3.5-turbo'],
    color: '#10b981',
  },
  anthropic: {
    label: 'Anthropic',
    defaultUrl: 'https://api.anthropic.com/v1/messages',
    defaultAuthType: 'anthropic',
    models: ['claude-opus-4-8', 'claude-sonnet-4-6', 'claude-haiku-4-5'],
    color: '#f97316',
  },
  qwen: {
    label: '通义千问',
    defaultUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions',
    defaultAuthType: 'openai',
    models: ['qwen-max', 'qwen-plus', 'qwen-turbo', 'qwen-long'],
    color: '#8b5cf6',
  },
  zhipu: {
    label: '智谱 AI',
    defaultUrl: 'https://open.bigmodel.cn/api/paas/v4/chat/completions',
    defaultAuthType: 'bearer',
    models: ['glm-4', 'glm-4-air', 'glm-4-flash'],
    color: '#06b6d4',
  },
  custom: {
    label: '自定义',
    defaultUrl: '',
    defaultAuthType: 'bearer',
    models: [],
    color: 'var(--kf-muted-foreground)',
  },
}

const PROVIDER_OPTIONS = Object.entries(PROVIDERS).map(([value, m]) => ({
  value,
  label: m.label,
}))

const PROVIDER_FILTER_OPTIONS = [{ label: '全部提供商', value: '' }, ...PROVIDER_OPTIONS]

const AUTH_TYPE_OPTIONS = [
  { value: 'bearer', label: 'Bearer（通用）' },
  { value: 'openai', label: 'OpenAI 兼容' },
  { value: 'anthropic', label: 'Anthropic' },
]

// ── Form type ─────────────────────────────────────────────────
interface ConfigFormValues {
  name: string
  provider: string
  apiUrl: string
  apiKey: string
  modelName: string
  authType: string
  remark?: string
}

// ── Component ─────────────────────────────────────────────────
export default function ApiKeyPage() {
  const qc = useQueryClient()
  const { message, modal } = App.useApp()
  const { t } = useTranslation()

  const [keyword, setKeyword] = useState('')
  const [providerFilter, setProviderFilter] = useState('')
  const [current, setCurrent] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [editTarget, setEditTarget] = useState<AiModelConfig | null>(null)
  const [createOpen, setCreateOpen] = useState(false)
  const [form] = Form.useForm<ConfigFormValues>()
  const [modelOptions, setModelOptions] = useState<string[]>([])

  const { data: configs, isLoading } = useQuery({
    queryKey: ['admin-api-keys'],
    queryFn: () => adminApiKeyApi.list(),
  })

  const createMutation = useMutation({
    mutationFn: (v: ConfigFormValues) => adminApiKeyApi.create(v),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin-api-keys'] })
      message.success(t('admin.apiKey.createSuccess'))
      setCreateOpen(false)
      form.resetFields()
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, v }: { id: number; v: ConfigFormValues }) => adminApiKeyApi.update(id, v),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin-api-keys'] })
      message.success(t('admin.apiKey.updateSuccess'))
      setEditTarget(null)
      form.resetFields()
    },
  })

  const activateMutation = useMutation({
    mutationFn: (id: number) => adminApiKeyApi.activate(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin-api-keys'] })
      message.success(t('admin.apiKey.activateSuccess'))
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => adminApiKeyApi.delete(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin-api-keys'] })
      message.success(t('admin.apiKey.deleteSuccess'))
    },
  })

  const handleDelete = (c: AiModelConfig) => {
    modal.confirm({
      title: t('admin.apiKey.deleteConfirmTitle', { name: c.name }),
      content: t('admin.apiKey.deleteConfirmContent'),
      okType: 'danger',
      onOk: () => deleteMutation.mutateAsync(c.id),
    })
  }

  const openEdit = (c: AiModelConfig) => {
    setEditTarget(c)
    setModelOptions(PROVIDERS[c.provider]?.models ?? [])
    form.setFieldsValue({
      name: c.name,
      provider: c.provider,
      apiUrl: c.apiUrl,
      apiKey: c.apiKey,
      modelName: c.modelName,
      authType: c.authType ?? 'bearer',
      remark: c.remark,
    })
  }

  const handleProviderChange = (provider: string) => {
    const meta = PROVIDERS[provider]
    if (!meta) return
    setModelOptions(meta.models)
    form.setFieldsValue({ apiUrl: meta.defaultUrl, authType: meta.defaultAuthType })
  }

  const handleFormSubmit = (v: ConfigFormValues) => {
    if (editTarget) {
      updateMutation.mutate({ id: editTarget.id, v })
    } else {
      createMutation.mutate(v)
    }
  }

  // ── Filtering ─────────────────────────────────────────────
  const filtered = (configs ?? []).filter((c) => {
    const q = keyword.trim().toLowerCase()
    const matchText =
      !q ||
      c.name.toLowerCase().includes(q) ||
      c.modelName.toLowerCase().includes(q) ||
      (PROVIDERS[c.provider]?.label ?? c.provider).toLowerCase().includes(q)
    const matchProvider = !providerFilter || c.provider === providerFilter
    return matchText && matchProvider
  })
  const paged = filtered.slice((current - 1) * pageSize, current * pageSize)

  // ── Columns ───────────────────────────────────────────────
  const columns: TableColumnType<AiModelConfig>[] = [
    {
      title: t('admin.apiKey.colName'),
      dataIndex: 'name',
      render: (name: string, c: AiModelConfig) => (
        <div>
          <div style={{ fontWeight: 600, fontSize: 13 }}>{name}</div>
          {c.remark && (
            <div style={{ fontSize: 11, color: 'var(--kf-muted-foreground)', marginTop: 2 }}>
              {c.remark}
            </div>
          )}
        </div>
      ),
    },
    {
      title: t('admin.apiKey.fieldProvider'),
      dataIndex: 'provider',
      width: 120,
      render: (provider: string) => {
        const meta = PROVIDERS[provider]
        return (
          <Tag
            style={{
              background: `${meta?.color ?? 'gray'}18`,
              borderColor: `${meta?.color ?? 'gray'}40`,
              color: meta?.color ?? 'inherit',
              border: '1px solid',
            }}
          >
            {meta?.label ?? provider}
          </Tag>
        )
      },
    },
    {
      title: t('admin.apiKey.fieldModelName'),
      dataIndex: 'modelName',
      width: 160,
      render: (v: string) => (
        <span style={{ fontFamily: 'var(--kf-font-mono)', fontSize: 12 }}>{v}</span>
      ),
    },
    {
      title: t('admin.apiKey.fieldApiKey'),
      dataIndex: 'apiKey',
      width: 160,
      render: (v: string) => (
        <span
          style={{
            fontFamily: 'var(--kf-font-mono)',
            fontSize: 12,
            color: 'var(--kf-muted-foreground)',
          }}
        >
          {v}
        </span>
      ),
    },
    {
      title: t('admin.apiKey.colStatus'),
      dataIndex: 'active',
      width: 100,
      render: (active: boolean) =>
        active ? (
          <Tag color="success">{t('admin.apiKey.statusActive')}</Tag>
        ) : (
          <Tag>{t('admin.apiKey.statusInactive')}</Tag>
        ),
    },
    {
      title: t('admin.apiKey.colActions'),
      width: 130,
      render: (_: unknown, c: AiModelConfig) => (
        <Space size="small">
          {!c.active && (
            <Tooltip title={t('admin.apiKey.tooltipActivate')}>
              <Button
                size="small"
                type="primary"
                icon={<CheckCircleOutlined />}
                loading={activateMutation.isPending}
                onClick={() => activateMutation.mutate(c.id)}
                style={{ background: 'var(--kf-accent)', border: 'none' }}
              />
            </Tooltip>
          )}
          <Tooltip title={t('admin.apiKey.tooltipEdit')}>
            <Button size="small" icon={<EditOutlined />} onClick={() => openEdit(c)} />
          </Tooltip>
          <Tooltip title={t('admin.apiKey.tooltipDelete')}>
            <Button size="small" danger icon={<DeleteOutlined />} onClick={() => handleDelete(c)} />
          </Tooltip>
        </Space>
      ),
    },
  ]

  const isModalOpen = createOpen || !!editTarget
  const isEditing = !!editTarget
  const isMutating = createMutation.isPending || updateMutation.isPending

  // ── Render ────────────────────────────────────────────────
  return (
    <div className={styles.root}>
      {/* ── Toolbar ── */}
      <div className={styles.topBar}>
        <div className={styles.filters}>
          <Input
            prefix={<SearchOutlined />}
            placeholder={t('admin.apiKey.searchPlaceholder')}
            value={keyword}
            onChange={(e) => {
              setKeyword(e.target.value)
              setCurrent(1)
            }}
            style={{ width: 220 }}
            allowClear
          />
          <Select
            value={providerFilter}
            onChange={(v) => {
              setProviderFilter(v)
              setCurrent(1)
            }}
            options={PROVIDER_FILTER_OPTIONS}
            style={{ width: 140 }}
          />
          <Button
            icon={<ReloadOutlined />}
            onClick={() => qc.invalidateQueries({ queryKey: ['admin-api-keys'] })}
          />
        </div>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          style={{ background: 'var(--kf-accent-gradient-r)', border: 'none' }}
          onClick={() => {
            form.resetFields()
            setModelOptions([])
            setCreateOpen(true)
          }}
        >
          {t('admin.apiKey.createBtn')}
        </Button>
      </div>

      {/* ── Table ── */}
      <PageTable<AiModelConfig>
        rowKey="id"
        columns={columns}
        dataSource={paged}
        loading={isLoading}
        showPagination={false}
      />
      {filtered.length > 0 && (
        <div
          style={{
            marginTop: 'auto',
            display: 'flex',
            justifyContent: 'flex-end',
            alignItems: 'center',
            flexShrink: 0,
            padding: '10px 20px',
            borderTop: '1px solid var(--kf-border)',
          }}
        >
          <PageBar
            current={current}
            pageSize={pageSize}
            total={filtered.length}
            onChange={(page, size) => {
              setCurrent(page)
              setPageSize(size)
            }}
          />
        </div>
      )}

      {/* ── Create / Edit Modal ── */}
      <Modal
        title={isEditing ? t('admin.apiKey.editModalTitle') : t('admin.apiKey.createModalTitle')}
        open={isModalOpen}
        onCancel={() => {
          setCreateOpen(false)
          setEditTarget(null)
          form.resetFields()
        }}
        onOk={() => form.submit()}
        confirmLoading={isMutating}
        destroyOnClose
        width={520}
      >
        <Form
          form={form}
          layout="vertical"
          style={{ marginTop: 8 }}
          onFinish={handleFormSubmit}
          initialValues={{ authType: 'bearer' }}
        >
          <Form.Item name="name" label={t('admin.apiKey.fieldName')} rules={[{ required: true }]}>
            <Input placeholder={t('admin.apiKey.namePlaceholder')} />
          </Form.Item>

          <Form.Item
            name="provider"
            label={t('admin.apiKey.fieldProvider')}
            rules={[{ required: true }]}
          >
            <Select
              options={PROVIDER_OPTIONS}
              placeholder={t('admin.apiKey.fieldProvider')}
              onChange={handleProviderChange}
            />
          </Form.Item>

          <Form.Item
            name="apiUrl"
            label={t('admin.apiKey.fieldApiUrl')}
            rules={[{ required: true }]}
          >
            <Input placeholder="https://api.example.com/v1/chat/completions" />
          </Form.Item>

          <Form.Item
            name="apiKey"
            label={t('admin.apiKey.fieldApiKey')}
            rules={isEditing ? [] : [{ required: true }]}
            extra={isEditing ? t('admin.apiKey.apiKeyEditHint') : undefined}
          >
            <Input.Password
              placeholder={
                isEditing ? t('admin.apiKey.apiKeyEditHint') : t('admin.apiKey.apiKeyPlaceholder')
              }
              iconRender={() => <EyeInvisibleOutlined />}
            />
          </Form.Item>

          <Form.Item
            name="modelName"
            label={t('admin.apiKey.fieldModelName')}
            rules={[{ required: true }]}
          >
            <AutoComplete
              options={modelOptions.map((m) => ({ value: m }))}
              placeholder={t('admin.apiKey.modelNamePlaceholder')}
              filterOption={(input, opt) =>
                (opt?.value ?? '').toLowerCase().includes(input.toLowerCase())
              }
            />
          </Form.Item>

          <Form.Item name="authType" label={t('admin.apiKey.fieldAuthType')}>
            <Select options={AUTH_TYPE_OPTIONS} />
          </Form.Item>

          <Form.Item name="remark" label={t('admin.apiKey.fieldRemark')}>
            <Input.TextArea rows={2} placeholder={t('admin.apiKey.remarkPlaceholder')} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
