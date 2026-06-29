import type { NodeProps } from '@xyflow/react'
import { useTranslation } from 'react-i18next'
import BaseNode from './BaseNode'
import type { DelayNodeData } from '@/types/workflow'

export default function DelayNode({ id, selected, data }: NodeProps) {
  const { t } = useTranslation()
  const d = data as unknown as DelayNodeData
  return (
    <BaseNode id={id} type="delay" selected={selected}>
      <div>{t('workflow.nodeContent.delay', { seconds: d?.seconds ?? '?' })}</div>
    </BaseNode>
  )
}
