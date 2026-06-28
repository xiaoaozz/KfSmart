import { useState, useEffect } from 'react'
import { Form, Input, Checkbox, Divider, App } from 'antd'
import { UserOutlined, LockOutlined } from '@ant-design/icons'
import { Link, useNavigate, useLocation } from 'react-router-dom'
import { motion } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import { authApi, type LoginParams } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { GradientButton } from '@/components/base'
import styles from './LoginPage.module.css'

export default function LoginPage() {
  const [loading, setLoading] = useState(false)
  const setTokens = useAuthStore((s) => s.setTokens)
  const navigate = useNavigate()
  const location = useLocation()
  const { message } = App.useApp()
  const { t } = useTranslation()
  const from = (location.state as { from?: Location })?.from?.pathname ?? '/dashboard'

  // 预取并缓存 RSA 公钥，避免登录提交时再等一个 RTT（加密在 authApi.login 内部完成）
  useEffect(() => {
    authApi.prefetchPublicKey()
  }, [])

  const onFinish = async (values: LoginParams) => {
    setLoading(true)
    try {
      const result = await authApi.login(values)
      setTokens(result.token, result.refreshToken)
      message.success(t('auth.login.success'))
      navigate(from, { replace: true })
    } catch (err: unknown) {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        (err instanceof Error ? err.message : null) ||
        t('auth.login.failed')
      message.error(msg)
    } finally {
      setLoading(false)
    }
  }

  return (
    <motion.div
      className={styles.root}
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4 }}
    >
      <div className={styles.header}>
        <h2 className={styles.title}>{t('auth.login.title')}</h2>
        <p className={styles.subtitle}>{t('auth.login.subtitle')}</p>
      </div>

      <Form
        layout="vertical"
        onFinish={onFinish}
        requiredMark={false}
        size="large"
        className={styles.form}
      >
        <Form.Item
          name="username"
          rules={[{ required: true, message: t('auth.login.usernameRequired') }]}
        >
          <Input
            prefix={<UserOutlined />}
            placeholder={t('auth.login.username')}
            autoComplete="username"
          />
        </Form.Item>

        <Form.Item
          name="password"
          rules={[{ required: true, message: t('auth.login.passwordRequired') }]}
        >
          <Input.Password
            prefix={<LockOutlined />}
            placeholder={t('auth.login.password')}
            autoComplete="current-password"
          />
        </Form.Item>

        <div className={styles.options}>
          <Form.Item name="remember" valuePropName="checked" noStyle>
            <Checkbox>{t('auth.login.rememberMe')}</Checkbox>
          </Form.Item>
        </div>

        <Form.Item style={{ marginTop: 8 }}>
          <GradientButton size="lg" loading={loading} style={{ width: '100%' }} type="submit">
            {t('auth.login.submit')}
          </GradientButton>
        </Form.Item>
      </Form>

      <Divider plain style={{ color: 'var(--kf-muted-foreground)', fontSize: 13 }}>
        {t('auth.login.noAccount')}
      </Divider>

      <div style={{ textAlign: 'center' }}>
        <Link to="/register" style={{ color: 'var(--kf-accent)', fontWeight: 500 }}>
          {t('auth.login.register')}
        </Link>
      </div>
    </motion.div>
  )
}
