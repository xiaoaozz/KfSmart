import { request } from '../request';

export function fetchGetConversationMessages(conversationId?: string) {
  return request<Api.Chat.Message[]>({
    url: '/users/conversation',
    params: conversationId ? { conversation_id: conversationId } : undefined
  });
}

type SessionFilterParams = {
  sessionType?: string;
  targetType?: string;
  targetId?: string;
};

export function fetchGetConversationSessions(params?: SessionFilterParams) {
  return request<Api.Chat.Session[]>({
    url: '/users/conversation/sessions',
    params: {
      session_type: params?.sessionType,
      target_type: params?.targetType,
      target_id: params?.targetId
    }
  });
}

export function fetchCreateConversationSession(data?: SessionFilterParams & { targetName?: string; targetDescription?: string }) {
  return request<Api.Chat.Session>({
    url: '/users/conversation/sessions',
    method: 'post',
    data: {
      sessionType: data?.sessionType,
      targetType: data?.targetType,
      targetId: data?.targetId,
      targetName: data?.targetName,
      targetDescription: data?.targetDescription
    }
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
