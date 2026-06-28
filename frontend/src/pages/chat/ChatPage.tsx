import { useState, useEffect, useRef, useCallback } from 'react'
import { Input, Button, App, Tooltip, Spin, Popover } from 'antd'
import {
  SendOutlined,
  PlusOutlined,
  PushpinOutlined,
  DeleteOutlined,
  RobotOutlined,
  StopOutlined,
  CopyOutlined,
  CheckOutlined,
  EditOutlined,
} from '@ant-design/icons'
import UserAvatar from '@/components/UserAvatar'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useParams, useNavigate } from 'react-router-dom'
import ReactMarkdown from 'react-markdown'
import { useTranslation } from 'react-i18next'
import { chatApi } from '@/api/chat'
import { useWebSocket } from '@/hooks/useWebSocket'
import { useCurrentUser } from '@/hooks/usePermission'
import type { Message, Conversation, Citation } from '@/types/chat'
import { injectCitationLinks } from '@/utils/citationHelpers'
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

interface WsSearchResultRaw {
  referenceNumber: number
  fileName: string
  fileMd5: string
  chunkId: number
  snippet: string
  score: number
}

type WsFrame =
  | { chunk: string }
  | { type: 'completion'; status: 'finished' | 'stopped' | 'failed'; message?: string }
  | { type: 'search_results'; totalCount: number; results: WsSearchResultRaw[] }
  | { type: 'connection'; sessionId: string }
  | { type: 'stop'; status: string; partialContent?: string }
  | { error: string; message?: string; type?: never }

/* -------------------------------------------------------------------------- */
/* Citation helpers                                                            */
/* -------------------------------------------------------------------------- */

