import type { NodeProps } from '@xyflow/react'
import BaseNode from './BaseNode'

export default function EndNode({ id, selected }: NodeProps) {
  return (
    <BaseNode id={id} type="end" selected={selected} hasSource={false}>
      <span>输出结果</span>
    </BaseNode>
  )
}
