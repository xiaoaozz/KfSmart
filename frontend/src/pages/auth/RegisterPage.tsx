import { useState, useEffect, useRef } from 'react'
import { Form, Input, Button, Progress, App } from 'antd'
import { UserOutlined, MailOutlined, LockOutlined, SafetyOutlined } from '@ant-design/icons'
import { Link, useNavigate } from 'react-router-dom'
import { motion } from 'framer-motion'
import { authApi, type RegisterParams } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { GradientButton } from '@/components/base'
import styles from './RegisterPage.module.css'

function passwordStrength(pw: string): { score: number; label: string; color: string } {
  if (!pw) return { score: 0, label: '', color: '' }
  let score = 0
  if (pw.length >= 8) score++
  if (/[A-Z]/.test(pw)) score++
  if (/[0-9]/.test(pw)) score++
  if (/[^A-Za-z0-9]/.test(pw)) score++
  const map = [
    { score: 0, label: '', color: '' },
    { score: 1, label: '弱', color: '#ef4444' },
    { score: 2, label: '一般', color: '#f59e0b' },
    { score: 3, label: '较强', color: '#3b82f6' },
    { score: 4, label: '强', color: '#10b981' },
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
      message.success('验证码已发送，请查收邮件')
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
      message.success('注册成功，欢迎加入！')
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
        <h2 className={styles.title}>创建账号</h2>
        <p className={styles.subtitle}>开始使用 KfSmart AI Platform</p>
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
            { required: true, message: '请输入用户名' },
            { min: 2, message: '用户名至少 2 个字符' },
          ]}
        >
          <Input prefix={<UserOutlined />} placeholder="用户名" autoComplete="username" />
        </Form.Item>

        <Form.Item
          name="email"
          rules={[
            { required: true, message: '请输入邮箱' },
            { type: 'email', message: '邮箱格式不正确' },
          ]}
        >
          <Input prefix={<MailOutlined />} placeholder="邮箱地址" autoComplete="email" />
        </Form.Item>

        <Form.Item
          name="emailCode"
          rules={[
            { required: true, message: '请输入邮箱验证码' },
            { pattern: /^\d{6}$/, message: '验证码为 6 位数字' },
          ]}
        >
          <Input
            prefix={<SafetyOutlined />}
            placeholder="6 位验证码"
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
                {countdown > 0 ? `${countdown}s 后重试` : '获取验证码'}
              </Button>
            }
          />
        </Form.Item>

        <Form.Item
          name="password"
          rules={[
            { required: true, message: '请输入密码' },
            { min: 8, message: '密码至少 8 位' },
          ]}
        >
          <Input.Password
            prefix={<LockOutlined />}
            placeholder="密码（至少 8 位）"
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
            <span style={{ fontSize: 12, color: strength.color }}>{strength.label}</span>
          </div>
        )}

        <Form.Item
          name="confirm"
          dependencies={['password']}
          rules={[
            { required: true, message: '请确认密码' },
            ({ getFieldValue }) => ({
              validator(_, value) {
                if (!value || getFieldValue('password') === value) return Promise.resolve()
                return Promise.reject(new Error('两次密码不一致'))
              },
            }),
          ]}
        >
          <Input.Password
            prefix={<LockOutlined />}
            placeholder="确认密码"
            autoComplete="new-password"
          />
        </Form.Item>

        <Form.Item style={{ marginTop: 8 }}>
          <GradientButton size="lg" loading={loading} style={{ width: '100%' }} type="submit">
            注册
          </GradientButton>
        </Form.Item>
      </Form>

      <div style={{ textAlign: 'center', marginTop: 16 }}>
        <span style={{ color: 'var(--kf-muted-foreground)', fontSize: 14 }}>已有账号？</span>{' '}
        <Link to="/login" style={{ color: 'var(--kf-accent)', fontWeight: 500 }}>
          立即登录
        </Link>
      </div>
    </motion.div>
  )
}
