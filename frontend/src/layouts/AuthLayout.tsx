import { motion } from 'framer-motion'
import { Outlet } from 'react-router-dom'
import { ThemeSwitch } from '@/components/base'
import styles from './AuthLayout.module.css'

export default function AuthLayout() {
  return (
    <div className={styles.root}>
      {/* Left brand panel */}
      <div className={styles.brand}>
        {/* Decorative rotating rings */}
        <motion.div
          className={styles.ring1}
          animate={{ rotate: 360 }}
          transition={{ duration: 24, repeat: Infinity, ease: 'linear' }}
        />
        <motion.div
          className={styles.ring2}
          animate={{ rotate: -360 }}
          transition={{ duration: 18, repeat: Infinity, ease: 'linear' }}
        />

        <motion.div
          className={styles.brandContent}
          initial={{ opacity: 0, y: 24 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.6, ease: 'easeOut' }}
        >
          <div className={styles.logo}>
            <span className={styles.logoIcon}>K</span>
            <span className={styles.logoText}>KfSmart</span>
          </div>
          <h1 className={styles.tagline}>
            AI Knowledge
            <br />
            Management
          </h1>
          <p className={styles.subTagline}>智能文档处理 · RAG 语义检索 · AI 对话增强</p>

          <ul className={styles.features}>
            {['企业级知识库管理', '向量语义检索', '流式 AI 对话', '可视化工作流编排'].map((f) => (
              <li key={f} className={styles.feature}>
                <span className={styles.featureDot} />
                {f}
              </li>
            ))}
          </ul>
        </motion.div>
      </div>

      {/* Right form panel */}
      <div className={styles.form}>
        <div className={styles.themeToggle}>
          <ThemeSwitch />
        </div>
        <motion.div
          className={styles.formInner}
          initial={{ opacity: 0, x: 20 }}
          animate={{ opacity: 1, x: 0 }}
          transition={{ duration: 0.5, delay: 0.1, ease: 'easeOut' }}
        >
          <Outlet />
        </motion.div>
      </div>
    </div>
  )
}
