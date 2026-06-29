import { useState, useRef, useEffect, useCallback } from 'react'
import { Button, Input, Tag, Tooltip, App } from 'antd'
import {
  ArrowLeftOutlined,
  RobotOutlined,
  ApartmentOutlined,
  SendOutlined,
  StopOutlined,
  SyncOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  ClockCircleOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  CopyOutlined,
  CheckOutlined,
} from '@ant-design/icons'
import { useParams, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import ReactMarkdown from 'react-markdown'
import { useTranslation } from 'react-i18next'
import { useAuthStore } from '@/stores/auth'
import { agentApi } from '@/api/agent'
import type { AgentTraceStep, AgentTokenUsage } from '@/api/agent'
import { workflowApi } from '@/api/workflow'
import { useCurrentUser } from '@/hooks/usePermission'
import UserAvatar from '@/components/UserAvatar'
import styles from './ExploreChatPage.module.css'

/* ------------------------------------------------------------------ Types */

type Mode = 'agent' | 'workflow'

interface ExploreMsg {
  id: string
  role: 'user' | 'assistant'
  content: string
  executionId?: string
}

interface NodeTrace {
  nodeId: string
  status: 'running' | 'success' | 'error'
  output?: string
}

interface ExecTrace {
  executionId: string
  status: 'running' | 'success' | 'failed'
  nodes: NodeTrace[]
  tokens?: number
  tokenUsage?: AgentTokenUsage
  iterations?: number
  durationMs?: number
  answer?: string
  turnIndex: number
  expanded: boolean
}

/** Map a backend ReAct trace step into a clickable trace node showing the raw values. */
function stepToNode(step: AgentTraceStep): NodeTrace {
  const status: NodeTrace['status'] =
    step.status && /error|fail/i.test(step.status) ? 'error' : 'success'
  const nodeId =
    step.action || /final/i.test(step.status)
      ? `#${step.iteration} ${step.action || 'final_answer'}`
      : `#${step.iteration} thought`

  const lines = [
    `iteration: ${step.iteration}`,
    `status: ${step.status}`,
    `durationMs: ${step.durationMs}`,
    step.thought ? `thought: ${step.thought}` : null,
    step.action ? `action: ${step.action}` : null,
    step.actionInput ? `actionInput: ${step.actionInput}` : null,
    step.observation ? `observation: ${step.observation}` : null,
  ].filter(Boolean)
  return { nodeId, status, output: lines.join('\n') }
}

/* ------------------------------------------------------------------ NodeRow */

function NodeRow({ node }: { node: NodeTrace }) {
  const [showOutput, setShowOutput] = useState(false)

  const icon =
    node.status === 'running' ? (
      <SyncOutlined spin style={{ color: 'var(--kf-accent)' }} />
    ) : node.status === 'success' ? (
      <CheckCircleOutlined style={{ color: '#52c41a' }} />
    ) : (
      <CloseCircleOutlined style={{ color: '#ff4d4f' }} />
    )

  return (
    <>
      <div
        className={[styles.nodeRow, showOutput ? styles.nodeRowExpanded : ''].join(' ')}
        onClick={() => node.output && setShowOutput((v) => !v)}
        style={{ cursor: node.output ? 'pointer' : 'default' }}
      >
        <span className={styles.nodeStatusIcon}>{icon}</span>
        <span className={styles.nodeId}>{node.nodeId}</span>
        <Tag
          color={
            node.status === 'running'
              ? 'processing'
              : node.status === 'success'
                ? 'success'
                : 'error'
          }
          style={{ fontSize: 10, padding: '0 5px', lineHeight: '18px' }}
        >
          {node.status}
        </Tag>
      </div>
      {showOutput && node.output && (
        <div className={styles.nodeOutput}>
          <pre className={styles.nodeOutputPre}>{node.output}</pre>
        </div>
      )}
    </>
  )
}

/* ------------------------------------------------------------------ ExecBlock */

function ExecBlock({ exec, isCurrent }: { exec: ExecTrace; isCurrent?: boolean }) {
  const { t } = useTranslation()
  const [expanded, setExpanded] = useState(isCurrent ?? exec.expanded)

  const headerClass = isCurrent ? styles.currentExecHeader : styles.execHeader

  const statusIcon = isCurrent ? (
    <SyncOutlined spin style={{ color: 'var(--kf-accent)', fontSize: 12 }} />
  ) : exec.status === 'success' ? (
    <CheckCircleOutlined style={{ color: '#52c41a', fontSize: 12 }} />
  ) : (
    <CloseCircleOutlined style={{ color: '#ff4d4f', fontSize: 12 }} />
  )

  return (
    <div className={isCurrent ? styles.currentExec : styles.execBlock}>
      <div className={headerClass} onClick={() => setExpanded((v) => !v)}>
        {statusIcon}
        <span className={isCurrent ? styles.currentExecLabel : styles.execId}>
          {isCurrent ? t('explore.chat.executionRunning') : `#${exec.executionId.slice(0, 8)}…`}
        </span>
        <Tag color="default" style={{ fontSize: 10, padding: '0 5px', lineHeight: '18px' }}>
          {t('explore.chat.turn', { n: exec.turnIndex + 1 })}
        </Tag>
      </div>

      {expanded && (
        <>
          {(exec.tokenUsage || exec.durationMs != null || exec.iterations != null) && (
            <div className={styles.execMeta}>
              {exec.tokenUsage && (
                <>
                  <span className={styles.execMetaItem}>
                    {t('explore.chat.tokenTotal', {
                      count: exec.tokenUsage.totalTokens.toLocaleString(),
                    })}
                  </span>
                  <span className={styles.execMetaItem}>
                    {t('explore.chat.tokenPrompt', {
                      count: exec.tokenUsage.promptTokens.toLocaleString(),
                    })}
                  </span>
                  <span className={styles.execMetaItem}>
                    {t('explore.chat.tokenCompletion', {
                      count: exec.tokenUsage.completionTokens.toLocaleString(),
                    })}
                  </span>
                  <span className={styles.execMetaItem}>
                    {t('explore.chat.tokenCost', { cost: exec.tokenUsage.cost.toFixed(6) })}
                  </span>
                </>
              )}
              {exec.iterations != null && (
                <span className={styles.execMetaItem}>
                  {t('explore.chat.iterations', { n: exec.iterations })}
                </span>
              )}
              {exec.durationMs != null && (
                <span className={styles.execMetaItem}>
                  {t('explore.chat.duration', {
                    ms:
                      exec.durationMs < 1000
                        ? `${exec.durationMs}ms`
                        : `${(exec.durationMs / 1000).toFixed(2)}s`,
                  })}
                </span>
              )}
            </div>
          )}

          {exec.answer && (
            <div className={styles.nodeOutput}>
              <div className={styles.rawLabel}>{t('explore.chat.rawAnswer')}</div>
              <pre className={styles.nodeOutputPre}>{exec.answer}</pre>
            </div>
          )}

          {exec.nodes.length > 0 && (
            <>
              <div className={styles.rawLabel}>{t('explore.chat.rawTrace')}</div>
              <div className={styles.nodeList}>
                {exec.nodes.map((n) => (
                  <NodeRow key={n.nodeId} node={n} />
                ))}
              </div>
            </>
          )}
        </>
      )}
    </div>
  )
}

/* ------------------------------------------------------------------ MessageBubble */

function MessageBubble({
  msg,
  userAvatar,
  username,
}: {
  msg: ExploreMsg
  userAvatar?: string
  username?: string
}) {
  const { t } = useTranslation()
  const [copied, setCopied] = useState(false)
  const isUser = msg.role === 'user'

  const handleCopy = () => {
    navigator.clipboard.writeText(msg.content).then(() => {
      setCopied(true)
      setTimeout(() => setCopied(false), 1500)
    })
  }

  return (
    <div className={[styles.bubble, isUser ? styles.userBubble : ''].join(' ')}>
      <div className={styles.bubbleAvatar}>
        {isUser ? (
          <UserAvatar size={32} avatar={userAvatar} username={username} style={{ color: '#fff' }} />
        ) : (
          <RobotOutlined />
        )}
      </div>
      <div className={styles.bubbleBody}>
        {isUser ? (
          <p className={styles.userText}>{msg.content}</p>
        ) : (
          <div className={styles.aiBody}>
            <div className={styles.aiText}>
              <ReactMarkdown>{msg.content}</ReactMarkdown>
            </div>
            <div className={styles.msgActions}>
              <Tooltip title={copied ? t('common.copied') : t('common.copy')}>
                <button className={styles.msgActionBtn} onClick={handleCopy}>
                  {copied ? (
                    <CheckOutlined style={{ color: 'var(--kf-success)' }} />
                  ) : (
                    <CopyOutlined />
                  )}
                </button>
              </Tooltip>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}

/* ------------------------------------------------------------------ TracePanel */

function TracePanel({
  currentExec,
  history,
  onClose,
}: {
  currentExec: ExecTrace | null
  history: ExecTrace[]
  onClose: () => void
}) {
  const { t } = useTranslation()
  const contentRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (currentExec) {
      const el = contentRef.current
      if (el) el.scrollTop = 0
    }
  }, [currentExec])

  const isEmpty = !currentExec && history.length === 0

  return (
    <aside className={styles.tracePanel}>
      <div className={styles.traceHeader}>
        <span className={styles.traceTitle}>
          <ClockCircleOutlined />
          {t('explore.chat.traceTitle')}
        </span>
        <Tooltip title={t('explore.chat.collapseTrace')}>
          <Button type="text" size="small" icon={<MenuFoldOutlined />} onClick={onClose} />
        </Tooltip>
      </div>

      <div className={styles.traceContent} ref={contentRef}>
        {isEmpty ? (
          <div className={styles.traceEmpty}>{t('explore.chat.traceEmpty')}</div>
        ) : (
          <>
            {currentExec && <ExecBlock exec={currentExec} isCurrent />}
            {[...history].reverse().map((exec) => (
              <ExecBlock key={exec.executionId} exec={exec} />
            ))}
          </>
        )}
      </div>
    </aside>
  )
}

/* ------------------------------------------------------------------ ThinkingIndicator */

function ThinkingIndicator() {
  return (
    <div className={styles.thinking}>
      <div className={styles.thinkingAvatar}>
        <RobotOutlined />
      </div>
      <div className={styles.thinkingDots}>
        <div className={styles.thinkingDot} />
        <div className={styles.thinkingDot} />
        <div className={styles.thinkingDot} />
      </div>
    </div>
  )
}

/* ------------------------------------------------------------------ ExploreChatPage */

export default function ExploreChatPage() {
  const { type, id } = useParams<{ type: string; id: string }>()
  const mode: Mode = type === 'workflow' ? 'workflow' : 'agent'
  const entityId = Number(id)

  const { t } = useTranslation()
  const navigate = useNavigate()
  const { message: antMsg } = App.useApp()
  const token = useAuthStore((s) => s.token)
  const { data: currentUser } = useCurrentUser()

  /* entity data */
  const { data: agent } = useQuery({
    queryKey: ['agent', entityId],
    queryFn: () => agentApi.get(entityId),
    enabled: mode === 'agent' && !!id,
  })
  const { data: workflow } = useQuery({
    queryKey: ['workflow', entityId],
    queryFn: () => workflowApi.get(entityId),
    enabled: mode === 'workflow' && !!id,
  })
  const entityName = mode === 'agent' ? (agent?.name ?? '') : (workflow?.name ?? '')

  /* chat state */
  const [messages, setMessages] = useState<ExploreMsg[]>([])
  const [input, setInput] = useState('')
  const [running, setRunning] = useState(false)

  /* trace state */
  const [traceOpen, setTraceOpen] = useState(true)
  const [traceHistory, setTraceHistory] = useState<ExecTrace[]>([])
  const [currentTrace, setCurrentTrace] = useState<ExecTrace | null>(null)
  const turnCountRef = useRef(0)

  /* refs */
  const bottomRef = useRef<HTMLDivElement>(null)
  const wsRef = useRef<WebSocket | null>(null)
  const pollRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const abortRef = useRef(false)

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, running])

  useEffect(() => {
    const abort = abortRef
    const ws = wsRef
    const poll = pollRef
    return () => {
      abort.current = true
      ws.current?.close()
      if (poll.current) clearTimeout(poll.current)
    }
  }, [])

  /* ---- Agent execution (synchronous POST /agents/:id/chat) ---- */

  const runAgent = useCallback(
    async (text: string) => {
      const turnIndex = turnCountRef.current++
      setRunning(true)
      abortRef.current = false

      const exec: ExecTrace = {
        executionId: 'pending',
        status: 'running',
        nodes: [],
        turnIndex,
        expanded: true,
      }
      setCurrentTrace(exec)

      /* Build multi-turn history from prior messages (user/assistant pairs). */
      const history = messages
        .filter((m) => m.role === 'user' || m.role === 'assistant')
        .map((m) => ({ role: m.role, content: m.content }))

      try {
        const res = await agentApi.testRun(entityId, text, history)
        if (abortRef.current) return

        const finalExec: ExecTrace = {
          executionId: res.executionId,
          status: res.success ? 'success' : 'failed',
          nodes: (res.trace ?? []).map(stepToNode),
          tokens: res.tokens?.totalTokens,
          tokenUsage: res.tokens,
          iterations: res.iterations,
          durationMs: res.durationMs,
          answer: res.answer,
          turnIndex,
          expanded: true,
        }
        setCurrentTrace(null)
        setTraceHistory((prev) => [...prev, finalExec])

        setMessages((prev) => [
          ...prev,
          {
            id: `asst-${Date.now()}`,
            role: 'assistant',
            content: res.success && res.answer ? res.answer : t('explore.chat.executionFailed'),
            executionId: res.executionId,
          },
        ])
        setRunning(false)
      } catch {
        setCurrentTrace(null)
        setRunning(false)
        antMsg.error(t('explore.chat.executionFailed'))
      }
    },
    [entityId, messages, t, antMsg],
  )

  /* ---- Workflow execution (POST + WebSocket) ---- */

  const runWorkflow = useCallback(
    async (text: string) => {
      const turnIndex = turnCountRef.current++
      setRunning(true)
      abortRef.current = false

      const exec: ExecTrace = {
        executionId: 'pending',
        status: 'running',
        nodes: [],
        turnIndex,
        expanded: true,
      }
      setCurrentTrace(exec)

      try {
        const { executionId } = await workflowApi.run(entityId, text)
        if (abortRef.current) return

        setCurrentTrace((prev) => (prev ? { ...prev, executionId } : null))

        wsRef.current?.close()
        const wsBase =
          import.meta.env.VITE_WS_BASE_URL ?? window.location.origin.replace(/^http/, 'ws')
        const ws = new WebSocket(`${wsBase}/ws/workflow/${executionId}?token=${token}`)
        wsRef.current = ws

        ws.onmessage = (event) => {
          if (abortRef.current) return
          try {
            const frame = JSON.parse(event.data) as {
              type: string
              nodeId?: string
              status?: string
              output?: string
              success?: boolean
              tokens?: number
              durationMs?: number
            }

            if (frame.type === 'node_progress' && frame.nodeId) {
              const node: NodeTrace = {
                nodeId: frame.nodeId,
                status: (frame.status as NodeTrace['status']) ?? 'running',
                output: frame.output,
              }
              setCurrentTrace((prev) => {
                if (!prev) return null
                const idx = prev.nodes.findIndex((n) => n.nodeId === frame.nodeId)
                const newNodes = [...prev.nodes]
                if (idx >= 0) newNodes[idx] = node
                else newNodes.push(node)
                return { ...prev, nodes: newNodes }
              })
            }

            if (frame.type === 'execution_completed' || frame.type === 'execution_failed') {
              const success = frame.type === 'execution_completed' && frame.success !== false
              const outputText = frame.output

              setCurrentTrace((prev) => {
                if (!prev) return null
                const finalExec: ExecTrace = {
                  ...prev,
                  status: success ? 'success' : 'failed',
                  tokens: frame.tokens,
                  durationMs: frame.durationMs,
                }
                setTraceHistory((h) => [...h, finalExec])
                return null
              })

              setMessages((prev) => [
                ...prev,
                {
                  id: `wf-${Date.now()}`,
                  role: 'assistant',
                  content: outputText
                    ? outputText
                    : success
                      ? t('explore.chat.workflowCompleted')
                      : t('explore.chat.executionFailed'),
                  executionId,
                },
              ])
              setRunning(false)
            }
          } catch {
            // ignore parse errors
          }
        }

        ws.onclose = () => {
          if (!abortRef.current) {
            setCurrentTrace((prev) => {
              if (prev) {
                setTraceHistory((h) => [...h, { ...prev, status: 'failed' }])
              }
              return null
            })
            setRunning((r) => {
              if (r) {
                setMessages((prev) => [
                  ...prev,
                  {
                    id: `wf-close-${Date.now()}`,
                    role: 'assistant',
                    content: t('explore.chat.workflowCompleted'),
                  },
                ])
              }
              return false
            })
          }
        }
      } catch {
        setCurrentTrace(null)
        setRunning(false)
        antMsg.error(t('explore.chat.executionFailed'))
      }
    },
    [entityId, token, t, antMsg],
  )

  /* ---- Send handler ---- */

  const handleSend = useCallback(() => {
    const text = input.trim()
    if (!text || running) return
    setInput('')
    setMessages((prev) => [...prev, { id: `user-${Date.now()}`, role: 'user', content: text }])
    if (mode === 'agent') runAgent(text)
    else runWorkflow(text)
  }, [input, running, mode, runAgent, runWorkflow])

  const handleStop = useCallback(() => {
    abortRef.current = true
    wsRef.current?.close()
    if (pollRef.current) clearTimeout(pollRef.current)
    setCurrentTrace(null)
    setRunning(false)
  }, [])

  /* ---- Render ---- */

  const ModeIcon = mode === 'agent' ? RobotOutlined : ApartmentOutlined

  return (
    <div className={styles.root}>
      {/* Header */}
      <div className={styles.header}>
        <Button icon={<ArrowLeftOutlined />} type="text" onClick={() => navigate('/explore')}>
          {t('explore.chat.backBtn')}
        </Button>

        <div className={styles.headerTitle}>
          <ModeIcon style={{ color: 'var(--kf-accent)', fontSize: 16 }} />
          <span className={styles.entityName}>{entityName}</span>
          <Tag color={mode === 'agent' ? 'blue' : 'purple'} style={{ marginLeft: 2 }}>
            {mode === 'agent' ? t('explore.chat.agentMode') : t('explore.chat.workflowMode')}
          </Tag>
        </div>

        <div className={styles.headerRight}>
          {traceOpen ? (
            <Tooltip title={t('explore.chat.collapseTrace')}>
              <Button
                type="text"
                size="small"
                icon={<MenuFoldOutlined />}
                onClick={() => setTraceOpen(false)}
              />
            </Tooltip>
          ) : (
            <Tooltip title={t('explore.chat.expandTrace')}>
              <Button
                type="text"
                size="small"
                icon={<MenuUnfoldOutlined />}
                onClick={() => setTraceOpen(true)}
              />
            </Tooltip>
          )}
        </div>
      </div>

      {/* Body */}
      <div className={styles.body}>
        {/* Left: Chat */}
        <div className={styles.chatPanel}>
          <div className={styles.messages}>
            {messages.length === 0 && !running && (
              <div className={styles.emptyState}>
                <ModeIcon className={styles.emptyIcon} />
                <p className={styles.emptyTitle}>
                  {t('explore.chat.emptyTitle', { name: entityName })}
                </p>
                <p className={styles.emptyHint}>
                  {mode === 'agent'
                    ? t('explore.chat.emptyAgentHint')
                    : t('explore.chat.emptyWorkflowHint')}
                </p>
              </div>
            )}

            {messages.map((msg) => (
              <MessageBubble
                key={msg.id}
                msg={msg}
                userAvatar={currentUser?.avatar}
                username={currentUser?.username}
              />
            ))}

            {running && <ThinkingIndicator />}
            <div ref={bottomRef} />
          </div>

          <div className={styles.inputBar}>
            <Input.TextArea
              value={input}
              onChange={(e) => setInput(e.target.value)}
              placeholder={
                mode === 'agent'
                  ? t('explore.chat.agentInputPlaceholder')
                  : t('explore.chat.workflowInputPlaceholder')
              }
              autoSize={{ minRows: 1, maxRows: 6 }}
              disabled={running}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && !e.shiftKey) {
                  e.preventDefault()
                  handleSend()
                }
              }}
              className={styles.textarea}
            />
            <div className={styles.inputActions}>
              {running ? (
                <Button icon={<StopOutlined />} onClick={handleStop} className={styles.stopBtn}>
                  {t('explore.chat.stop')}
                </Button>
              ) : (
                <Button
                  type="primary"
                  icon={<SendOutlined />}
                  disabled={!input.trim()}
                  onClick={handleSend}
                  className={styles.sendBtn}
                />
              )}
            </div>
          </div>
        </div>

        {/* Right: Trace panel */}
        {traceOpen && (
          <TracePanel
            currentExec={currentTrace}
            history={traceHistory}
            onClose={() => setTraceOpen(false)}
          />
        )}
      </div>
    </div>
  )
}
