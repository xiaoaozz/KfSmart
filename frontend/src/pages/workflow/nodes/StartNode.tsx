import type { NodeProps } from '@xyflow/react'
import BaseNode from './BaseNode'

export default function StartNode({ id, selected }: NodeProps) {
  return (
    <BaseNode id={id} type="start" selected={selected} hasTarget={false}>
      <span>工作流入口</span>
    </BaseNode>
  )
}