function CitationTag({ refNum, citation }: { refNum: number; citation?: Citation }) {
  const { t } = useTranslation()
  const content = citation ? (
    <div style={{ maxWidth: 380 }}>
      <div
        style={{
          fontWeight: 600,
          fontSize: 13,
          marginBottom: 8,
          color: 'var(--kf-foreground)',
          wordBreak: 'break-all',
        }}
      >
        📄 {citation.fileName}
      </div>
      <div
        style={{
          fontSize: 12,
          color: 'var(--kf-foreground)',
          maxHeight: 220,
          overflowY: 'auto',
          lineHeight: 1.7,
          background: 'var(--kf-muted)',
          padding: '8px 10px',
          borderRadius: 4,
          whiteSpace: 'pre-wrap',
          wordBreak: 'break-word',
        }}
      >
        {citation.snippet}
      </div>
    </div>
  ) : (
    <span style={{ fontSize: 12, color: 'var(--kf-muted-foreground)' }}>
      {t('chat.noPermission')}
    </span>
  )

  return (
    <Popover
      content={content}
      title={t('chat.citationSource', { num: refNum })}
      trigger="hover"
      placement="top"
      overlayStyle={{ maxWidth: 420 }}
    >
      <span className={styles.citationRef}>#{refNum}</span>
    </Popover>
  )
}

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
  const { t } = useTranslation()
  return (
    <aside className={styles.sidebar}>
      <Button
        type="primary"
        icon={<PlusOutlined />}
        block
        onClick={onCreate}
        className={styles.newBtn}
      >
        {t('chat.newConversation')}
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
            <span className={styles.convTitle}>{c.title || t('chat.untitled')}</span>
            <div className={styles.convActions} onClick={(e) => e.stopPropagation()}>
              <Tooltip title={c.pinned ? t('chat.unpin') : t('chat.pin')}>
                <PushpinOutlined
                  className={[styles.convAction, c.pinned ? styles.pinned : ''].join(' ')}
                  style={c.pinned ? { transform: 'rotate(45deg)' } : undefined}
                  onClick={() => onPin(c.id, !c.pinned)}
                />
              </Tooltip>
              <Tooltip title={t('common.delete')}>
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

function MessageBubble({
  msg,
  userAvatar,
  username,
  disabled,
  editingId,
  editingContent,
  onEditStart,
  onEditChange,
  onEditCancel,
  onEditSubmit,
}: {
  msg: Message
  userAvatar?: string
  username?: string
  disabled?: boolean
  editingId: string | null
  editingContent: string
  onEditStart: (id: string, content: string) => void
  onEditChange: (val: string) => void
  onEditCancel: () => void
  onEditSubmit: (id: string, content: string) => void
}) {
  const { t } = useTranslation()
  const isUser = msg.role === 'user'
  const isEditing = editingId === msg.id
  const [copied, setCopied] = useState(false)

  const processedContent =
    !isUser && msg.citations ? injectCitationLinks(msg.content || '') : msg.content || ''

  const handleCopy = () => {
    navigator.clipboard.writeText(msg.content).then(() => {
      setCopied(true)
      setTimeout(() => setCopied(false), 1500)
    })
  }

  return (
    <div className={[styles.bubble, isUser ? styles.userBubble : styles.aiBubble].join(' ')}>
      <div className={styles.bubbleAvatar}>
        {isUser ? (
          <UserAvatar size={32} avatar={userAvatar} username={username} style={{ color: '#fff' }} />
        ) : (
          <RobotOutlined />
        )}
      </div>
      <div className={styles.bubbleBody}>
        {isUser ? (
          isEditing ? (
            <div className={styles.editArea}>
              <Input.TextArea
                value={editingContent}
                onChange={(e) => onEditChange(e.target.value)}
                autoFocus
                autoSize={{ minRows: 2, maxRows: 10 }}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && !e.shiftKey) {
                    e.preventDefault()
                    if (editingContent.trim()) onEditSubmit(msg.id, editingContent)
                  }
                  if (e.key === 'Escape') onEditCancel()
                }}
                className={styles.editTextarea}
              />
              <div className={styles.editBtns}>
                <Button size="small" onClick={onEditCancel}>
                  {t('common.cancel')}
                </Button>
                <Button
                  type="primary"
                  size="small"
                  disabled={!editingContent.trim() || disabled}
                  onClick={() => onEditSubmit(msg.id, editingContent)}
                >
                  {t('chat.resend')}
                </Button>
              </div>
            </div>
          ) : (
            <div className={styles.userMsgWrap}>
              <p className={styles.userText}>{msg.content}</p>
              <div className={styles.msgActions}>
                <Tooltip title={t('chat.edit')}>
                  <button
                    className={styles.msgActionBtn}
                    onClick={() => onEditStart(msg.id, msg.content)}
                    disabled={disabled}
                  >
                    <EditOutlined />
                  </button>
                </Tooltip>
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
          )
        ) : (
          <div className={styles.aiMsgWrap}>
            <div className={styles.aiText}>
              <ReactMarkdown
                components={{
                  a: ({ href, children }) => {
                    if (href?.startsWith('#cite-')) {
                      const refNum = parseInt(href.slice(6), 10)
                      const citation = msg.citations?.find((c) => c.referenceNumber === refNum)
                      return <CitationTag refNum={refNum} citation={citation} />
                    }
                    return (
                      <a href={href} target="_blank" rel="noopener noreferrer">
                        {children}
                      </a>
                    )
                  },
                }}
              >
                {processedContent}
              </ReactMarkdown>
              {msg.streaming && <span className={styles.cursor} />}
            </div>
            {!msg.streaming && (
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
            )}
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
  const { data: currentUser } = useCurrentUser()
  const { t } = useTranslation()

  const [activeId, setActiveId] = useState<string | undefined>(sessionId)
  const [messages, setMessages] = useState<Message[]>([])
  const [input, setInput] = useState('')
  const [sending, setSending] = useState(false)
  const [searching, setSearching] = useState(false)
  const [wsUrl, setWsUrl] = useState<string | null>(null)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [editingContent, setEditingContent] = useState('')

  const sendRef = useRef<(data: string) => void>(() => void 0)
  const cmdTokenRef = useRef<string | null>(null)
  const pendingPayloadRef = useRef<string | null>(null)
  const autoInitedRef = useRef(false)
  const errorToastShownRef = useRef(false)
  const pendingCitationsRef = useRef<Citation[]>([])

  const bottomRef = useRef<HTMLDivElement>(null)

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

  useEffect(() => {
    if (autoInitedRef.current) return
    if (activeId) {
      autoInitedRef.current = true
      return
    }
    if (!convData) return

    autoInitedRef.current = true
    const first = convData?.[0]
    if (first) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setActiveId(first.id)
      navigate(`/chat/${first.id}`, { replace: true })
    } else {
      createConv.mutate()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [convData])

  const handleWsMessage = useCallback(
    (data: string) => {
      try {
        const frame = JSON.parse(data) as WsFrame

        if ('error' in frame && !('type' in frame)) {
          setSearching(false)
          if (frame.message) {
            antMsg.error(frame.message)
            errorToastShownRef.current = true
          }
          return
        }

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
            case 'search_results':
              pendingCitationsRef.current = frame.results.map((r) => ({
                referenceNumber: r.referenceNumber,
                fileName: r.fileName,
                fileMd5: r.fileMd5,
                chunkId: r.chunkId,
                snippet: r.snippet,
                score: r.score,
              }))
              setSearching(true)
              return

            case 'connection':
              if (pendingPayloadRef.current) {
                sendRef.current(pendingPayloadRef.current)
                pendingPayloadRef.current = null
              }
              return

            case 'stop': {
              const stopCitations = pendingCitationsRef.current
              pendingCitationsRef.current = []
              setSearching(false)
              setMessages((prev) => {
                const last = prev[prev.length - 1]
                if (last?.streaming)
                  return [
                    ...prev.slice(0, -1),
                    {
                      ...last,
                      streaming: false,
                      citations: stopCitations.length > 0 ? stopCitations : undefined,
                    },
                  ]
                return prev
              })
              return
            }

            case 'completion': {
              const completionCitations = pendingCitationsRef.current
              pendingCitationsRef.current = []
              setSearching(false)
              if (frame.status === 'finished' || frame.status === 'stopped') {
                setMessages((prev) => {
                  const last = prev[prev.length - 1]
                  if (last?.streaming)
                    return [
                      ...prev.slice(0, -1),
                      {
                        ...last,
                        streaming: false,
                        citations: completionCitations.length > 0 ? completionCitations : undefined,
                      },
                    ]
                  return prev
                })
                setSending(false)
              } else if (frame.status === 'failed') {
                setMessages((prev) => {
                  const last = prev[prev.length - 1]
                  if (last?.streaming) return [...prev.slice(0, -1), { ...last, streaming: false }]
                  return prev
                })
                setSending(false)
                if (!errorToastShownRef.current) {
                  antMsg.error(frame.message || t('chat.aiError'))
                }
                errorToastShownRef.current = false
              }
              return
            }
          }
        }
      } catch {
        setMessages((prev) => {
          const last = prev[prev.length - 1]
          if (last?.streaming) {
            return [...prev.slice(0, -1), { ...last, content: last.content + data }]
          }
          return prev
        })
      }
    },
    [antMsg, t],
  )

  const { send, status: wsStatus } = useWebSocket(wsUrl, { onMessage: handleWsMessage })

  useEffect(() => {
    sendRef.current = send
  }, [send])

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  const initWsAndSend = async (content: string) => {
    if (!activeId || sending) return
    setSending(true)
    errorToastShownRef.current = false
    pendingCitationsRef.current = []

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
        antMsg.error(t('chat.connectFailed'))
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
    pendingCitationsRef.current = []
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

  const handleEditStart = (id: string, content: string) => {
    setEditingId(id)
    setEditingContent(content)
  }

  const handleEditCancel = () => {
    setEditingId(null)
    setEditingContent('')
  }

  const handleEditSubmit = async (id: string, newContent: string) => {
    if (!newContent.trim() || !activeId || sending) return
    const msgIndex = messages.findIndex((m) => m.id === id)
    if (msgIndex === -1) return
    setEditingId(null)
    setEditingContent('')
    try {
      await chatApi.truncateMessages(activeId, msgIndex)
    } catch {
      antMsg.error(t('chat.truncateError'))
      return
    }
    setMessages((prev) => prev.slice(0, msgIndex))
    initWsAndSend(newContent.trim())
  }

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
          if (activeId && messages.length === 0) {
            antMsg.info(t('chat.alreadyNew'))
            return
          }
          createConv.mutate()
        }}
        onPin={(id, pinned) => pinConv.mutate({ id, pinned })}
        onDelete={(id) => deleteConv.mutate(id)}
      />

      <div className={styles.main}>
        <div className={styles.messages}>
          {!activeId ? (
            <div className={styles.emptyChat}>
              <RobotOutlined className={styles.emptyChatIcon} />
              <p className={styles.emptyChatTitle}>{t('chat.emptyTitle')}</p>
              <p className={styles.emptyChatHint}>{t('chat.emptyHint')}</p>
            </div>
          ) : historyLoading ? (
            <div className={styles.loadingChat}>
              <Spin size="small" />
            </div>
          ) : messages.length === 0 ? (
            <div className={styles.emptyChat}>
              <RobotOutlined className={styles.emptyChatIcon} />
              <p className={styles.emptyChatTitle}>{t('chat.startTitle')}</p>
              <p className={styles.emptyChatHint}>{t('chat.startHint')}</p>
            </div>
          ) : (
            <>
              {messages.map((m) => (
                <MessageBubble
                  key={m.id}
                  msg={m}
                  userAvatar={currentUser?.avatar}
                  username={currentUser?.username}
                  disabled={sending}
                  editingId={editingId}
                  editingContent={editingContent}
                  onEditStart={handleEditStart}
                  onEditChange={setEditingContent}
                  onEditCancel={handleEditCancel}
                  onEditSubmit={handleEditSubmit}
                />
              ))}
              {searching && (
                <div className={styles.searching}>
                  <Spin size="small" />
                  <span>{t('chat.searching')}</span>
                </div>
              )}
              <div ref={bottomRef} />
            </>
          )}
        </div>

        {activeId && (
          <div className={styles.inputBar}>
            <Input.TextArea
              value={input}
              onChange={(e) => setInput(e.target.value)}
              placeholder={t('chat.inputPlaceholder')}
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
                  {t('chat.stop')}
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
