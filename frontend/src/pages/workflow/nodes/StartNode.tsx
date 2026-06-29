import type { NodeProps } from '@xyflow/react'
import { useTranslation } from 'react-i18next'
import BaseNode from './BaseNode'
import type { StartNodeData } from '@/types/workflow'

export default function StartNode({ id, selected, data }: NodeProps) {
  const { t } = useTranslation()
  const d = data as unknown as StartNodeData
  const varNames = (d?.variables ?? []).map((v) => v.name).filter(Boolean)
  return (
    <BaseNode id={id} type="start" selected={selected} hasTarget={false}>
      <span>{t('workflow.nodeContent.start')}</span>
      {varNames.length > 0 && (
        <div
          style={{
            marginTop: 4,
            fontSize: 11,
            color: 'var(--kf-muted-foreground)',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
            maxWidth: 180,
          }}
        >
          {varNames.join(', ')}
        </div>
      )}
    </BaseNode>
  )
}
