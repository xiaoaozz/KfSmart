export interface Conversation {
  id: string
  title: string
  pinned: boolean
  createdAt: string
  updatedAt: string
}

export interface Citation {
  referenceNumber: number
  fileName: string
  fileMd5: string
  chunkId: number
  snippet: string
  score: number
}

export interface Message {
  id: string
  role: 'user' | 'assistant'
  content: string
  createdAt: string
  streaming?: boolean
  citations?: Citation[]
}

export interface SendMessageParams {
  conversationId: string
  content: string
  kbIds?: number[]
}
