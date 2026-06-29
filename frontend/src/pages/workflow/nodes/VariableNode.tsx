import type { NodeProps } from '@xyflow/react'
import { useTranslation } from 'react-i18next'
import BaseNode from './BaseNode'
import type { VariableNodeData } from '@/types/workflow'

export default function VariableNode({ id, selected, data }: NodeProps) {
  const { t } = useTranslation()
  const d = data as unknown as VariableNodeData
  return (
    <BaseNode id={id} type="variable" selected={selected}>
      <div>{t('workflow.nodeContent.variable', { key: d?.key ?? '?' })}</div>
      {d?.value && (
        <div
          style={{
            fontSize: 11,
            color: 'var(--kf-muted-foreground)',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
            maxWidth: 160,
          }}
        >
          {d.value.slice(0, 24)}
          {d.value.length > 24 ? '…' : ''}
        </div>
      )}
    </BaseNode>
  )
}
