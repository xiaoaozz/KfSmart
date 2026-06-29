import { Empty } from 'antd'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

interface EmptyStateProps {
  title?: string
  description?: string
  action?: ReactNode
  image?: ReactNode
}

export default function EmptyState({ title, description, action, image }: EmptyStateProps) {
  const { t } = useTranslation()
  return (
    <Empty
      image={image ?? Empty.PRESENTED_IMAGE_SIMPLE}
      description={
        <span>
          <strong style={{ color: 'var(--kf-foreground)' }}>{title ?? t('common.empty')}</strong>
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
