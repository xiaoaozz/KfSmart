import { useRef, useState, useEffect } from 'react'
import { Form, Input, Button, Avatar, App } from 'antd'
import { UserOutlined, CameraOutlined, SaveOutlined } from '@ant-design/icons'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { profileApi } from '@/api/profile'
import styles from './Section.module.css'

interface ProfileFormValues {
  email: string
  phone?: string
  bio?: string
}

export default function BasicInfoSection() {
  const qc = useQueryClient()
  const { message } = App.useApp()
  const [form] = Form.useForm<ProfileFormValues>()
  // 头像上传后立即预览；后端在 /me/avatar 接口内已持久化，无需随 updateProfile 再发
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
      form.setFieldsValue({
        email: user.email ?? '',
        phone: user.phone ?? '',
        bio: user.bio ?? '',
      })
    }
  }, [user, form])

  const avatarUrl = uploadedAvatar ?? user?.avatar

  const updateMutation = useMutation({
    mutationFn: (v: ProfileFormValues) => profileApi.updateProfile(v),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['users', 'me'] })
      message.success('资料已更新')
    },
  })

  const avatarMutation = useMutation({
    mutationFn: (file: File) => profileApi.uploadAvatar(file),
    onSuccess: (res) => {
      setUploadedAvatar(res.avatar)
      qc.invalidateQueries({ queryKey: ['users', 'me'] })
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
          <div className={styles.avatarRole}>{user?.role === 'ADMIN' ? '管理员' : '普通用户'}</div>
          <div className={styles.avatarTip}>支持 JPG、PNG、WebP、GIF，最大 2MB</div>
        </div>
      </div>

      <Form
        form={form}
        layout="vertical"
        onFinish={(v) => updateMutation.mutate(v)}
        style={{ maxWidth: 480 }}
      >
        <Form.Item name="email" label="邮箱" rules={[{ required: true, type: 'email' }]}>
          <Input />
        </Form.Item>
        <Form.Item
          name="phone"
          label="手机号"
          rules={[{ pattern: /^[0-9+\-()\s]{6,32}$/, message: '手机号格式不正确' }]}
        >
          <Input />
        </Form.Item>
        <Form.Item name="bio" label="个人简介">
          <Input.TextArea rows={3} maxLength={500} showCount />
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
