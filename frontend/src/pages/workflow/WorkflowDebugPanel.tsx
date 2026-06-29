import { useState, useCallback, useRef, useEffect } from 'react'
import { Button, App, Form, Input, Tag, Typography, Space, Divider } from 'antd'
import {
  BugOutlined,
  CloseOutlined,
  PlayCircleOutlined,
  ReloadOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  SyncOutlined,
  LoadingOutlined,
  RightOutlined,
} from '@ant-design/icons'
import type { Node } from '@xyflow/react'
import { useMutation } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useAuthStore } from '@/stores/auth'
import { workflowApi } from '@/api/workflow'
import styles from './WorkflowDebugPanel.module.css'

const { Text } = Typography

type StatusMap = Record<string, string>

interface Props {
  nodes: Node[]
  nodeStatusMap: StatusMap
  onStatusChange: (next: StatusMap | ((prev: StatusMap) => StatusMap)) => void
  onClose: () => void
  workflowId: number
}

interface TraceEntry {
  nodeId: string
  nodeType: string
  nodeName?: string
  status: 'pending' | 'running' | 'success' | 'error'
  output?: string
  errorMessage?: string
  durationMs?: number
  order: number
}

const STATUS_ICON = {
  pending: <SyncOutlined style={{ color: '#94a3b8' }} />,
  running: <LoadingOutlined style={{ color: '#3b82f6' }} />,
  success: <CheckCircleOutlined style={{ color: '#10b981' }} />,
  error: <CloseCircleOutlined style={{ color: '#ef4444' }} />,
}

const STATUS_COLOR = {
  pending: 'default' as const,
  running: 'processing' as const,
  success: 'success' as const,
  error: 'error' as const,
}

