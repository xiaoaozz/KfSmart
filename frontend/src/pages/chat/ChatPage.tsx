import { useState, useEffect, useRef, useCallback } from 'react'
import { Input, Button, App, Tooltip, Spin } from 'antd'
import {
  SendOutlined,
  PlusOutlined,
  PushpinOutlined,
  DeleteOutlined,
  RobotOutlined,
  UserOutlined,
  StopOutlined,
} from '@ant-design/icons'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useParams, useNavigate } from 'react-router-dom'
import ReactMarkdown from 'react-markdown'
import { chatApi } from '@/api/chat'
import { useWebSocket } from '@/hooks/useWebSocket'
import type { Message, Conversation } from '@/types/chat'
import styles from './ChatPage.module.css'

/* -------------------------------------------------------------------------- */
/* Helpers                                                                     */
/* -------------------------------------------------------------------------- */

function getStoredJwt(): string | null {
  try {
    const raw = localStorage.getItem('kf-auth')
    return raw ? (JSON.parse(raw)?.state?.token ?? null) : null
  } catch {
    return null
  }
}

type WsFrame =
  | { chunk: string }
  | { type: 'completion'; status: 'finished' | 'stopped' | 'failed'; message?: string }
  | { type: 'search_results'; results: unknown[] }
  | { type: 'connection'; sessionId: string }
  | { type: 'stop'; status: string; partialContent?: string }
  | { error: string; message?: string; type?: never }

/* -------------------------------------------------------------------------- */
/* Sidebar                                                                     */
/* -------------------------------------------------------------------------- */

function ChatSidebar({
  conversations,
  activeId,
  onSelect,
  onCreate,
  onPin,
  onDelete,
  disabled,
}: {
  conversations: Conversation[]
  activeId?: string
  onSelect: (id: string) => void
  onCreate: () => void
  onPin: (id: string, pinned: boolean) => void
  onDelete: (id: string) => void
  disabled?: boolean
}) {
  return (
    <aside className={styles.sidebar}>
      <Button
        type="primary"
        icon={<PlusOutlined />}
        block
        onClick={onCreate}
        className={styles.newBtn}
      >
        新建对话
      </Button>
      <ul className={styles.convList}>
        {conversations.map((c) => (
          <li
            key={c.id}
            className={[
              styles.convItem,
              activeId === c.id ? styles.active : '',
              disabled && activeId !== c.id ? styles.convItemDisabled : '',
            ].join(' ')}
            onClick={() => !disabled && onSelect(c.id)}
          >
            <span className={styles.convTitle}>{c.title || '新对话'}</span>
            <div className={styles.convActions} onClick={(e) => e.stopPropagation()}>
              <Tooltip title={c.pinned ? '取消置顶' : '置顶'}>
                <PushpinOutlined
                  className={[styles.convAction, c.pinned ? styles.pinned : ''].join(' ')}
                  onClick={() => onPin(c.id, !c.pinned)}
                />
              </Tooltip>
              <Tooltip title="删除">
                <DeleteOutlined
                  className={`${styles.convAction} ${styles.convDel}`}
                  onClick={() => onDelete(c.id)}
                />
              </Tooltip>
            </div>
          </li>
        ))}
      </ul>
    </aside>
  )
}

/* -------------------------------------------------------------------------- */
/* MessageBubble                                                               */
/* -------------------------------------------------------------------------- */

function MessageBubble({ msg }: { msg: Message }) {
  const isUser = msg.role === 'user'
  return (
    <div className={[styles.bubble, isUser ? styles.userBubble : styles.aiBubble].join(' ')}>
      <div className={styles.bubbleAvatar}>{isUser ? <UserOutlined /> : <RobotOutlined />}</div>
      <div className={styles.bubbleBody}>
        {isUser ? (
          <p className={styles.userText}>{msg.content}</p>
        ) : (
          <div className={styles.aiText}>
            <ReactMarkdown>{msg.content || ''}</ReactMarkdown>
            {msg.streaming && <span className={styles.cursor} />}
          </div>
        )}
      </div>
    </div>
  )
}

/* -------------------------------------------------------------------------- */
/* ChatPage                                                                    */
/* -------------------------------------------------------------------------- */

