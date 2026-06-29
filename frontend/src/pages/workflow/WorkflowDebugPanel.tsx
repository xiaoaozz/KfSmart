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
import type { StartNodeData, StartNodeVariable } from '@/types/workflow'
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
  outputs?: Record<string, unknown>
  errorMessage?: string
  durationMs?: number
  startedAt?: number
  promptTokens?: number
  completionTokens?: number
  description?: string
  inputs?: Record<string, unknown>
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

function formatDuration(ms?: number): string {
  if (ms == null) return ''
  return `${(ms / 1000).toFixed(2)}s`
}

function formatTotalTokens(prompt?: number, completion?: number): string | null {
  const p = prompt ?? 0
  const c = completion ?? 0
  const total = p + c
  return total > 0 ? `${total} tok` : null
}

function formatTimestamp(ts?: number): string | null {
  if (!ts) return null
  const d = new Date(ts)
  return d.toLocaleTimeString('zh-CN', { hour12: false })
}

function tryStringifyJson(value: unknown): string | null {
  if (value == null) return null
  if (typeof value === 'string') return value
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value)
  }
}

function extractOutputText(outputs?: Record<string, unknown>): string | undefined {
  if (!outputs) return undefined
  const out = (outputs as Record<string, unknown>).output
  const ans = (outputs as Record<string, unknown>).answer
  if (typeof out === 'string') return out
  if (typeof ans === 'string') return ans
  return undefined
}

