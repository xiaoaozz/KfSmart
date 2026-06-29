import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'

type WsStatus = 'connecting' | 'open' | 'closed' | 'error'

interface UseWebSocketOptions {
  onMessage: (data: string) => void
  onOpen?: () => void
  onClose?: () => void
  onError?: () => void
  /** Max reconnect attempts. Default 5. */
  maxRetries?: number
}

export function useWebSocket(url: string | null, options: UseWebSocketOptions) {
  const { onMessage, onOpen, onClose, onError, maxRetries = 5 } = options
  const wsRef = useRef<WebSocket | null>(null)
  const retriesRef = useRef(0)
  const heartbeatRef = useRef<ReturnType<typeof setInterval> | null>(null)
  const [status, setStatus] = useState<WsStatus>('closed')

  // Store connect in a ref so onclose can reference it without depending on the function value
  const connectRef = useRef<() => void>(() => undefined)

  const clearHeartbeat = () => {
    if (heartbeatRef.current) {
      clearInterval(heartbeatRef.current)
      heartbeatRef.current = null
    }
  }

  const connect = useCallback(() => {
    if (!url) return
    if (wsRef.current?.readyState === WebSocket.OPEN) return

    setStatus('connecting')
    const ws = new WebSocket(url)
    wsRef.current = ws

    ws.onopen = () => {
      setStatus('open')
      retriesRef.current = 0
      heartbeatRef.current = setInterval(() => {
        if (ws.readyState === WebSocket.OPEN) ws.send('ping')
      }, 30_000)
      onOpen?.()
    }

    ws.onmessage = (e: MessageEvent<string>) => {
      if (e.data === 'pong') return
      onMessage(e.data)
    }

    ws.onclose = () => {
      setStatus('closed')
      clearHeartbeat()
      onClose?.()
      if (retriesRef.current < maxRetries) {
        const delay = Math.min(1000 * 2 ** retriesRef.current, 30_000)
        retriesRef.current++
        setTimeout(() => connectRef.current(), delay)
      }
    }

    ws.onerror = () => {
      setStatus('error')
      onError?.()
      ws.close()
    }
  }, [url, onMessage, onOpen, onClose, onError, maxRetries])

  useLayoutEffect(() => {
    connectRef.current = connect
  }, [connect])

  const send = useCallback((data: string) => {
    if (wsRef.current?.readyState === WebSocket.OPEN) {
      wsRef.current.send(data)
    }
  }, [])

  const disconnect = useCallback(() => {
    retriesRef.current = maxRetries // prevent auto-reconnect
    clearHeartbeat()
    wsRef.current?.close()
  }, [maxRetries])

  useEffect(() => {
    if (url) connect()
    return disconnect
  }, [url, connect, disconnect])

  return { status, send, connect, disconnect }
}