export default function WorkflowDebugPanel({
  nodes,
  nodeStatusMap: _nodeStatusMap,
  onStatusChange,
  onClose,
  workflowId,
}: Props) {
  const { message } = App.useApp()
  const { t } = useTranslation()
  const token = useAuthStore((s) => s.token)
  const [form] = Form.useForm<{ input: string }>()
  const [trace, setTrace] = useState<TraceEntry[]>([])
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [isRunning, setIsRunning] = useState(false)
  const [finalOutput, setFinalOutput] = useState<string | null>(null)
  const wsRef = useRef<WebSocket | null>(null)
  const orderRef = useRef(0)
  const traceEndRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    traceEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [trace.length])

  useEffect(() => {
    return () => {
      wsRef.current?.close()
    }
  }, [])

  const connectWs = useCallback(
    (executionId: string) => {
      wsRef.current?.close()
      orderRef.current = 0
      const wsBase =
        import.meta.env.VITE_WS_BASE_URL ?? window.location.origin.replace(/^http/, 'ws')
      const ws = new WebSocket(`${wsBase}/ws/workflow/${executionId}?token=${token}`)
      wsRef.current = ws

      ws.onmessage = (event) => {
        try {
          const frame = JSON.parse(event.data as string) as Record<string, unknown>
          const type = frame.type as string

          if (type === 'node_completed' || type === 'node_progress') {
            // Server sends node info under "node" key OR at top-level
            const node = (frame.node ?? frame) as Record<string, unknown>
            const nodeId = node.nodeId as string
            const nodeType = node.nodeType as string
            const status = (node.status ?? 'success') as TraceEntry['status']
            const durationMs = node.durationMs as number | undefined
            const errorMessage = node.errorMessage as string | undefined
            const output = node.output as string | undefined
            const nodeName = node.nodeName as string | undefined

            const order = orderRef.current++

            onStatusChange((prev: StatusMap) => ({ ...prev, [nodeId]: status }))
            setTrace((prev) => {
              const existing = prev.findIndex((e) => e.nodeId === nodeId)
              const entry: TraceEntry = {
                nodeId,
                nodeType: nodeType ?? nodes.find((n) => n.id === nodeId)?.type ?? 'unknown',
                nodeName,
                status,
                durationMs,
                errorMessage,
                output,
                order: existing >= 0 ? prev[existing].order : order,
              }
              if (existing >= 0) {
                return prev.map((e, i) => (i === existing ? entry : e))
              }
              return [...prev, entry]
            })
          }

          if (type === 'execution_completed') {
            const success = frame.success as boolean
            setFinalOutput(
              success ? t('workflow.debug.execSuccess') : t('workflow.debug.execFailed'),
            )
            setIsRunning(false)
          }

          if (type === 'execution_failed') {
            const err = frame.error as string
            setFinalOutput(`${t('workflow.debug.execFailed')}: ${err}`)
            setIsRunning(false)
          }
        } catch {
          // ignore malformed frames
        }
      }

      ws.onclose = () => {
        setIsRunning(false)
      }
      ws.onerror = () => {
        setIsRunning(false)
        message.error(t('workflow.debug.wsError'))
      }
    },
    [token, nodes, onStatusChange, message, t],
  )

  const runMutation = useMutation({
    mutationFn: (input: string) => workflowApi.run(workflowId, input),
    onSuccess: (res) => {
      const pending: StatusMap = {}
      nodes.forEach((n) => (pending[n.id] = 'pending'))
      onStatusChange(pending)
      setTrace(
        nodes.map((n, i) => ({
          nodeId: n.id,
          nodeType: n.type ?? 'unknown',
          status: 'pending',
          order: i,
        })),
      )
      setFinalOutput(null)
      setIsRunning(true)
      connectWs(res.executionId)
    },
    onError: () => {
      message.error(t('common.requestFailed'))
    },
  })

  const handleClear = () => {
    onStatusChange({})
    setTrace([])
    setFinalOutput(null)
    setSelectedId(null)
    form.resetFields()
  }

  const sortedTrace = [...trace].sort((a, b) => a.order - b.order)

  return (
    <div className={styles.panel}>
      {/* Header */}
      <div className={styles.header}>
        <span className={styles.headerTitle}>
          <BugOutlined /> {t('workflow.debug.title')}
        </span>
        <Button type="text" size="small" icon={<CloseOutlined />} onClick={onClose} />
      </div>

      {/* Input + Actions */}
      <div className={styles.inputSection}>
        <Form form={form} layout="vertical" onFinish={(v) => runMutation.mutate(v.input ?? '')}>
          <Form.Item
            name="input"
            label={t('workflow.debug.inputLabel')}
            rules={[{ required: true }]}
          >
            <Input.TextArea
              rows={3}
              placeholder={t('workflow.debug.inputPlaceholder')}
              style={{ fontFamily: 'var(--kf-font-mono)', fontSize: 12 }}
            />
          </Form.Item>
        </Form>
        <Space>
          <Button
            type="primary"
            icon={<PlayCircleOutlined />}
            loading={runMutation.isPending || isRunning}
            onClick={() => form.submit()}
            style={{ background: 'var(--kf-accent-gradient-r)', border: 'none' }}
          >
            {t('workflow.debug.runBtn')}
          </Button>
          <Button icon={<ReloadOutlined />} onClick={handleClear}>
            {t('workflow.debug.clearBtn')}
          </Button>
        </Space>
      </div>

      <Divider style={{ margin: '8px 0' }} />

      {/* Execution Chain */}
      <div className={styles.chainSection}>
        <div className={styles.sectionTitle}>{t('workflow.debug.logTitle')}</div>
        {sortedTrace.length === 0 ? (
          <div className={styles.emptyHint}>{t('workflow.debug.emptyLog')}</div>
        ) : (
          <div className={styles.chain}>
            {sortedTrace.map((entry, idx) => (
              <div key={entry.nodeId}>
                <div
                  className={`${styles.chainNode} ${selectedId === entry.nodeId ? styles.chainNodeSelected : ''}`}
                  onClick={() => setSelectedId(entry.nodeId === selectedId ? null : entry.nodeId)}
                >
                  <span className={styles.chainIcon}>
                    {STATUS_ICON[entry.status] ?? STATUS_ICON.pending}
                  </span>
                  <span className={styles.chainLabel}>{entry.nodeName ?? entry.nodeId}</span>
                  <Tag
                    color={STATUS_COLOR[entry.status]}
                    style={{ marginLeft: 'auto', flexShrink: 0 }}
                  >
                    {entry.durationMs != null
                      ? `${(entry.durationMs / 1000).toFixed(2)}s`
                      : t(`workflow.debug.${entry.status}`)}
                  </Tag>
                  <RightOutlined
                    className={`${styles.chevron} ${selectedId === entry.nodeId ? styles.chevronOpen : ''}`}
                  />
                </div>

                {/* Inline detail expand */}
                {selectedId === entry.nodeId && (
                  <div className={styles.chainDetail}>
                    <div className={styles.detailRow}>
                      <Text type="secondary" style={{ fontSize: 11 }}>
                        Type
                      </Text>
                      <Text code style={{ fontSize: 11 }}>
                        {entry.nodeType}
                      </Text>
                    </div>
                    {entry.errorMessage && (
                      <div className={styles.detailRow}>
                        <Text type="danger" style={{ fontSize: 11 }}>
                          {entry.errorMessage}
                        </Text>
                      </div>
                    )}
                    {entry.output && (
                      <div className={styles.detailOutput}>
                        <pre>{entry.output}</pre>
                      </div>
                    )}
                  </div>
                )}

                {/* Connector arrow (not after last) */}
                {idx < sortedTrace.length - 1 && (
                  <div className={styles.connector}>
                    <div className={styles.connectorLine} />
                    <div className={styles.connectorArrow} />
                  </div>
                )}
              </div>
            ))}
            <div ref={traceEndRef} />
          </div>
        )}
      </div>

      {/* Final output */}
      {finalOutput && (
        <>
          <Divider style={{ margin: '8px 0' }} />
          <div
            className={`${styles.finalOutput} ${finalOutput.includes(t('workflow.debug.execFailed')) ? styles.finalOutputError : styles.finalOutputSuccess}`}
          >
            {finalOutput}
          </div>
        </>
      )}
    </div>
  )
}
