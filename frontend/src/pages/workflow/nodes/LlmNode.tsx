import type { NodeProps } from '@xyflow/react'
import BaseNode from './BaseNode'
import type { LlmNodeData } from '@/types/workflow'

export default function LlmNode({ id, selected, data }: NodeProps) {
  const d = data as unknown as LlmNodeData
  return (
    <BaseNode id={id} type="llm" selected={selected}>
      <div>{d?.model ?? 'deepseek-chat'}</div>
      {d?.systemPrompt && (
        <div
          style={{
            marginTop: 4,
            fontStyle: 'italic',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
            maxWidth: 180,
          }}
        >
          {d.systemPrompt.slice(0, 40)}
          {d.systemPrompt.length > 40 ? '…' : ''}
        </div>
      )}
    </BaseNode>
  )
}
