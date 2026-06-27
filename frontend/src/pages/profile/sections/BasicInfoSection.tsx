import { useRef, useState, useEffect } from 'react'
import { Form, Input, Button, Avatar, App } from 'antd'
import { UserOutlined, CameraOutlined, SaveOutlined } from '@ant-design/icons'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { profileApi } from '@/api/profile'
import styles from './Section.module.css'

export default function BasicInfoSection() {
  const qc = useQueryClient()
  const { message } = App.useApp()
  const [form] = Form.useForm<{ username: string; email: string }>()
  // Tracks a newly-uploaded avatar URL; null means "use whatever the API returns"
  const [uploadedAvatar, setUploadedAvatar] = useState<string | null>(null)
  const uploadRef = useRef<HTMLInputElement>(null)
  const formInitRef = useRef(false)

  const { data: user } = useQuery({
    queryKey: ['users', 'me'],
    queryFn: () => profileApi.getMe(),
  })

  useEffect(() => {
    if (user && !formInitRef.current) {
      formInitRef.current = true
      form.setFieldsValue({ username: user.username, email: user.email })
    }
  }, [user, form])

  const avatarUrl = uploadedAvatar ?? user?.avatar

  const updateMutation = useMutation({
    mutationFn: (v: { username: string; email: string }) =>
      profileApi.updateProfile({ ...v, avatar: avatarUrl }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['users', 'me'] })
      message.success('资料已更新')
    },
  })

  const avatarMutation = useMutation({
    mutationFn: (file: File) => profileApi.uploadAvatar(file),
    onSuccess: (res) => {
      setUploadedAvatar(res.url)
      message.success('头像已上传')
    },
  })

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    if (file.size > 2 * 1024 * 1024) {
      message.error('头像不能超过 2MB')
      return
    }
    avatarMutation.mutate(file)
  }

  return (
    <div className={styles.section}>
      <h3 className={styles.sectionTitle}>基本资料</h3>

      <div className={styles.avatarRow}>
        <div className={styles.avatarWrap}>
          <Avatar size={80} src={avatarUrl} icon={<UserOutlined />} />
          <button className={styles.avatarOverlay} onClick={() => uploadRef.current?.click()}>
            <CameraOutlined />
          </button>
          <input
            ref={uploadRef}
            type="file"
            accept="image/*"
            style={{ display: 'none' }}
            onChange={handleFileChange}
          />
        </div>
        <div className={styles.avatarHint}>
          <div className={styles.avatarName}>{user?.username}</div>
          <div className={styles.avatarRole}>{user?.role === 'admin' ? '管理员' : '普通用户'}</div>
          <div className={styles.avatarTip}>支持 JPG、PNG，最大 2MB</div>
        </div>
      </div>

      <Form
        form={form}
        layout="vertical"
        onFinish={(v) => updateMutation.mutate(v)}
        style={{ maxWidth: 480 }}
      >
        <Form.Item name="username" label="用户名" rules={[{ required: true, min: 2 }]}>
          <Input prefix={<UserOutlined />} />
        </Form.Item>
        <Form.Item name="email" label="邮箱" rules={[{ required: true, type: 'email' }]}>
          <Input />
        </Form.Item>
        <Form.Item>
          <Button
            type="primary"
            htmlType="submit"
            icon={<SaveOutlined />}
            loading={updateMutation.isPending}
            style={{ background: 'var(--kf-accent-gradient-r)', border: 'none' }}
          >
            保存修改
          </Button>
        </Form.Item>
      </Form>
    </div>
  )
}
