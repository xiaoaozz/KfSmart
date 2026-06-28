import { http } from './http'
import type { Conversation, Message } from '@/types/chat'

export const chatApi = {
  listConversations: (params?: { keyword?: string; current?: number; size?: number }) =>
    http
      .get<Array<Conversation & { isPinned?: boolean }>>('/users/conversation/sessions', { params })
      .then((r) =>
        r.data.map((c) => ({ ...c, pinned: Boolean(c.isPinned ?? c.pinned) }) as Conversation),
      ),

  createConversation: (title?: string) =>
    http
      .post<Conversation>('/users/conversation/sessions', { title: title ?? '新对话' })
      .then((r) => r.data),

  deleteConversation: (id: string) =>
    http.delete('/users/conversation/sessions', { params: { conversation_id: id } }),

  pinConversation: (id: string, pinned: boolean) =>
    http.put('/users/conversation/sessions/pin', { conversationId: id, pinned }),

  listMessages: (conversationId: string) =>
    http
      .get<Message[]>('/users/conversation', { params: { conversation_id: conversationId } })
      .then((r) => r.data),

  /** Get the internal stop-command token for this server instance */
  getCmdToken: () =>
    http.get<{ cmdToken: string }>('/chat/websocket-token').then((r) => r.data.cmdToken),

  /** Truncate conversation history in Redis to keepCount messages (for edit-and-resend) */
  truncateMessages: (conversationId: string, keepCount: number) =>
    http.delete('/users/conversation/messages', {
      params: { conversation_id: conversationId, keep_count: keepCount },
    }),
}
