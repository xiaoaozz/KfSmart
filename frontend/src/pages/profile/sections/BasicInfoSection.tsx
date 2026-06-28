import { useRef, useState, useEffect } from 'react'
import { Form, Input, Button, App } from 'antd'
import { CameraOutlined, SaveOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import UserAvatar from '@/components/UserAvatar'
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
  const { t } = useTranslation()
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
      message.success(t('profile.basic.updateSuccess'))
    },
  })

  const avatarMutation = useMutation({
    mutationFn: (file: File) => profileApi.uploadAvatar(file),
    onSuccess: (res) => {
      setUploadedAvatar(res.avatar)
      qc.invalidateQueries({ queryKey: ['users', 'me'] })
      message.success(t('profile.basic.avatarSuccess'))
    },
  })

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    if (file.size > 2 * 1024 * 1024) {
      message.error(t('profile.basic.avatarTooLarge'))
      return
    }
    avatarMutation.mutate(file)
  }

  return (
    <div className={styles.section}>
      <h3 className={styles.sectionTitle}>{t('profile.basic.title')}</h3>

      <div className={styles.avatarRow}>
        <div className={styles.avatarWrap}>
          <UserAvatar size={80} avatar={avatarUrl} username={user?.username} />
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
          <div className={styles.avatarRole}>
            {user?.role === 'ADMIN' ? t('profile.basic.roleAdmin') : t('profile.basic.roleUser')}
          </div>
          <div className={styles.avatarTip}>{t('profile.basic.avatarTip')}</div>
        </div>
      </div>

      <Form
        form={form}
        layout="vertical"
        onFinish={(v) => updateMutation.mutate(v)}
        style={{ maxWidth: 480 }}
      >
        <Form.Item
          name="email"
          label={t('profile.basic.fieldEmail')}
          rules={[{ required: true, type: 'email' }]}
        >
          <Input />
        </Form.Item>
        <Form.Item
          name="phone"
          label={t('profile.basic.fieldPhone')}
          rules={[{ pattern: /^[0-9+\-()\s]{6,32}$/, message: t('profile.basic.phonePattern') }]}
        >
          <Input />
        </Form.Item>
        <Form.Item name="bio" label={t('profile.basic.fieldBio')}>
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
            {t('profile.basic.saveBtn')}
          </Button>
        </Form.Item>
      </Form>
    </div>
  )
}
