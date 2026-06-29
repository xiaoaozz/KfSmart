import { Spin } from 'antd'
import { useUiStore } from '@/stores/ui'
import styles from './LoadingOverlay.module.css'

export default function LoadingOverlay() {
  const visible = useUiStore((s) => s.loadingOverlay)
  if (!visible) return null

  return (
    <div className={styles.overlay}>
      <Spin size="large" />
    </div>
  )
}
