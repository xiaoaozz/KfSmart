import type { ReactNode } from 'react'

interface SectionBadgeProps {
  children: ReactNode
  pulse?: boolean
  className?: string
}

export default function SectionBadge({
  children,
  pulse = false,
  className = '',
}: SectionBadgeProps) {
  return (
    <div className={`section-badge ${className}`}>
      <span className={`section-badge__dot${pulse ? ' section-badge__dot--pulse' : ''}`} />
      {children}
    </div>
  )
}
