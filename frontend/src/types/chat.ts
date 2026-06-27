export interface Conversation {
  id: string
  title: string
  pinned: boolean
  createdAt: string
  updatedAt: string
}

export interface Message {
  id: string
  role: 'user' | 'assistant'
  content: string
  createdAt: string
  streaming?: boolean
}

export interface SendMessageParams {
  conversationId: string
  content: string
  kbIds?: number[]
}
