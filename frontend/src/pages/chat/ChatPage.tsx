import { useState, useEffect, useRef, useCallback } from 'react'
import { Input, Button, App, Tooltip, Spin } from 'antd'
import {
  SendOutlined,
  PlusOutlined,
  PushpinOutlined,
  DeleteOutlined,
  RobotOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useParams, useNavigate } from 'react-router-dom'
import ReactMarkdown from 'react-markdown'
import { chatApi } from '@/api/chat'
import { useWebSocket } from '@/hooks/useWebSocket'
import type { Message, Conversation } from '@/types/chat'
import styles from './ChatPage.module.css'

/* ------------------------------------------------------------------ Sidebar */
function ChatSidebar({
  conversations,
  activeId,
  onSelect,
  onCreate,
  onPin,
  onDelete,
}: {
  conversations: Conversation[]
  activeId?: string
  onSelect: (id: string) => void
  onCreate: () => void
  onPin: (id: string, pinned: boolean) => void
  onDelete: (id: string) => void
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
            className={[styles.convItem, activeId === c.id ? styles.active : ''].join(' ')}
            onClick={() => onSelect(c.id)}
          >
            <span className={styles.convTitle}>{c.title}</span>
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

/* ------------------------------------------------------------------ MessageBubble */
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
            <ReactMarkdown>{msg.content}</ReactMarkdown>
            {msg.streaming && <span className={styles.cursor} />}
          </div>
        )}
      </div>
    </div>
  )
}

/* ------------------------------------------------------------------ ChatPage */
export default function ChatPage() {
  const { sessionId } = useParams<{ sessionId?: string }>()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const { message: antMsg } = App.useApp()

  const [activeId, setActiveId] = useState<string | undefined>(sessionId)
  const [messages, setMessages] = useState<Message[]>([])
  const [input, setInput] = useState('')
  const [sending, setSending] = useState(false)
  const [wsUrl, setWsUrl] = useState<string | null>(null)
  const bottomRef = useRef<HTMLDivElement>(null)
  const streamingIdRef = useRef<string | null>(null)

  const { data: convData } = useQuery({
    queryKey: ['conversations'],
    queryFn: () => chatApi.listConversations({ size: 50 }),
  })
  const conversations = convData?.records ?? []

  const { data: historyMsgs, isLoading: historyLoading } = useQuery({
    queryKey: ['messages', activeId],
    queryFn: () => (activeId ? chatApi.listMessages(activeId) : Promise.resolve([])),
    enabled: !!activeId,
  })

  // Sync fetched history into local state (eslint: intentional synced state)
  // eslint-disable-next-line react-hooks/set-state-in-effect
  useEffect(() => {
    if (historyMsgs) setMessages(historyMsgs)
  }, [historyMsgs])

  const createConv = useMutation({
    mutationFn: () => chatApi.createConversation(),
    onSuccess: (conv) => {
      qc.invalidateQueries({ queryKey: ['conversations'] })
      setActiveId(conv.id)
      navigate(`/chat/${conv.id}`, { replace: true })
    },
  })

  const deleteConv = useMutation({
    mutationFn: chatApi.deleteConversation,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['conversations'] })
      if (activeId) {
        setActiveId(undefined)
        navigate('/chat', { replace: true })
      }
    },
  })

  const pinConv = useMutation({
    mutationFn: ({ id, pinned }: { id: string; pinned: boolean }) =>
      chatApi.pinConversation(id, pinned),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['conversations'] }),
  })

  // WebSocket message handler
  const handleWsMessage = useCallback(
    (data: string) => {
      try {
        const evt = JSON.parse(data) as { type: string; content?: string; done?: boolean }
        if (evt.type === 'token' && evt.content) {
          setMessages((prev) => {
            const last = prev[prev.length - 1]
            if (last?.streaming) {
              return [...prev.slice(0, -1), { ...last, content: last.content + evt.content }]
            }
            const newMsg: Message = {
              id: streamingIdRef.current ?? Date.now().toString(),
              role: 'assistant',
              content: evt.content!,
              createdAt: new Date().toISOString(),
              streaming: true,
            }
            return [...prev, newMsg]
          })
        } else if (evt.type === 'done' || evt.done) {
          setMessages((prev) => {
            const last = prev[prev.length - 1]
            if (last?.streaming) return [...prev.slice(0, -1), { ...last, streaming: false }]
            return prev
          })
          setSending(false)
          qc.invalidateQueries({ queryKey: ['messages', activeId] })
        } else if (evt.type === 'error') {
          antMsg.error('AI 响应出错，请重试')
          setSending(false)
        }
      } catch {
        // Raw text token (fallback)
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

  const { send } = useWebSocket(wsUrl, { onMessage: handleWsMessage })

  // Scroll to bottom on new messages
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  const initWsAndSend = async (content: string) => {
    if (!activeId) return
    setSending(true)

    // Optimistic user message
    const userMsg: Message = {
      id: Date.now().toString(),
      role: 'user',
      content,
      createdAt: new Date().toISOString(),
    }
    setMessages((prev) => [...prev, userMsg])

    if (!wsUrl) {
      try {
        const token = await chatApi.getWsToken()
        const base = import.meta.env.VITE_WS_BASE_URL ?? `ws://${window.location.host}`
        const url = `${base}/chat/${token}`
        setWsUrl(url)
        // Wait for open, then send
        setTimeout(() => {
          streamingIdRef.current = `msg-${Date.now()}`
          send(JSON.stringify({ conversationId: activeId, content }))
        }, 300)
      } catch {
        setSending(false)
        antMsg.error('无法建立 WebSocket 连接')
      }
    } else {
      streamingIdRef.current = `msg-${Date.now()}`
      send(JSON.stringify({ conversationId: activeId, content }))
    }
  }

  const handleSend = async () => {
    const content = input.trim()
    if (!content || sending) return
    setInput('')
    await initWsAndSend(content)
  }

  return (
    <div className={styles.root}>
      <ChatSidebar
        conversations={conversations}
        activeId={activeId}
        onSelect={(id) => {
          setActiveId(id)
          navigate(`/chat/${id}`)
        }}
        onCreate={() => createConv.mutate()}
        onPin={(id, pinned) => pinConv.mutate({ id, pinned })}
        onDelete={(id) => deleteConv.mutate(id)}
      />

      <div className={styles.main}>
        {/* Messages area */}
        <div className={styles.messages}>
          {!activeId ? (
            <div className={styles.emptyChat}>
              <RobotOutlined className={styles.emptyChatIcon} />
              <p>选择一个对话，或新建对话开始聊天</p>
            </div>
          ) : historyLoading ? (
            <div style={{ display: 'flex', justifyContent: 'center', paddingTop: 60 }}>
              <Spin />
            </div>
          ) : (
            <>
              {messages.map((m) => (
                <MessageBubble key={m.id} msg={m} />
              ))}
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
            <Button
              type="primary"
              icon={<SendOutlined />}
              disabled={!input.trim() || sending}
              loading={sending}
              onClick={handleSend}
              className={styles.sendBtn}
            />
          </div>
        )}
      </div>
    </div>
  )
}
