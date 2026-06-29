import { createContext, useContext } from 'react'

export const NodeStatusContext = createContext<Record<string, string>>({})

export function useNodeStatus(nodeId: string): string | undefined {
  const map = useContext(NodeStatusContext)
  return map[nodeId]
}