function getStartVariables(nodes: Node[]): StartNodeVariable[] {
  const startNode = nodes.find((n) => n.type === 'start')
  if (!startNode) return [{ name: 'query', value: '' }]
  const data = startNode.data as unknown as StartNodeData
  const vars = data?.variables
  if (!vars || vars.length === 0) return [{ name: 'query', value: '' }]
  return vars
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
  const [form] = Form.useForm<Record<string, string>>()
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

      ws.onopen = () => {
        ws.send(JSON.stringify({ type: 'subscribe', executionId }))
      }

      ws.onmessage = (event) => {
        try {
          const frame = JSON.parse(event.data as string) as Record<string, unknown>
          const type = frame.type as string

          if (type === 'node_completed' || type === 'node_progress') {
            const node = (frame.node ?? frame) as Record<string, unknown>
            const nodeId = node.nodeId as string
            const nodeType = node.nodeType as string
            const status = (node.status ?? 'success') as TraceEntry['status']
            const durationMs = node.durationMs as number | undefined
            const errorMessage = node.errorMessage as string | undefined
            const nodeName = node.nodeName as string | undefined
            const outputs = node.outputs as Record<string, unknown> | undefined
            const description = node.description as string | undefined
            const promptTokens = node.promptTokens as number | undefined
            const completionTokens = node.completionTokens as number | undefined
            const startedAt = node.startedAt as number | undefined
            const inputs = node.inputs as Record<string, unknown> | undefined
            const output = extractOutputText(outputs) ?? (node.output as string | undefined)

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
                outputs,
                description,
                promptTokens,
                completionTokens,
                startedAt,
                inputs,
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

  const startVars = getStartVariables(nodes)
  const isMultiVar =
    startVars.length > 1 || (startVars.length === 1 && startVars[0].name !== 'input')

  const runMutation = useMutation({
    mutationFn: (input: string | Record<string, string>) => workflowApi.run(workflowId, input),
    onSuccess: (res) => {
      const pending: StatusMap = {}
      nodes.forEach((n) => (pending[n.id] = 'pending'))
      onStatusChange(pending)
      setTrace([])
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
        <Form
          form={form}
          layout="vertical"
          onFinish={(v) => {
            if (isMultiVar) {
              runMutation.mutate(v as Record<string, string>)
            } else {
              runMutation.mutate((v as Record<string, string>).input ?? '')
            }
          }}
        >
          {isMultiVar ? (
            startVars.map((variable) => (
              <Form.Item
                key={variable.name}
                name={variable.name}
                label={variable.name}
                initialValue={variable.value}
              >
                <Input.TextArea
                  rows={2}
                  placeholder={variable.value || t('workflow.debug.inputPlaceholder')}
                  style={{ fontFamily: 'var(--kf-font-mono)', fontSize: 12 }}
                />
              </Form.Item>
            ))
          ) : (
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
          )}
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
            {sortedTrace.map((entry, idx) => {
              const durStr = formatDuration(entry.durationMs)
              const tokStr = formatTotalTokens(entry.promptTokens, entry.completionTokens)
              const tagText = [durStr, tokStr].filter(Boolean).join(' · ')
              return (
                <div key={entry.nodeId}>
                  <div
                    className={`${styles.chainNode} ${selectedId === entry.nodeId ? styles.chainNodeSelected : ''}`}
                    onClick={() => setSelectedId(entry.nodeId === selectedId ? null : entry.nodeId)}
                  >
                    <span className={styles.chainIcon}>
                      {STATUS_ICON[entry.status] ?? STATUS_ICON.pending}
                    </span>
                    <span className={styles.chainLabel}>{entry.nodeName ?? entry.nodeId}</span>
                    {tagText ? (
                      <Tag
                        color={STATUS_COLOR[entry.status]}
                        style={{ marginLeft: 'auto', flexShrink: 0, fontSize: 10 }}
                      >
                        {tagText}
                      </Tag>
                    ) : (
                      <Tag
                        color={STATUS_COLOR[entry.status]}
                        style={{ marginLeft: 'auto', flexShrink: 0 }}
                      >
                        {t(`workflow.debug.${entry.status}`)}
                      </Tag>
                    )}
                    <RightOutlined
                      className={`${styles.chevron} ${selectedId === entry.nodeId ? styles.chevronOpen : ''}`}
                    />
                  </div>

                  {/* Inline detail expand */}
                  {selectedId === entry.nodeId && (
                    <div className={styles.chainDetail}>
                      {/* Basic info row */}
                      <div className={styles.detailMetaRow}>
                        <span className={styles.metaTag}>
                          <Text code style={{ fontSize: 10 }}>
                            {entry.nodeType}
                          </Text>
                        </span>
                        {durStr && (
                          <span className={styles.metaTag}>
                            <Text type="secondary" style={{ fontSize: 10 }}>
                              ⏱ {durStr}
                            </Text>
                          </span>
                        )}
                        {formatTimestamp(entry.startedAt) && (
                          <span className={styles.metaTag}>
                            <Text type="secondary" style={{ fontSize: 10 }}>
                              🕐 {formatTimestamp(entry.startedAt)}
                            </Text>
                          </span>
                        )}
                      </div>

                      {/* Token usage */}
                      {entry.promptTokens != null &&
                        entry.completionTokens != null &&
                        (entry.promptTokens > 0 || entry.completionTokens > 0) && (
                          <div className={styles.tokenBar}>
                            <span className={styles.tokenItem}>
                              <Text type="secondary" style={{ fontSize: 10 }}>
                                Prompt
                              </Text>
                              <span className={styles.tokenValue}>{entry.promptTokens}</span>
                            </span>
                            <span className={styles.tokenItem}>
                              <Text type="secondary" style={{ fontSize: 10 }}>
                                Completion
                              </Text>
                              <span className={styles.tokenValue}>{entry.completionTokens}</span>
                            </span>
                            <span className={styles.tokenItem}>
                              <Text type="secondary" style={{ fontSize: 10 }}>
                                Total
                              </Text>
                              <span className={styles.tokenValueTotal}>
                                {entry.promptTokens + entry.completionTokens}
                              </span>
                            </span>
                          </div>
                        )}

                      {/* Description */}
                      {entry.description && (
                        <div className={styles.detailDesc}>
                          <Text style={{ fontSize: 11 }}>{entry.description}</Text>
                        </div>
                      )}

                      {/* Error */}
                      {entry.errorMessage && (
                        <div className={styles.detailError}>
                          <Text type="danger" style={{ fontSize: 11 }}>
                            {entry.errorMessage}
                          </Text>
                        </div>
                      )}

                      {/* Inputs */}
                      {entry.inputs && Object.keys(entry.inputs).length > 0 && (
                        <div className={styles.detailBlock}>
                          <div className={styles.detailBlockTitle}>输入</div>
                          <div className={styles.detailJson}>
                            <pre>{tryStringifyJson(entry.inputs)}</pre>
                          </div>
                        </div>
                      )}

                      {/* Outputs */}
                      {entry.outputs && Object.keys(entry.outputs).length > 0 && (
                        <div className={styles.detailBlock}>
                          <div className={styles.detailBlockTitle}>输出</div>
                          <div className={styles.detailJson}>
                            <pre>{tryStringifyJson(entry.outputs)}</pre>
                          </div>
                        </div>
                      )}

                      {/* Fallback output (for pending/running states) */}
                      {!entry.outputs && entry.output && (
                        <div className={styles.detailBlock}>
                          <div className={styles.detailBlockTitle}>输出</div>
                          <div className={styles.detailJson}>
                            <pre>{entry.output}</pre>
                          </div>
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
              )
            })}
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
