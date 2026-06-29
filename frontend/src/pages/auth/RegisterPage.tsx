import { useState, useEffect, useRef } from 'react'
import { Form, Input, Button, Progress, App } from 'antd'
import { UserOutlined, MailOutlined, LockOutlined, SafetyOutlined } from '@ant-design/icons'
import { Link, useNavigate } from 'react-router-dom'
import { motion } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import { authApi, type RegisterParams } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { GradientButton } from '@/components/base'
import styles from './RegisterPage.module.css'

function passwordStrength(pw: string): { score: number; labelKey: string; color: string } {
  if (!pw) return { score: 0, labelKey: '', color: '' }
  let score = 0
  if (pw.length >= 8) score++
  if (/[A-Z]/.test(pw)) score++
  if (/[0-9]/.test(pw)) score++
  if (/[^A-Za-z0-9]/.test(pw)) score++
  const map = [
    { score: 0, labelKey: '', color: '' },
    { score: 1, labelKey: 'auth.register.strengthWeak', color: '#ef4444' },
    { score: 2, labelKey: 'auth.register.strengthFair', color: '#f59e0b' },
    { score: 3, labelKey: 'auth.register.strengthGood', color: '#3b82f6' },
    { score: 4, labelKey: 'auth.register.strengthStrong', color: '#10b981' },
  ]
  return map[score]
}

const COUNTDOWN = 60

export default function RegisterPage() {
  const [loading, setLoading] = useState(false)
  const [pw, setPw] = useState('')
  const [countdown, setCountdown] = useState(0)
  const [sendingCode, setSendingCode] = useState(false)
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const setTokens = useAuthStore((s) => s.setTokens)
  const navigate = useNavigate()
  const { message } = App.useApp()
  const { t } = useTranslation()
  const [form] = Form.useForm()
  const strength = passwordStrength(pw)

  useEffect(
    () => () => {
      if (timerRef.current) clearInterval(timerRef.current)
    },
    [],
  )

  const handleSendCode = async () => {
    try {
      await form.validateFields(['email'])
    } catch {
      return
    }
    const email: string = form.getFieldValue('email')
    setSendingCode(true)
    try {
      await authApi.sendEmailCode(email)
      message.success(t('auth.register.codeSent'))
      setCountdown(COUNTDOWN)
      timerRef.current = setInterval(() => {
        setCountdown((c) => {
          if (c <= 1) {
            clearInterval(timerRef.current!)
            timerRef.current = null
            return 0
          }
          return c - 1
        })
      }, 1000)
    } catch {
      // Error shown by http interceptor
    } finally {
      setSendingCode(false)
    }
  }

  const onFinish = async (values: RegisterParams & { confirm: string }) => {
    setLoading(true)
    try {
      const result = await authApi.register({
        username: values.username,
        email: values.email,
        password: values.password,
        emailCode: values.emailCode,
      })
      setTokens(result.token, result.refreshToken)
      message.success(t('auth.register.success'))
      navigate('/dashboard', { replace: true })
    } catch {
      // Error shown by interceptor
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
        <h2 className={styles.title}>{t('auth.register.title')}</h2>
        <p className={styles.subtitle}>{t('auth.register.subtitle')}</p>
      </div>

      <Form
        form={form}
        layout="vertical"
        onFinish={onFinish}
        requiredMark={false}
        size="large"
        className={styles.form}
      >
        <Form.Item
          name="username"
          rules={[
            { required: true, message: t('auth.register.usernameRequired') },
            { min: 2, message: t('auth.register.usernameMinLength') },
          ]}
        >
          <Input
            prefix={<UserOutlined />}
            placeholder={t('auth.register.username')}
            autoComplete="username"
          />
        </Form.Item>

        <Form.Item
          name="email"
          rules={[
            { required: true, message: t('auth.register.emailRequired') },
            { type: 'email', message: t('auth.register.emailInvalid') },
          ]}
        >
          <Input
            prefix={<MailOutlined />}
            placeholder={t('auth.register.email')}
            autoComplete="email"
          />
        </Form.Item>

        <Form.Item
          name="emailCode"
          rules={[
            { required: true, message: t('auth.register.emailCodeRequired') },
            { pattern: /^\d{6}$/, message: t('auth.register.emailCodePattern') },
          ]}
        >
          <Input
            prefix={<SafetyOutlined />}
            placeholder={t('auth.register.emailCode')}
            maxLength={6}
            autoComplete="one-time-code"
            suffix={
              <Button
                type="link"
                size="small"
                loading={sendingCode}
                disabled={countdown > 0 || sendingCode}
                onClick={handleSendCode}
                style={{ padding: '0 4px', fontSize: 13, whiteSpace: 'nowrap' }}
              >
                {countdown > 0
                  ? t('auth.register.resendAfter', { sec: countdown })
                  : t('auth.register.sendCode')}
              </Button>
            }
          />
        </Form.Item>

        <Form.Item
          name="password"
          rules={[
            { required: true, message: t('auth.register.passwordRequired') },
            { min: 8, message: t('auth.register.passwordMinLength') },
          ]}
        >
          <Input.Password
            prefix={<LockOutlined />}
            placeholder={t('auth.register.password')}
            autoComplete="new-password"
            onChange={(e) => setPw(e.target.value)}
          />
        </Form.Item>

        {pw && (
          <div className={styles.strengthWrap}>
            <Progress
              percent={(strength.score / 4) * 100}
              showInfo={false}
              strokeColor={strength.color}
              size="small"
              style={{ marginBottom: 4 }}
            />
            <span style={{ fontSize: 12, color: strength.color }}>
              {strength.labelKey ? t(strength.labelKey) : ''}
            </span>
          </div>
        )}

        <Form.Item
          name="confirm"
          dependencies={['password']}
          rules={[
            { required: true, message: t('auth.register.confirmRequired') },
            ({ getFieldValue }) => ({
              validator(_, value) {
                if (!value || getFieldValue('password') === value) return Promise.resolve()
                return Promise.reject(new Error(t('auth.register.confirmMismatch')))
              },
            }),
          ]}
        >
          <Input.Password
            prefix={<LockOutlined />}
            placeholder={t('auth.register.confirmPassword')}
            autoComplete="new-password"
          />
        </Form.Item>

        <Form.Item style={{ marginTop: 8 }}>
          <GradientButton size="lg" loading={loading} style={{ width: '100%' }} type="submit">
            {t('auth.register.submit')}
          </GradientButton>
        </Form.Item>
      </Form>

      <div style={{ textAlign: 'center', marginTop: 16 }}>
        <span style={{ color: 'var(--kf-muted-foreground)', fontSize: 14 }}>
          {t('auth.register.hasAccount')}
        </span>{' '}
        <Link to="/login" style={{ color: 'var(--kf-accent)', fontWeight: 500 }}>
          {t('auth.register.login')}
        </Link>
      </div>
    </motion.div>
  )
}
