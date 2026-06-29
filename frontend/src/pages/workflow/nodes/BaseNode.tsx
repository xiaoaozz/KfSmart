import type { ReactNode } from 'react'
import { Handle, Position } from '@xyflow/react'
import { useTranslation } from 'react-i18next'
import { NODE_COLORS } from './nodeTypes'
import { useNodeStatus } from './NodeStatusContext'
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
  id,
  type,
  selected,
  hasTarget = true,
  hasSource = true,
  children,
  extra,
}: Props) {
  const { t } = useTranslation()
  const color = NODE_COLORS[type] ?? '#aaa'
  const label = t('workflow.nodeLabel.' + type, { defaultValue: type })
  const runStatus = useNodeStatus(id)

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
      {runStatus && (
        <span className={`${styles.statusDot} ${styles['status' + capitalize(runStatus)]}`} />
      )}
      {hasSource && <Handle type="source" position={Position.Bottom} className={styles.handle} />}
    </div>
  )
}

function capitalize(s: string): string {
  return s.charAt(0).toUpperCase() + s.slice(1)
}
