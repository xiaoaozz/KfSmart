import { useState } from 'react'
import { Button, Modal, Form, Input, Select, App, Tag, Tooltip } from 'antd'
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  CheckCircleOutlined,
  DisconnectOutlined,
  SyncOutlined,
  ExclamationCircleOutlined,
  SearchOutlined,
  ApiOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { motion } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import { mcpApi } from '@/api/skill'
import type { McpTool } from '@/types/skill'
import { GradientButton, GradientCard } from '@/components/base'
import { PermissionButton, PageBar, EmptyState } from '@/components/business'
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

  const [keyword, setKeyword] = useState('')
  const [statusFilter, setStatusFilter] = useState<string | undefined>()
  const [current, setCurrent] = useState(1)
  const [pageSize, setPageSize] = useState(20)
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

  const allTools = data?.records ?? []
  const filteredTools = allTools.filter((tool) => {
    const kw = keyword.toLowerCase()
    const matchesKeyword =
      !kw ||
      tool.name.toLowerCase().includes(kw) ||
      tool.endpoint.toLowerCase().includes(kw) ||
      (tool.description ?? '').toLowerCase().includes(kw)
    const matchesStatus = !statusFilter || tool.status === statusFilter
    return matchesKeyword && matchesStatus
  })
  const tools = filteredTools.slice((current - 1) * pageSize, current * pageSize)

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
      <div className={styles.pageHeader}>
        <div className={styles.topBar}>
          <div className={styles.filters}>
            <Input
              prefix={<SearchOutlined />}
              placeholder={t('skill.mcp.searchPlaceholder')}
              value={keyword}
              onChange={(e) => {
                setKeyword(e.target.value)
                setCurrent(1)
              }}
              style={{ width: 220 }}
              allowClear
            />
            <Select
              placeholder={t('skill.mcp.statusPlaceholder')}
              allowClear
              value={statusFilter}
              onChange={(v) => {
                setStatusFilter(v)
                setCurrent(1)
              }}
              style={{ width: 130 }}
              options={Object.entries(STATUS_CFG).map(([k, v]) => ({ label: v.label, value: k }))}
            />
          </div>
          <PermissionButton permission="mcp:create">
            <GradientButton icon={<PlusOutlined />} onClick={handleOpenCreate}>
              {t('skill.mcp.addBtn')}
            </GradientButton>
          </PermissionButton>
        </div>
      </div>

      <div className={styles.body}>
        {isLoading ? (
          <div className={styles.grid}>
            {Array.from({ length: 6 }).map((_, i) => (
              <div key={i} className={styles.skeleton} />
            ))}
          </div>
        ) : !tools.length ? (
          <EmptyState
            title={t('skill.mcp.empty')}
            description={t('skill.mcp.searchPlaceholder')}
            action={
              <PermissionButton permission="mcp:create">
                <GradientButton icon={<PlusOutlined />} onClick={handleOpenCreate}>
                  {t('skill.mcp.addBtn')}
                </GradientButton>
              </PermissionButton>
            }
          />
        ) : (
          <div className={styles.grid}>
            {tools.map((tool: McpTool, i: number) => {
              const cfg = STATUS_CFG[tool.status] ?? STATUS_CFG['离线']
              return (
                <motion.div
                  key={tool.id}
                  initial={{ opacity: 0, y: 14 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: i * 0.04 }}
                >
                  <GradientCard className={styles.card}>
                    <div className={styles.cardHeader}>
                      <div className={styles.cardIconWrap}>
                        <ApiOutlined />
                      </div>
                      <Tag color={cfg.color} icon={cfg.icon} style={{ fontSize: 12 }}>
                        {cfg.label}
                      </Tag>
                    </div>

                    <h4 className={styles.cardName}>{tool.name}</h4>
                    {tool.description && <p className={styles.cardDesc}>{tool.description}</p>}

                    <div className={styles.cardEndpoint} title={tool.endpoint}>
                      {tool.endpoint}
                    </div>

                    <div className={styles.cardMeta}>
                      <span className={styles.metaItem}>{tool.type}</span>
                      {tool.callCount > 0 && (
                        <span className={styles.metaItem}>
                          <ThunderboltOutlined /> {tool.callCount}
                        </span>
                      )}
                      {tool.lastTestAt && (
                        <span className={styles.metaItem}>
                          {new Date(tool.lastTestAt).toLocaleDateString()}
                        </span>
                      )}
                    </div>

                    <div className={styles.cardActions} onClick={(e) => e.stopPropagation()}>
                      <Tooltip title={t('skill.mcp.testBtn')}>
                        <Button
                          size="small"
                          icon={<SyncOutlined spin={testingId === tool.id} />}
                          loading={testingId === tool.id}
                          className={styles.btnBlue}
                          onClick={() => handleTest(tool)}
                        />
                      </Tooltip>
                      <PermissionButton permission="mcp:update" mode="hide">
                        <Tooltip title={t('skill.mcp.tooltipEdit')}>
                          <Button
                            size="small"
                            icon={<EditOutlined />}
                            className={styles.btnGray}
                            onClick={() => handleOpenEdit(tool)}
                          />
                        </Tooltip>
                      </PermissionButton>
                      <PermissionButton permission="mcp:delete" mode="hide">
                        <Tooltip title={t('skill.mcp.tooltipDelete')}>
                          <Button
                            size="small"
                            icon={<DeleteOutlined />}
                            className={styles.btnRed}
                            onClick={() => handleDelete(tool)}
                          />
                        </Tooltip>
                      </PermissionButton>
                    </div>
                  </GradientCard>
                </motion.div>
              )
            })}
          </div>
        )}
      </div>
      {filteredTools.length > 0 && (
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
            total={filteredTools.length}
            onChange={(page, size) => {
              setCurrent(page)
              setPageSize(size)
            }}
          />
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
