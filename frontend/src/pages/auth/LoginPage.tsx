import { useState, useEffect } from 'react'
import { Form, Input, Checkbox, Divider, App } from 'antd'
import { UserOutlined, LockOutlined } from '@ant-design/icons'
import { Link, useNavigate, useLocation } from 'react-router-dom'
import { motion } from 'framer-motion'
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
      message.success('登录成功')
      navigate(from, { replace: true })
    } catch (err: unknown) {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        (err instanceof Error ? err.message : null) ||
        '登录失败，请稍后重试'
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
        <h2 className={styles.title}>欢迎回来</h2>
        <p className={styles.subtitle}>登录 KfSmart AI Platform</p>
      </div>

      <Form
        layout="vertical"
        onFinish={onFinish}
        requiredMark={false}
        size="large"
        className={styles.form}
      >
        <Form.Item name="username" rules={[{ required: true, message: '请输入用户名或邮箱' }]}>
          <Input prefix={<UserOutlined />} placeholder="用户名 / 邮箱" autoComplete="username" />
        </Form.Item>

        <Form.Item name="password" rules={[{ required: true, message: '请输入密码' }]}>
          <Input.Password
            prefix={<LockOutlined />}
            placeholder="密码"
            autoComplete="current-password"
          />
        </Form.Item>

        <div className={styles.options}>
          <Form.Item name="remember" valuePropName="checked" noStyle>
            <Checkbox>记住我</Checkbox>
          </Form.Item>
        </div>

        <Form.Item style={{ marginTop: 8 }}>
          <GradientButton size="lg" loading={loading} style={{ width: '100%' }} type="submit">
            登录
          </GradientButton>
        </Form.Item>
      </Form>

      <Divider plain style={{ color: 'var(--kf-muted-foreground)', fontSize: 13 }}>
        还没有账号？
      </Divider>

      <div style={{ textAlign: 'center' }}>
        <Link to="/register" style={{ color: 'var(--kf-accent)', fontWeight: 500 }}>
          立即注册
        </Link>
      </div>
    </motion.div>
  )
}
