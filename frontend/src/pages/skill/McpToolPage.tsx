import { useState } from 'react'
import {
  Button,
  Modal,
  Form,
  Input,
  Select,
  App,
  Empty,
  Tag,
  Badge,
  Descriptions,
  Space,
  Tooltip,
} from 'antd'
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  ApiOutlined,
  CheckCircleOutlined,
  DisconnectOutlined,
  SyncOutlined,
  ExclamationCircleOutlined,
} from '@ant-design/icons'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { motion } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import { mcpApi } from '@/api/skill'
import type { McpTool } from '@/types/skill'
import { PermissionButton } from '@/components/business'
import styles from './McpToolPage.module.css'

const TYPE_OPTIONS = [
  { label: 'MCP', value: 'MCP' },
  { label: 'HTTP', value: 'HTTP' },
  { label: 'SSE', value: 'SSE' },
  { label: 'stdio', value: 'stdio' },
]

export default function McpToolPage() {
  const qc = useQueryClient()
  const { message, modal } = App.useApp()
  const { t } = useTranslation()

  const STATUS_CFG: Record<string, { color: string; label: string; icon: React.ReactNode }> = {
    在线: {
      color: 'success',
      label: t('skill.mcp.statusConnected'),
      icon: <CheckCircleOutlined />,
    },
    离线: {
      color: 'default',
      label: t('skill.mcp.statusDisconnected'),
      icon: <DisconnectOutlined />,
    },
    错误: {
      color: 'error',
      label: t('skill.mcp.statusError'),
      icon: <ExclamationCircleOutlined />,
    },
    测试中: {
      color: 'processing',
      label: t('skill.mcp.statusTesting'),
      icon: <SyncOutlined spin />,
    },
  }

  const [formOpen, setFormOpen] = useState(false)
  const [editTarget, setEditTarget] = useState<McpTool | null>(null)
  const [form] = Form.useForm<{
    name: string
    description?: string
    type: string
    endpoint: string
    apiKey?: string
  }>()
  const [testingId, setTestingId] = useState<number | null>(null)

  const { data, isLoading } = useQuery({
    queryKey: ['mcp-tools'],
    queryFn: () => mcpApi.list(),
  })

  const tools = data?.records ?? []

  const createMutation = useMutation({
    mutationFn: (v: {
      name: string
      description?: string
      type: string
      endpoint: string
      apiKey?: string
    }) => mcpApi.create(v),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['mcp-tools'] })
      setFormOpen(false)
      form.resetFields()
      message.success(t('skill.mcp.addSuccess'))
    },
  })

  const updateMutation = useMutation({
    mutationFn: (v: {
      name: string
      description?: string
      type: string
      endpoint: string
      apiKey?: string
    }) => mcpApi.update(editTarget!.toolId, v),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['mcp-tools'] })
      setFormOpen(false)
      setEditTarget(null)
      form.resetFields()
      message.success(t('skill.mcp.updateSuccess'))
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (toolId: string) => mcpApi.delete(toolId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['mcp-tools'] })
      message.success(t('skill.mcp.deleteSuccess'))
    },
  })

  const handleDelete = (tool: McpTool) => {
    modal.confirm({
      title: t('skill.mcp.deleteConfirm', { name: tool.name }),
      okType: 'danger',
      onOk: () => deleteMutation.mutateAsync(tool.toolId),
    })
  }

  const handleOpenCreate = () => {
    setEditTarget(null)
    form.resetFields()
    setFormOpen(true)
  }

  const handleOpenEdit = (tool: McpTool) => {
    setEditTarget(tool)
    form.setFieldsValue({
      name: tool.name,
      description: tool.description,
      type: tool.type,
      endpoint: tool.endpoint,
    })
    setFormOpen(true)
  }

  const handleTest = async (tool: McpTool) => {
    setTestingId(tool.id)
    try {
      const res = await mcpApi.test(tool.toolId, {})
      if (res.success) {
        qc.invalidateQueries({ queryKey: ['mcp-tools'] })
        message.success(t('skill.mcp.testSuccess'))
      } else {
        message.error(t('skill.mcp.testFailed', { msg: res.message }))
      }
    } catch {
      message.error(t('skill.mcp.testError'))
    } finally {
      setTestingId(null)
    }
  }

  const handleSubmit = () => {
    form.validateFields().then((v) => {
      if (editTarget) updateMutation.mutate(v)
      else createMutation.mutate(v)
    })
  }

  return (
    <div className={styles.root}>
      <div className={styles.topBar}>
        <h2 className={styles.pageTitle}>
          <ApiOutlined /> {t('skill.mcp.title')}
        </h2>
        <PermissionButton permission="mcp:create">
          <Button
            type="primary"
            icon={<PlusOutlined />}
            style={{ background: 'var(--kf-accent-gradient-r)', border: 'none' }}
            onClick={handleOpenCreate}
          >
            {t('skill.mcp.addBtn')}
          </Button>
        </PermissionButton>
      </div>

      <div className={styles.subtitle}>{t('skill.mcp.subtitle')}</div>

      {isLoading ? (
        <div className={styles.listSkeleton}>
          {Array.from({ length: 3 }).map((_, i) => (
            <div key={i} className={styles.skeleton} />
          ))}
        </div>
      ) : !tools.length ? (
        <Empty description={t('skill.mcp.empty')} />
      ) : (
        <div className={styles.list}>
          {tools.map((tool: McpTool, i: number) => {
            const cfg = STATUS_CFG[tool.status] ?? STATUS_CFG['离线']
            return (
              <motion.div
                key={tool.id}
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: i * 0.05 }}
                className={styles.card}
              >
                <div className={styles.cardHeader}>
                  <Badge
                    status={
                      tool.status === '在线'
                        ? 'success'
                        : tool.status === '错误'
                          ? 'error'
                          : 'default'
                    }
                  />
                  <span className={styles.cardName}>{tool.name}</span>
                  <Tag color={cfg.color} icon={cfg.icon}>
                    {cfg.label}
                  </Tag>
                  {tool.callCount > 0 && (
                    <Tag color="blue">{t('skill.mcp.toolCount', { count: tool.callCount })}</Tag>
                  )}
                </div>
                {tool.description && <div className={styles.cardDesc}>{tool.description}</div>}
                <Descriptions size="small" column={2}>
                  <Descriptions.Item label={t('skill.mcp.descTransport')}>
                    {tool.type}
                  </Descriptions.Item>
                  <Descriptions.Item label={t('skill.mcp.descEndpoint')}>
                    <span className={styles.endpoint}>{tool.endpoint}</span>
                  </Descriptions.Item>
                  {tool.lastTestAt && (
                    <Descriptions.Item label={t('skill.mcp.descLastTest')}>
                      {new Date(tool.lastTestAt).toLocaleString()}
                    </Descriptions.Item>
                  )}
                </Descriptions>
                <div className={styles.cardActions}>
                  <Space>
                    <Button
                      size="small"
                      icon={<SyncOutlined spin={testingId === tool.id} />}
                      loading={testingId === tool.id}
                      onClick={() => handleTest(tool)}
                    >
                      {t('skill.mcp.testBtn')}
                    </Button>
                    <Tooltip title={t('skill.mcp.tooltipEdit')}>
                      <PermissionButton permission="mcp:update">
                        <Button
                          size="small"
                          icon={<EditOutlined />}
                          onClick={() => handleOpenEdit(tool)}
                        />
                      </PermissionButton>
                    </Tooltip>
                    <Tooltip title={t('skill.mcp.tooltipDelete')}>
                      <PermissionButton permission="mcp:delete">
                        <Button
                          size="small"
                          danger
                          icon={<DeleteOutlined />}
                          onClick={() => handleDelete(tool)}
                        />
                      </PermissionButton>
                    </Tooltip>
                  </Space>
                </div>
              </motion.div>
            )
          })}
        </div>
      )}

      <Modal
        title={editTarget ? t('skill.mcp.editModalTitle') : t('skill.mcp.addModalTitle')}
        open={formOpen}
        onCancel={() => {
          setFormOpen(false)
          form.resetFields()
          setEditTarget(null)
        }}
        onOk={handleSubmit}
        confirmLoading={createMutation.isPending || updateMutation.isPending}
        destroyOnClose
        width={520}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label={t('skill.mcp.fieldName')} rules={[{ required: true }]}>
            <Input placeholder={t('skill.mcp.namePlaceholder')} />
          </Form.Item>
          <Form.Item name="description" label={t('skill.mcp.fieldDesc')}>
            <Input placeholder={t('skill.mcp.descPlaceholder')} />
          </Form.Item>
          <div style={{ display: 'flex', gap: 12 }}>
            <Form.Item
              name="type"
              label={t('skill.mcp.fieldTransport')}
              initialValue="MCP"
              style={{ width: 120 }}
              rules={[{ required: true }]}
            >
              <Select options={TYPE_OPTIONS} />
            </Form.Item>
            <Form.Item
              name="endpoint"
              label={t('skill.mcp.fieldEndpoint')}
              style={{ flex: 1 }}
              rules={[{ required: true }]}
            >
              <Input
                placeholder="http://localhost:8080/sse"
                style={{ fontFamily: 'var(--kf-font-mono)', fontSize: 13 }}
              />
            </Form.Item>
          </div>
          <Form.Item name="apiKey" label={t('skill.mcp.fieldApiKey')}>
            <Input.Password placeholder={t('skill.mcp.apiKeyPlaceholder')} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
