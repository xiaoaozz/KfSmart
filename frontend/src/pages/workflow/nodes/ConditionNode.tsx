import type { NodeProps } from '@xyflow/react'
import { Handle, Position } from '@xyflow/react'
import BaseNode from './BaseNode'
import type { ConditionNodeData } from '@/types/workflow'
import styles from './BaseNode.module.css'

export default function ConditionNode({ id, selected, data }: NodeProps) {
  const d = data as unknown as ConditionNodeData
  return (
    <BaseNode id={id} type="condition" selected={selected} hasSource={false}>
      <div>
        {d?.variable ?? 'input'} {d?.operator ?? 'eq'} {d?.value ?? ''}
      </div>
      <Handle
        type="source"
        position={Position.Bottom}
        id="true"
        style={{ left: '30%' }}
        className={styles.handle}
      />
      <Handle
        type="source"
        position={Position.Bottom}
        id="false"
        style={{ left: '70%' }}
        className={styles.handle}
      />
    </BaseNode>
  )
}
