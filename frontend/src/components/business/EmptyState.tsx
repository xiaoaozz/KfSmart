import { Empty } from 'antd'
import type { ReactNode } from 'react'

interface EmptyStateProps {
  title?: string
  description?: string
  action?: ReactNode
  image?: ReactNode
}

export default function EmptyState({
  title = '暂无数据',
  description,
  action,
  image,
}: EmptyStateProps) {
  return (
    <Empty
      image={image ?? Empty.PRESENTED_IMAGE_SIMPLE}
      description={
        <span>
          <strong style={{ color: 'var(--kf-foreground)' }}>{title}</strong>
          {description && (
            <span style={{ display: 'block', color: 'var(--kf-muted-foreground)', marginTop: 4 }}>
              {description}
            </span>
          )}
        </span>
      }
      style={{ padding: '48px 0' }}
    >
      {action}
    </Empty>
  )
}
