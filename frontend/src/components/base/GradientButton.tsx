import { motion } from 'framer-motion'
import type { ReactNode, ButtonHTMLAttributes } from 'react'

import styles from './GradientButton.module.css'

// Omit event names that framer-motion redefines with incompatible signatures
type SafeButtonProps = Omit<
  ButtonHTMLAttributes<HTMLButtonElement>,
  'onDrag' | 'onDragStart' | 'onDragEnd' | 'onAnimationStart'
>

interface GradientButtonProps extends SafeButtonProps {
  children: ReactNode
  size?: 'sm' | 'md' | 'lg'
  icon?: ReactNode
  loading?: boolean
}

export default function GradientButton({
  children,
  size = 'md',
  icon,
  loading = false,
  className = '',
  disabled,
  ...rest
}: GradientButtonProps) {
  return (
    <motion.button
      className={[styles.btn, styles[size], className].filter(Boolean).join(' ')}
      whileHover={!disabled && !loading ? { y: -2 } : undefined}
      whileTap={!disabled && !loading ? { scale: 0.97 } : undefined}
      transition={{ duration: 0.15 }}
      disabled={disabled || loading}
      {...rest}
    >
      {loading ? (
        <span className={styles.spinner} aria-hidden="true" />
      ) : icon ? (
        <span className={styles.iconWrap}>{icon}</span>
      ) : null}
      {children}
    </motion.button>
  )
}
