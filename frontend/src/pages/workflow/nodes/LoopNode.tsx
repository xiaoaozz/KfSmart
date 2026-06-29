import type { NodeProps } from '@xyflow/react'
import { useTranslation } from 'react-i18next'
import BaseNode from './BaseNode'
import type { LoopNodeData } from '@/types/workflow'

export default function LoopNode({ id, selected, data }: NodeProps) {
  const { t } = useTranslation()
  const d = data as unknown as LoopNodeData
  const isCount = d?.mode !== 'array'
  return (
    <BaseNode id={id} type="loop" selected={selected}>
      <div>
        {isCount
          ? t('workflow.nodeContent.loopCountMode', { count: d?.count ?? '?' })
          : t('workflow.nodeContent.loopArrayMode', { var: d?.arrayVariable ?? '?' })}
      </div>
    </BaseNode>
  )
}
