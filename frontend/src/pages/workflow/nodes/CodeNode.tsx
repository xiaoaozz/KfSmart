import type { NodeProps } from '@xyflow/react'
import { CodeOutlined } from '@ant-design/icons'
import BaseNode from './BaseNode'
import type { CodeNodeData } from '@/types/workflow'

export default function CodeNode({ id, selected, data }: NodeProps) {
  const d = data as unknown as CodeNodeData
  return (
    <BaseNode id={id} type="code" selected={selected} extra={<CodeOutlined />}>
      <div>{d?.language ?? 'javascript'}</div>
    </BaseNode>
  )
}
