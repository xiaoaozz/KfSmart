import type { ReactNode } from 'react'
import { Handle, Position } from '@xyflow/react'
import { NODE_COLORS, NODE_LABELS } from './nodeTypes'
import styles from './BaseNode.module.css'

interface Props {
  id: string
  type: string
  selected: boolean
  hasTarget?: boolean
  hasSource?: boolean
  children?: ReactNode
  extra?: ReactNode
}

export default function BaseNode({
  id: _id,
  type,
  selected,
  hasTarget = true,
  hasSource = true,
  children,
  extra,
}: Props) {
  const color = NODE_COLORS[type] ?? '#aaa'
  const label = NODE_LABELS[type] ?? type

  return (
    <div
      className={`${styles.node} ${selected ? styles.selected : ''}`}
      style={{ '--node-color': color } as React.CSSProperties}
    >
      {hasTarget && <Handle type="target" position={Position.Top} className={styles.handle} />}
      <div className={styles.header}>
        <span className={styles.dot} />
        <span className={styles.label}>{label}</span>
        {extra}
      </div>
      {children && <div className={styles.body}>{children}</div>}
      {hasSource && <Handle type="source" position={Position.Bottom} className={styles.handle} />}
    </div>
  )
}
