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
import { mcpApi } from '@/api/skill'
import type { McpTool } from '@/types/skill'
import { PermissionButton } from '@/components/business'
import styles from './McpToolPage.module.css'

const STATUS_CFG = {
  connected: { color: 'success', label: '已连接', icon: <CheckCircleOutlined /> },
  disconnected: { color: 'default', label: '未连接', icon: <DisconnectOutlined /> },
  error: { color: 'error', label: '错误', icon: <ExclamationCircleOutlined /> },
  testing: { color: 'processing', label: '测试中', icon: <SyncOutlined spin /> },
}

const TRANSPORT_OPTIONS = [
  { label: 'stdio', value: 'stdio' },
  { label: 'SSE', value: 'sse' },
  { label: 'HTTP', value: 'http' },
]

export default function McpToolPage() {
  const qc = useQueryClient()
  const { message, modal } = App.useApp()

  const [formOpen, setFormOpen] = useState(false)
  const [editTarget, setEditTarget] = useState<McpTool | null>(null)
  const [form] = Form.useForm<{
    name: string
    description?: string
    transport: string
    endpoint: string
    apiKey?: string
  }>()
  const [testingId, setTestingId] = useState<number | null>(null)

  const { data: tools, isLoading } = useQuery({
    queryKey: ['mcp-tools'],
    queryFn: () => mcpApi.list(),
  })

  const createMutation = useMutation({
    mutationFn: (v: Parameters<typeof mcpApi.create>[0]) => mcpApi.create(v),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['mcp-tools'] })
      setFormOpen(false)
      form.resetFields()
      message.success('已添加')
    },
  })

  const updateMutation = useMutation({
    mutationFn: (v: Parameters<typeof mcpApi.update>[1]) => mcpApi.update(editTarget!.id, v),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['mcp-tools'] })
      setFormOpen(false)
      setEditTarget(null)
      form.resetFields()
      message.success('已更新')
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => mcpApi.delete(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['mcp-tools'] })
      message.success('已删除')
    },
  })

  const handleDelete = (t: McpTool) => {
    modal.confirm({
      title: `删除 MCP 工具「${t.name}」？`,
      okType: 'danger',
      onOk: () => deleteMutation.mutateAsync(t.id),
    })
  }

  const handleOpenCreate = () => {
    setEditTarget(null)
    form.resetFields()
    setFormOpen(true)
  }

  const handleOpenEdit = (t: McpTool) => {
    setEditTarget(t)
    form.setFieldsValue({
      name: t.name,
      description: t.description,
      transport: t.transport,
      endpoint: t.endpoint,
    })
    setFormOpen(true)
  }

  const handleTest = async (t: McpTool) => {
    setTestingId(t.id)
    try {
      const res = await mcpApi.test(t.id)
      if (res.success) {
        qc.invalidateQueries({ queryKey: ['mcp-tools'] })
        message.success(`连接成功，发现 ${res.toolCount} 个工具`)
      } else {
        message.error(`连接失败：${res.message}`)
      }
    } catch {
      message.error('测试连接失败')
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
          <ApiOutlined /> MCP 工具
        </h2>
        <PermissionButton permission="mcp:create">
          <Button
            type="primary"
            icon={<PlusOutlined />}
            style={{ background: 'var(--kf-accent-gradient-r)', border: 'none' }}
            onClick={handleOpenCreate}
          >
            添加 MCP 工具
          </Button>
        </PermissionButton>
      </div>

      <div className={styles.subtitle}>
        通过 MCP（Model Context Protocol）协议连接外部工具，Agent 和工作流可直接调用。
      </div>

      {isLoading ? (
        <div className={styles.listSkeleton}>
          {Array.from({ length: 3 }).map((_, i) => (
            <div key={i} className={styles.skeleton} />
          ))}
        </div>
      ) : !tools?.length ? (
        <Empty description="暂无 MCP 工具配置" />
      ) : (
        <div className={styles.list}>
          {tools.map((t: McpTool, i: number) => {
            const cfg = STATUS_CFG[t.status] ?? STATUS_CFG.disconnected
            return (
              <motion.div
                key={t.id}
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: i * 0.05 }}
                className={styles.card}
              >
                <div className={styles.cardHeader}>
                  <Badge
                    status={
                      t.status === 'connected'
                        ? 'success'
                        : t.status === 'error'
                          ? 'error'
                          : 'default'
                    }
                  />
                  <span className={styles.cardName}>{t.name}</span>
                  <Tag color={cfg.color} icon={cfg.icon}>
                    {cfg.label}
                  </Tag>
                  {t.toolCount > 0 && <Tag color="blue">{t.toolCount} 工具</Tag>}
                </div>
                {t.description && <div className={styles.cardDesc}>{t.description}</div>}
                <Descriptions size="small" column={2}>
                  <Descriptions.Item label="传输协议">
                    {t.transport.toUpperCase()}
                  </Descriptions.Item>
                  <Descriptions.Item label="端点">
                    <span className={styles.endpoint}>{t.endpoint}</span>
                  </Descriptions.Item>
                  {t.lastTestTime && (
                    <Descriptions.Item label="最后连接">
                      {new Date(t.lastTestTime).toLocaleString()}
                    </Descriptions.Item>
                  )}
                </Descriptions>
                <div className={styles.cardActions}>
                  <Space>
                    <Button
                      size="small"
                      icon={<SyncOutlined spin={testingId === t.id} />}
                      loading={testingId === t.id}
                      onClick={() => handleTest(t)}
                    >
                      测试连接
                    </Button>
                    <Tooltip title="编辑">
                      <PermissionButton permission="mcp:update">
                        <Button
                          size="small"
                          icon={<EditOutlined />}
                          onClick={() => handleOpenEdit(t)}
                        />
                      </PermissionButton>
                    </Tooltip>
                    <Tooltip title="删除">
                      <PermissionButton permission="mcp:delete">
                        <Button
                          size="small"
                          danger
                          icon={<DeleteOutlined />}
                          onClick={() => handleDelete(t)}
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
        title={editTarget ? '编辑 MCP 工具' : '添加 MCP 工具'}
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
          <Form.Item name="name" label="名称" rules={[{ required: true }]}>
            <Input placeholder="例：Brave 浏览器搜索" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input placeholder="简要描述此工具功能" />
          </Form.Item>
          <div style={{ display: 'flex', gap: 12 }}>
            <Form.Item
              name="transport"
              label="传输协议"
              initialValue="sse"
              style={{ width: 120 }}
              rules={[{ required: true }]}
            >
              <Select options={TRANSPORT_OPTIONS} />
            </Form.Item>
            <Form.Item
              name="endpoint"
              label="端点地址"
              style={{ flex: 1 }}
              rules={[{ required: true }]}
            >
              <Input
                placeholder="http://localhost:8080/sse"
                style={{ fontFamily: 'var(--kf-font-mono)', fontSize: 13 }}
              />
            </Form.Item>
          </div>
          <Form.Item name="apiKey" label="API Key（可选）">
            <Input.Password placeholder="Bearer token 或 API Key" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
