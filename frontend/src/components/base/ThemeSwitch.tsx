import { Tooltip } from 'antd'
import { motion, AnimatePresence } from 'framer-motion'

import { useThemeStore } from '@/stores/theme'

import styles from './ThemeSwitch.module.css'

export default function ThemeSwitch() {
  const { isDark, toggle } = useThemeStore()

  return (
    <Tooltip title={isDark ? '切换浅色模式' : '切换深色模式'} placement="bottom">
      <button
        className={styles.btn}
        onClick={toggle}
        aria-label={isDark ? '切换浅色模式' : '切换深色模式'}
        type="button"
      >
        <AnimatePresence mode="wait" initial={false}>
          {isDark ? (
            <motion.span
              key="moon"
              className={styles.icon}
              initial={{ opacity: 0, rotate: -30, scale: 0.5 }}
              animate={{ opacity: 1, rotate: 0, scale: 1 }}
              exit={{ opacity: 0, rotate: 30, scale: 0.5 }}
              transition={{ duration: 0.2 }}
            >
              🌙
            </motion.span>
          ) : (
            <motion.span
              key="sun"
              className={styles.icon}
              initial={{ opacity: 0, rotate: 30, scale: 0.5 }}
              animate={{ opacity: 1, rotate: 0, scale: 1 }}
              exit={{ opacity: 0, rotate: -30, scale: 0.5 }}
              transition={{ duration: 0.2 }}
            >
              ☀️
            </motion.span>
          )}
        </AnimatePresence>
      </button>
    </Tooltip>
  )
}
