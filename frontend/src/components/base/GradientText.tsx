import type { CSSProperties, ElementType, ReactNode } from 'react'

interface GradientTextProps {
  children: ReactNode
  /** Show gradient underline bar below text */
  underline?: boolean
  className?: string
  style?: CSSProperties
  as?: ElementType
}

export default function GradientText({
  children,
  underline = false,
  className = '',
  style,
  as: Tag = 'span',
}: GradientTextProps) {
  const cls = ['gradient-text', underline ? 'gradient-underline' : '', className]
    .filter(Boolean)
    .join(' ')

  return (
    <Tag className={cls} style={style}>
      {children}
    </Tag>
  )
}
