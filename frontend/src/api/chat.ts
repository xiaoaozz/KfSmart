import { http } from './http'
import type { Conversation, Message } from '@/types/chat'
import type { PageResult } from '@/types/api'

export const chatApi = {
  listConversations: (params?: { keyword?: string; current?: number; size?: number }) =>
    http
      .get<PageResult<Conversation>>('/users/conversation/sessions', { params })
      .then((r) => r.data),

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

  /** Get a short-lived WS token */
  getWsToken: () =>
    http.get<{ cmdToken: string }>('/chat/websocket-token').then((r) => r.data.cmdToken),
}