export default function ChatPage() {
  const { sessionId } = useParams<{ sessionId?: string }>()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const { message: antMsg } = App.useApp()

  const [activeId, setActiveId] = useState<string | undefined>(sessionId)
  const [messages, setMessages] = useState<Message[]>([])
  const [input, setInput] = useState('')
  const [sending, setSending] = useState(false)
  const [searching, setSearching] = useState(false)
  const [wsUrl, setWsUrl] = useState<string | null>(null)

  // Stable ref to latest `send` — avoids circular deps when handling the `connection` frame
  const sendRef = useRef<(data: string) => void>(() => void 0)
  // Stop command token fetched once from backend
  const cmdTokenRef = useRef<string | null>(null)
  // Message payload queued while WS is still connecting
  const pendingPayloadRef = useRef<string | null>(null)
  // Guard against duplicate auto-init runs
  const autoInitedRef = useRef(false)
  // Tracks whether an {error} frame already displayed the error toast this request
  const errorToastShownRef = useRef(false)

  const bottomRef = useRef<HTMLDivElement>(null)

  /* ---- Queries ---- */
  // Backend returns Conversation[] directly (not a paged wrapper)
  const { data: convData } = useQuery({
    queryKey: ['conversations'],
    queryFn: () => chatApi.listConversations({ size: 50 }),
  })
  const conversations = convData ?? []

  const { data: historyMsgs, isLoading: historyLoading } = useQuery({
    queryKey: ['messages', activeId],
    queryFn: () => (activeId ? chatApi.listMessages(activeId) : Promise.resolve([])),
    enabled: !!activeId,
  })

  /* ---- Mutations ---- */
  // Declared before the effects below that reference createConv
  const createConv = useMutation({
    mutationFn: () => chatApi.createConversation(),
    onSuccess: (conv) => {
      qc.invalidateQueries({ queryKey: ['conversations'] })
      setActiveId(conv.id)
      setMessages([])
      navigate(`/chat/${conv.id}`, { replace: true })
    },
  })

  const deleteConv = useMutation({
    mutationFn: chatApi.deleteConversation,
    onSuccess: (_, deletedId) => {
      qc.invalidateQueries({ queryKey: ['conversations'] })
      const remaining = conversations.filter((c) => c.id !== deletedId)
      if (remaining.length > 0) {
        setActiveId(remaining[0].id)
        navigate(`/chat/${remaining[0].id}`, { replace: true })
      } else {
        setActiveId(undefined)
        autoInitedRef.current = false
        navigate('/chat', { replace: true })
      }
    },
  })

  const pinConv = useMutation({
    mutationFn: ({ id, pinned }: { id: string; pinned: boolean }) =>
      chatApi.pinConversation(id, pinned),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['conversations'] }),
  })

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    if (historyMsgs) setMessages(historyMsgs)
  }, [historyMsgs])

  // Auto-init: on page load, enter the most-recent conversation or create one if none exist
  useEffect(() => {
    if (autoInitedRef.current) return // already ran
    if (activeId) {
      autoInitedRef.current = true
      return
    } // URL already has a session
    if (!convData) return // wait until list is loaded

    autoInitedRef.current = true
    const first = convData?.[0]
    if (first) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setActiveId(first.id)
      navigate(`/chat/${first.id}`, { replace: true })
    } else {
      // No conversations at all — create the first one silently
      createConv.mutate()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [convData])

  /* ---- WebSocket message handler ---- */
  const handleWsMessage = useCallback(
    (data: string) => {
      try {
        const frame = JSON.parse(data) as WsFrame

        // Server-side error notification: {"error":"true","message":"..."}
        // Backend always sends this 500ms before completion:failed.
        if ('error' in frame && !('type' in frame)) {
          setSearching(false)
          if (frame.message) {
            antMsg.error(frame.message)
            errorToastShownRef.current = true
          }
          return
        }

        // Streaming token: {"chunk": "..."}
        if ('chunk' in frame) {
          setSearching(false)
          setMessages((prev) => {
            const last = prev[prev.length - 1]
            if (last?.streaming) {
              return [...prev.slice(0, -1), { ...last, content: last.content + frame.chunk }]
            }
            return [
              ...prev,
              {
                id: `stream-${Date.now()}`,
                role: 'assistant' as const,
                content: frame.chunk,
                createdAt: new Date().toISOString(),
                streaming: true,
              },
            ]
          })
          return
        }

        if ('type' in frame) {
          switch (frame.type) {
            // Knowledge base search in progress
            case 'search_results':
              setSearching(true)
              return

            // WS connection confirmed — flush any queued message
            case 'connection':
              if (pendingPayloadRef.current) {
                sendRef.current(pendingPayloadRef.current)
                pendingPayloadRef.current = null
              }
              return

            // Backend stop confirmation
            case 'stop':
              setSearching(false)
              setMessages((prev) => {
                const last = prev[prev.length - 1]
                if (last?.streaming) return [...prev.slice(0, -1), { ...last, streaming: false }]
                return prev
              })
              return

            // Stream finished / stopped / failed
            case 'completion':
              setSearching(false)
              if (frame.status === 'finished' || frame.status === 'stopped') {
                setMessages((prev) => {
                  const last = prev[prev.length - 1]
                  if (last?.streaming) return [...prev.slice(0, -1), { ...last, streaming: false }]
                  return prev
                })
                setSending(false)
                if (activeId) qc.invalidateQueries({ queryKey: ['messages', activeId] })
              } else if (frame.status === 'failed') {
                setMessages((prev) => {
                  const last = prev[prev.length - 1]
                  if (last?.streaming) return [...prev.slice(0, -1), { ...last, streaming: false }]
                  return prev
                })
                setSending(false)
                // Only show toast if the prior {error} frame didn't already show one
                if (!errorToastShownRef.current) {
                  antMsg.error(frame.message || 'AI 响应出错，请重试')
                }
                errorToastShownRef.current = false
              }
              return
          }
        }
      } catch {
        // Raw text fallback
        setMessages((prev) => {
          const last = prev[prev.length - 1]
          if (last?.streaming) {
            return [...prev.slice(0, -1), { ...last, content: last.content + data }]
          }
          return prev
        })
      }
    },
    [activeId, qc, antMsg],
  )

  const { send, status: wsStatus } = useWebSocket(wsUrl, { onMessage: handleWsMessage })

  // Keep sendRef pointing to the latest send (stable but must be kept in sync)
  useEffect(() => {
    sendRef.current = send
  }, [send])

  // Auto-scroll on new messages
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  /* ---- Send ---- */
  const initWsAndSend = async (content: string) => {
    if (!activeId || sending) return
    setSending(true)
    errorToastShownRef.current = false

    setMessages((prev) => [
      ...prev,
      {
        id: `user-${Date.now()}`,
        role: 'user' as const,
        content,
        createdAt: new Date().toISOString(),
      },
    ])

    const payload = JSON.stringify({
      type: 'chat',
      message: content,
      conversationId: activeId,
    })

    if (!wsUrl) {
      try {
        const jwt = getStoredJwt()
        if (!jwt) throw new Error('no_jwt')

        // Fetch stop token in background
        chatApi
          .getCmdToken()
          .then((tok) => {
            cmdTokenRef.current = tok
          })
          .catch(() => {})

        const proto = window.location.protocol === 'https:' ? 'wss' : 'ws'
        const wsBase = import.meta.env.VITE_WS_BASE_URL ?? `${proto}://${window.location.host}`
        pendingPayloadRef.current = payload
        setWsUrl(`${wsBase}/chat/${jwt}`)
      } catch {
        setSending(false)
        antMsg.error('无法建立连接，请重新登录')
      }
    } else {
      if (wsStatus === 'open') {
        send(payload)
      } else {
        pendingPayloadRef.current = payload
      }
    }
  }

  const handleStop = useCallback(() => {
    if (cmdTokenRef.current) {
      send(JSON.stringify({ type: 'stop', _internal_cmd_token: cmdTokenRef.current }))
    }
    setSending(false)
    setSearching(false)
    setMessages((prev) => {
      const last = prev[prev.length - 1]
      if (last?.streaming) return [...prev.slice(0, -1), { ...last, streaming: false }]
      return prev
    })
  }, [send])

  const handleSend = () => {
    const content = input.trim()
    if (!content || sending) return
    setInput('')
    initWsAndSend(content)
  }

  /* ---- Render ---- */
  return (
    <div className={styles.root}>
      <ChatSidebar
        conversations={conversations}
        activeId={activeId}
        disabled={sending}
        onSelect={(id) => {
          setActiveId(id)
          navigate(`/chat/${id}`)
        }}
        onCreate={() => {
          // Guard: don't create another empty conversation — the current one is already empty
          if (activeId && messages.length === 0) {
            antMsg.info('当前已是新对话，请先发送消息')
            return
          }
          createConv.mutate()
        }}
        onPin={(id, pinned) => pinConv.mutate({ id, pinned })}
        onDelete={(id) => deleteConv.mutate(id)}
      />

      <div className={styles.main}>
        {/* Messages area */}
        <div className={styles.messages}>
          {!activeId ? (
            <div className={styles.emptyChat}>
              <RobotOutlined className={styles.emptyChatIcon} />
              <p className={styles.emptyChatTitle}>智能 AI 助手</p>
              <p className={styles.emptyChatHint}>选择一个对话，或新建对话开始聊天</p>
            </div>
          ) : historyLoading ? (
            <div className={styles.loadingChat}>
              <Spin size="small" />
            </div>
          ) : messages.length === 0 ? (
            <div className={styles.emptyChat}>
              <RobotOutlined className={styles.emptyChatIcon} />
              <p className={styles.emptyChatTitle}>开始对话</p>
              <p className={styles.emptyChatHint}>在下方输入您的问题</p>
            </div>
          ) : (
            <>
              {messages.map((m) => (
                <MessageBubble key={m.id} msg={m} />
              ))}
              {searching && (
                <div className={styles.searching}>
                  <Spin size="small" />
                  <span>正在搜索知识库…</span>
                </div>
              )}
              <div ref={bottomRef} />
            </>
          )}
        </div>

        {/* Input bar */}
        {activeId && (
          <div className={styles.inputBar}>
            <Input.TextArea
              value={input}
              onChange={(e) => setInput(e.target.value)}
              placeholder="输入消息，Enter 发送，Shift+Enter 换行"
              autoSize={{ minRows: 1, maxRows: 6 }}
              disabled={sending}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && !e.shiftKey) {
                  e.preventDefault()
                  handleSend()
                }
              }}
              className={styles.textarea}
            />
            <div className={styles.inputActions}>
              {sending ? (
                <Button icon={<StopOutlined />} onClick={handleStop} className={styles.stopBtn}>
                  停止
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
        )}
      </div>
    </div>
  )
}
