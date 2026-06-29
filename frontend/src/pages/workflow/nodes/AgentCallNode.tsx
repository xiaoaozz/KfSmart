import type { NodeProps } from '@xyflow/react'
import { useTranslation } from 'react-i18next'
import BaseNode from './BaseNode'
import type { AgentCallNodeData } from '@/types/workflow'

export default function AgentCallNode({ id, selected, data }: NodeProps) {
  const { t } = useTranslation()
  const d = data as unknown as AgentCallNodeData
  return (
    <BaseNode id={id} type="agent_call" selected={selected}>
      <div>{t('workflow.nodeContent.agent_call', { id: d?.agentId ?? '—' })}</div>
    </BaseNode>
  )
}
