import { motion } from 'framer-motion'
import { Outlet } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { ThemeSwitch, LanguageSwitch } from '@/components/base'
import styles from './AuthLayout.module.css'

export default function AuthLayout() {
  const { t } = useTranslation()

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
            {t('auth.brand.tagline')
              .split('\n')
              .map((line, i, arr) => (
                <span key={i}>
                  {line}
                  {i < arr.length - 1 && <br />}
                </span>
              ))}
          </h1>
          <p className={styles.subTagline}>{t('auth.brand.subTagline')}</p>

          <ul className={styles.features}>
            {(['kb', 'vector', 'chat', 'workflow'] as const).map((key) => (
              <li key={key} className={styles.feature}>
                <span className={styles.featureDot} />
                {t(`auth.brand.features.${key}`)}
              </li>
            ))}
          </ul>
        </motion.div>
      </div>

      {/* Right form panel */}
      <div className={styles.form}>
        <div className={styles.topRight}>
          <LanguageSwitch />
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
