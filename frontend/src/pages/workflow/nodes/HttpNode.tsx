import type { NodeProps } from '@xyflow/react'
import BaseNode from './BaseNode'
import type { HttpNodeData } from '@/types/workflow'

export default function HttpNode({ id, selected, data }: NodeProps) {
  const d = data as unknown as HttpNodeData
  return (
    <BaseNode id={id} type="http" selected={selected}>
      <div>
        <span style={{ fontFamily: 'var(--kf-font-mono)', fontSize: 11 }}>
          {d?.method ?? 'GET'}
        </span>
      </div>
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
        {(d?.url ?? '—').replace(/^https?:\/\//, '')}
      </div>
    </BaseNode>
  )
}
