import { Form, Input, Button, App, Progress } from 'antd'
import { LockOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { useMutation } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { profileApi } from '@/api/profile'
import { useAuthStore } from '@/stores/auth'
import styles from './Section.module.css'

export default function PasswordSection() {
  const { message } = App.useApp()
  const { t } = useTranslation()
  const navigate = useNavigate()
  const clearTokens = useAuthStore((s) => s.clearTokens)
  const [form] = Form.useForm<{ oldPassword: string; newPassword: string; confirm: string }>()
  const newPw = Form.useWatch('newPassword', form) ?? ''

  function getStrength(pw: string): { percent: number; color: string; label: string } {
    if (!pw) return { percent: 0, color: '#ccc', label: '' }
    let score = 0
    if (pw.length >= 8) score++
    if (/[A-Z]/.test(pw)) score++
    if (/[0-9]/.test(pw)) score++
    if (/[^A-Za-z0-9]/.test(pw)) score++
    const levels = [
      { percent: 25, color: '#ff4d4f', label: t('profile.password.strengthWeak') },
      { percent: 50, color: '#fa8c16', label: t('profile.password.strengthFair') },
      { percent: 75, color: '#fadb14', label: t('profile.password.strengthGood') },
      { percent: 100, color: '#52c41a', label: t('profile.password.strengthStrong') },
    ]
    return levels[score - 1] ?? levels[0]
  }

  const strength = getStrength(newPw)

  const mutation = useMutation({
    mutationFn: (v: { oldPassword: string; newPassword: string }) => profileApi.changePassword(v),
    onSuccess: () => {
      message.success(t('profile.password.updateSuccess'))
      form.resetFields()
      // 清除登录态并跳转到登录页
      clearTokens()
      navigate('/login', { replace: true })
    },
  })

  return (
    <div className={styles.section}>
      <h3 className={styles.sectionTitle}>{t('profile.password.title')}</h3>

      <Form
        form={form}
        layout="vertical"
        onFinish={({ oldPassword, newPassword }) => mutation.mutate({ oldPassword, newPassword })}
        style={{ maxWidth: 420 }}
      >
        <Form.Item
          name="oldPassword"
          label={t('profile.password.fieldOld')}
          rules={[{ required: true }]}
        >
          <Input.Password prefix={<LockOutlined />} />
        </Form.Item>

        <Form.Item
          name="newPassword"
          label={t('profile.password.fieldNew')}
          rules={[{ required: true, min: 8, message: t('profile.password.fieldNewMin') }]}
        >
          <Input.Password
            prefix={<LockOutlined />}
            placeholder={t('profile.password.fieldNewPlaceholder')}
          />
        </Form.Item>

        {newPw && (
          <div className={styles.strengthBar}>
            <Progress
              percent={strength.percent}
              strokeColor={strength.color}
              showInfo={false}
              size="small"
            />
            <span style={{ color: strength.color, fontSize: 12 }}>{strength.label}</span>
          </div>
        )}

        <Form.Item
          name="confirm"
          label={t('profile.password.fieldConfirm')}
          dependencies={['newPassword']}
          rules={[
            { required: true },
            ({ getFieldValue }) => ({
              validator(_, value) {
                if (!value || getFieldValue('newPassword') === value) return Promise.resolve()
                return Promise.reject(new Error(t('profile.password.confirmMismatch')))
              },
            }),
          ]}
        >
          <Input.Password prefix={<LockOutlined />} />
        </Form.Item>

        <Form.Item>
          <Button
            type="primary"
            htmlType="submit"
            loading={mutation.isPending}
            icon={<LockOutlined />}
            style={{ background: 'var(--kf-accent-gradient-r)', border: 'none' }}
          >
            {t('profile.password.saveBtn')}
          </Button>
        </Form.Item>
      </Form>
    </div>
  )
}
