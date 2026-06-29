import { useState, useEffect, useRef } from 'react'
import { Tag, Collapse, Button, App, Modal, Form, Input, Descriptions, Drawer, Badge } from 'antd'
import {
  ArrowLeftOutlined,
  ThunderboltOutlined,
  PlayCircleOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  SyncOutlined,
} from '@ant-design/icons'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useParams, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useAuthStore } from '@/stores/auth'
import { workflowApi } from '@/api/workflow'
import type { WorkflowRun } from '@/types/workflow'
import PageTable, { type TableColumnType } from '@/components/business/PageTable'
import styles from './WorkflowExecutionPage.module.css'

interface NodeProgress {
  nodeId: string
  nodeType?: string
  nodeName?: string
  status: 'running' | 'success' | 'error'
  output?: string
  durationMs?: number
  errorMessage?: string
}

function formatMs(ms: number) {
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(2)}s`
}

/** Render a backend IO JSON string readably. Input is stored as `{"input":"..."}`;
 *  extract the inner text. Other JSON is pretty-printed; non-JSON falls back to raw. */
function displayIo(v?: string | null): string {
  if (!v) return ''
  try {
    const parsed = JSON.parse(v)
    if (
      parsed != null &&
      typeof parsed === 'object' &&
      'input' in parsed &&
      typeof (parsed as Record<string, unknown>).input === 'string'
    ) {
      return (parsed as Record<string, string>).input
    }
    return typeof parsed === 'string' ? parsed : JSON.stringify(parsed, null, 2)
  } catch {
    return v
  }
}

/** Safe date formatter — never returns "Invalid Date". */
function formatDate(v?: string | null): string {
  if (!v) return '—'
  const d = new Date(v)
  return Number.isNaN(d.getTime()) ? '—' : d.toLocaleString()
}

export default function WorkflowExecutionPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const { message } = App.useApp()
  const { t } = useTranslation()
  const token = useAuthStore((s) => s.token)

  const [current, setCurrent] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [runModalOpen, setRunModalOpen] = useState(false)
  const [selectedRun, setSelectedRun] = useState<WorkflowRun | null>(null)
  const [nodeProgress, setNodeProgress] = useState<NodeProgress[]>([])
  const [runningExecutionId, setRunningExecutionId] = useState<string | null>(null)
  const wsRef = useRef<WebSocket | null>(null)
  const [form] = Form.useForm<{ input: string }>()

  const STATUS_CFG = {
    running: {
      color: 'processing',
      label: t('workflow.executions.statusRunning'),
      icon: <SyncOutlined spin />,
    },
    success: {
      color: 'success',
      label: t('workflow.executions.statusSuccess'),
      icon: <CheckCircleOutlined />,
    },
    failed: {
      color: 'error',
      label: t('workflow.executions.statusFailed'),
      icon: <CloseCircleOutlined />,
    },
  }

  const { data: wf } = useQuery({
    queryKey: ['workflows', id],
    queryFn: () => workflowApi.get(Number(id)),
    enabled: !!id,
  })

  const { data, isLoading } = useQuery({
    queryKey: ['workflow-executions', id, current, pageSize],
    queryFn: () => workflowApi.listRuns(Number(id), { current, size: pageSize }),
    enabled: !!id,
    refetchInterval: (query) =>
      query.state.data?.records?.some((e) => e.status === 'running') ? 3000 : false,
  })

  const runMutation = useMutation({
    mutationFn: (input: string) => workflowApi.run(Number(id), input),
    onSuccess: (run) => {
      qc.invalidateQueries({ queryKey: ['workflow-executions', id] })
      setRunModalOpen(false)
      form.resetFields()
      message.success(t('workflow.executions.runSuccess'))
      setRunningExecutionId(run.executionId)
      connectProgressWs(run.executionId)
    },
    onError: () => {
      message.error(t('common.operationFailed', { defaultValue: '操作失败' }))
    },
  })

  // WebSocket connection for real-time node progress
  function connectProgressWs(executionId: string) {
    if (wsRef.current) {
      wsRef.current.close()
    }
    const wsBase = import.meta.env.VITE_WS_BASE_URL ?? window.location.origin.replace(/^http/, 'ws')
    const ws = new WebSocket(`${wsBase}/ws/workflow/${executionId}?token=${token}`)
    wsRef.current = ws

    ws.onopen = () => {
      ws.send(JSON.stringify({ type: 'subscribe', executionId }))
    }

    ws.onmessage = (event) => {
      try {
        const frame = JSON.parse(event.data) as Record<string, unknown> & { type: string }
        if (frame.type === 'node_completed' || frame.type === 'node_progress') {
          const node = (frame.node ?? frame) as NodeProgress
          setNodeProgress((prev) => {
            const idx = prev.findIndex((p) => p.nodeId === node.nodeId)
            if (idx >= 0) {
              const next = [...prev]
              next[idx] = { nodeId: node.nodeId, status: node.status, output: node.output }
              return next
            }
            return [...prev, { nodeId: node.nodeId, status: node.status, output: node.output }]
          })
        }
        if (frame.type === 'execution_completed' || frame.type === 'execution_failed') {
          qc.invalidateQueries({ queryKey: ['workflow-executions', id] })
        }
      } catch {
        // ignore parse errors
      }
    }

    ws.onclose = () => {
      qc.invalidateQueries({ queryKey: ['workflow-executions', id] })
      setRunningExecutionId(null)
    }
  }

  useEffect(() => {
    return () => {
      wsRef.current?.close()
    }
  }, [])

  const handleCopy = (text: string) => {
    navigator.clipboard
      .writeText(text)
      .then(() => message.success(t('workflow.executions.copySuccess')))
  }

  const columns: TableColumnType<WorkflowRun>[] = [
    {
      title: t('workflow.executions.colStatus'),
      dataIndex: 'status',
      width: 110,
      render: (s: WorkflowRun['status']) => {
        const cfg = STATUS_CFG[s] ?? { color: 'default' as const, label: s, icon: null }
        return (
          <Tag color={cfg.color} icon={cfg.icon}>
            {cfg.label}
          </Tag>
        )
      },
    },
    {
      title: t('workflow.executions.colInput'),
      dataIndex: 'inputJson',
      ellipsis: true,
      render: (v: string) => {
        const text = displayIo(v)
        return text ? text.slice(0, 80) : '—'
      },
    },
    {
      title: t('workflow.executions.colOutput'),
      dataIndex: 'outputJson',
      ellipsis: true,
      render: (v: string) => {
        const text = displayIo(v)
        return text ? text.slice(0, 80) : '—'
      },
    },
    {
      title: t('workflow.executions.colTokens'),
      dataIndex: 'totalTokens',
      width: 90,
      render: (n: number) => (n != null ? n.toLocaleString() : '—'),
    },
    {
      title: t('workflow.executions.colDuration'),
      dataIndex: 'durationMs',
      width: 90,
      render: (ms: number) => (ms != null ? formatMs(ms) : '—'),
    },
    {
      title: t('workflow.executions.colTime'),
      dataIndex: 'startedAt',
      width: 160,
      render: (v: string) => formatDate(v),
    },
    {
      title: '',
      key: 'action',
      width: 80,
      render: (_: unknown, row: WorkflowRun) => (
        <Button size="small" onClick={() => setSelectedRun(row)}>
          {t('common.detail', { defaultValue: '详情' })}
        </Button>
      ),
    },
  ]

  return (
    <div className={styles.root}>
      <div className={styles.pageHeader}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/workflows')}>
          {t('workflow.executions.backBtn')}
        </Button>
        <h2 className={styles.title}>
          <ThunderboltOutlined />
          {wf ? `${wf.name} — ` : ''}
          {t('workflow.executions.title')}
        </h2>
        <Button
          type="primary"
          icon={<PlayCircleOutlined />}
          style={{ background: 'var(--kf-accent-gradient-r)', border: 'none', marginLeft: 'auto' }}
          onClick={() => setRunModalOpen(true)}
          loading={runMutation.isPending}
        >
          {t('workflow.executions.runBtn')}
        </Button>
      </div>

      {runningExecutionId && nodeProgress.length > 0 && (
        <div className={styles.progressBar}>
          <span className={styles.progressLabel}>
            <SyncOutlined spin /> {t('workflow.executions.statusRunning')}
          </span>
          <div className={styles.progressNodes}>
            {nodeProgress.map((np) => (
              <Badge
                key={np.nodeId}
                status={
                  np.status === 'running'
                    ? 'processing'
                    : np.status === 'success'
                      ? 'success'
                      : 'error'
                }
                text={
                  <span className={styles.progressNodeText}>
                    {np.nodeId}
                    <Tag
                      color={
                        np.status === 'running'
                          ? 'processing'
                          : np.status === 'success'
                            ? 'success'
                            : 'error'
                      }
                      style={{ marginLeft: 4 }}
                    >
                      {t(`workflow.executions.nodeStatus.${np.status}`)}
                    </Tag>
                  </span>
                }
              />
            ))}
          </div>
        </div>
      )}

      <div className={styles.body}>
        <PageTable<WorkflowRun>
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
          expandable={{
            expandedRowRender: (row) => {
              const inputText = displayIo(row.inputJson)
              const outputText = displayIo(row.outputJson)
              return (
                <Collapse
                  size="small"
                  items={[
                    {
                      key: 'input',
                      label: t('workflow.executions.expandInput'),
                      children: (
                        <div className={styles.codeBlock}>
                          <pre>{inputText || '—'}</pre>
                          {inputText && (
                            <Button size="small" onClick={() => handleCopy(inputText)}>
                              {t('common.copy')}
                            </Button>
                          )}
                        </div>
                      ),
                    },
                    ...(outputText
                      ? [
                          {
                            key: 'output',
                            label: t('workflow.executions.expandOutput'),
                            children: (
                              <div className={styles.codeBlock}>
                                <pre>{outputText}</pre>
                                <Button size="small" onClick={() => handleCopy(outputText)}>
                                  {t('common.copy')}
                                </Button>
                              </div>
                            ),
                          },
                        ]
                      : []),
                  ]}
                />
              )
            },
          }}
        />
      </div>

      {/* Run Modal */}
      <Modal
        title={t('workflow.executions.runModalTitle')}
        open={runModalOpen}
        onCancel={() => {
          setRunModalOpen(false)
          form.resetFields()
        }}
        onOk={() => form.submit()}
        okText={t('workflow.executions.runModalOk')}
        confirmLoading={runMutation.isPending}
        destroyOnClose
      >
        <Form form={form} layout="vertical" onFinish={(v) => runMutation.mutate(v.input ?? '')}>
          <Form.Item
            name="input"
            label={t('workflow.executions.runInputLabel')}
            rules={[{ required: true, message: t('workflow.executions.runInputPlaceholder') }]}
          >
            <Input.TextArea
              rows={5}
              placeholder={t('workflow.executions.runInputPlaceholder')}
              style={{ fontFamily: 'var(--kf-font-mono)', fontSize: 13 }}
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* Detail Drawer */}
      <Drawer
        title={`#${selectedRun?.id} — ${selectedRun ? STATUS_CFG[selectedRun.status]?.label : ''}`}
        open={!!selectedRun}
        onClose={() => setSelectedRun(null)}
        width={520}
      >
        {selectedRun && (
          <div className={styles.detailContent}>
            <Descriptions bordered size="small" column={1}>
              <Descriptions.Item label={t('workflow.executions.colStatus')}>
                <Tag color={STATUS_CFG[selectedRun.status]?.color}>
                  {STATUS_CFG[selectedRun.status]?.label}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label={t('workflow.executions.colDuration')}>
                {selectedRun.durationMs != null ? formatMs(selectedRun.durationMs) : '—'}
              </Descriptions.Item>
              <Descriptions.Item label={t('workflow.executions.colTokens')}>
                {selectedRun.totalTokens != null ? selectedRun.totalTokens.toLocaleString() : '—'}
              </Descriptions.Item>
              <Descriptions.Item label={t('workflow.executions.colTime')}>
                {formatDate(selectedRun.startedAt)}
              </Descriptions.Item>
            </Descriptions>

            <div className={styles.ioSection}>
              <div className={styles.ioLabel}>{t('workflow.executions.expandInput')}</div>
              <div className={styles.codeBlock}>
                <pre>{displayIo(selectedRun.inputJson) || '—'}</pre>
                {displayIo(selectedRun.inputJson) && (
                  <Button
                    size="small"
                    onClick={() => handleCopy(displayIo(selectedRun.inputJson)!)}
                  >
                    {t('common.copy')}
                  </Button>
                )}
              </div>
            </div>

            {displayIo(selectedRun.outputJson) && (
              <div className={styles.ioSection}>
                <div className={styles.ioLabel}>{t('workflow.executions.expandOutput')}</div>
                <div className={styles.codeBlock}>
                  <pre>{displayIo(selectedRun.outputJson)}</pre>
                  <Button
                    size="small"
                    onClick={() => handleCopy(displayIo(selectedRun.outputJson)!)}
                  >
                    {t('common.copy')}
                  </Button>
                </div>
              </div>
            )}
          </div>
        )}
      </Drawer>
    </div>
  )
}
