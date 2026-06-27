import type { NodeProps } from '@xyflow/react'
import BaseNode from './BaseNode'
import type { KbNodeData } from '@/types/workflow'

export default function KbNode({ id, selected, data }: NodeProps) {
  const d = data as unknown as KbNodeData
  return (
    <BaseNode id={id} type="kb" selected={selected}>
      <div>KB #{d?.knowledgeBaseId ?? '—'}</div>
      <div>
        Top-{d?.topK ?? 5} · 阈值 {d?.threshold ?? 0.7}
      </div>
    </BaseNode>
  )
}
