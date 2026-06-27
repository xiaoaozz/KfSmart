import { motion } from 'framer-motion'
import type { ReactNode, CSSProperties } from 'react'

import styles from './GradientCard.module.css'

interface GradientCardProps {
  children: ReactNode
  /** Show gradient border permanently */
  featured?: boolean
  /** Lift on hover */
  hoverable?: boolean
  className?: string
  style?: CSSProperties
  onClick?: () => void
}

export default function GradientCard({
  children,
  featured = false,
  hoverable = true,
  className = '',
  style,
  onClick,
}: GradientCardProps) {
  const cls = [
    styles.card,
    featured ? styles.featured : '',
    hoverable ? styles.hoverable : '',
    className,
  ]
    .filter(Boolean)
    .join(' ')

  if (featured) {
    return (
      <div className={styles.featuredWrapper} style={style} onClick={onClick}>
        <motion.div
          className={cls}
          whileHover={hoverable ? { y: -4 } : undefined}
          transition={{ duration: 0.2 }}
        >
          {children}
        </motion.div>
      </div>
    )
  }

  return (
    <motion.div
      className={cls}
      style={style}
      onClick={onClick}
      whileHover={hoverable ? { y: -2 } : undefined}
      transition={{ duration: 0.2 }}
    >
      {children}
    </motion.div>
  )
}
