import { request } from '../request';

export function fetchGetConversationMessages(conversationId?: string) {
  return request<Api.Chat.Message[]>({
    url: '/users/conversation',
    params: conversationId ? { conversation_id: conversationId } : undefined
  });
}

export function fetchGetConversationSessions() {
  return request<Api.Chat.Session[]>({
    url: '/users/conversation/sessions'
  });
}

export function fetchCreateConversationSession() {
  return request<Api.Chat.Session>({
    url: '/users/conversation/sessions',
    method: 'post',
    data: {}
  });
}

export function fetchDeleteConversationSession(conversationId: string) {
  return request<Api.Chat.DeleteSessionResult>({
    url: '/users/conversation/sessions',
    method: 'delete',
    params: {
      conversation_id: conversationId
    }
  });
}

export function fetchUpdateConversationPin(conversationId: string, pinned: boolean) {
  return request<Api.Chat.PinSessionResult>({
    url: '/users/conversation/sessions/pin',
    method: 'put',
    data: {
      conversationId,
      pinned
    }
  });
}
