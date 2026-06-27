import { Form, Input, Button, App, Progress } from 'antd'
import { LockOutlined } from '@ant-design/icons'
import { useMutation } from '@tanstack/react-query'
import { profileApi } from '@/api/profile'
import styles from './Section.module.css'

function getStrength(pw: string): { percent: number; color: string; label: string } {
  if (!pw) return { percent: 0, color: '#ccc', label: '' }
  let score = 0
  if (pw.length >= 8) score++
  if (/[A-Z]/.test(pw)) score++
  if (/[0-9]/.test(pw)) score++
  if (/[^A-Za-z0-9]/.test(pw)) score++
  const levels = [
    { percent: 25, color: '#ff4d4f', label: '弱' },
    { percent: 50, color: '#fa8c16', label: '一般' },
    { percent: 75, color: '#fadb14', label: '较强' },
    { percent: 100, color: '#52c41a', label: '强' },
  ]
  return levels[score - 1] ?? levels[0]
}

export default function PasswordSection() {
  const { message } = App.useApp()
  const [form] = Form.useForm<{ oldPassword: string; newPassword: string; confirm: string }>()
  const newPw = Form.useWatch('newPassword', form) ?? ''
  const strength = getStrength(newPw)

  const mutation = useMutation({
    mutationFn: (v: { oldPassword: string; newPassword: string }) => profileApi.changePassword(v),
    onSuccess: () => {
      message.success('密码已更新，请重新登录')
      form.resetFields()
    },
    // 错误提示交由 http 拦截器统一处理（保留后端的校验文案，如"当前密码不正确"）
  })

  return (
    <div className={styles.section}>
      <h3 className={styles.sectionTitle}>修改密码</h3>

      <Form
        form={form}
        layout="vertical"
        onFinish={({ oldPassword, newPassword }) => mutation.mutate({ oldPassword, newPassword })}
        style={{ maxWidth: 420 }}
      >
        <Form.Item name="oldPassword" label="当前密码" rules={[{ required: true }]}>
          <Input.Password prefix={<LockOutlined />} />
        </Form.Item>

        <Form.Item
          name="newPassword"
          label="新密码"
          rules={[{ required: true, min: 8, message: '密码最少 8 位' }]}
        >
          <Input.Password
            prefix={<LockOutlined />}
            placeholder="至少 8 位，包含大写字母和数字效果更好"
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
          label="确认新密码"
          dependencies={['newPassword']}
          rules={[
            { required: true },
            ({ getFieldValue }) => ({
              validator(_, value) {
                if (!value || getFieldValue('newPassword') === value) return Promise.resolve()
                return Promise.reject(new Error('两次密码不一致'))
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
            更新密码
          </Button>
        </Form.Item>
      </Form>
    </div>
  )
}
