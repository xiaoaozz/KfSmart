import { Switch, Button, App, Skeleton, Divider } from 'antd'
import { SaveOutlined } from '@ant-design/icons'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { profileApi, type NotificationSettings } from '@/api/profile'
import styles from './Section.module.css'

export default function NotificationSection() {
  const qc = useQueryClient()
  const { message } = App.useApp()
  const { t } = useTranslation()

  type NotifItem = { key: keyof NotificationSettings; label: string; desc: string; group: string }
  const NOTIFICATION_ITEMS: NotifItem[] = [
    {
      key: 'systemAlert',
      label: t('profile.notifications.items.systemAlert.label'),
      desc: t('profile.notifications.items.systemAlert.desc'),
      group: t('profile.notifications.groups.site'),
    },
    {
      key: 'newMessage',
      label: t('profile.notifications.items.newMessage.label'),
      desc: t('profile.notifications.items.newMessage.desc'),
      group: t('profile.notifications.groups.site'),
    },
    {
      key: 'mentionMe',
      label: t('profile.notifications.items.mentionMe.label'),
      desc: t('profile.notifications.items.mentionMe.desc'),
      group: t('profile.notifications.groups.site'),
    },
    {
      key: 'knowledgeUpdate',
      label: t('profile.notifications.items.knowledgeUpdate.label'),
      desc: t('profile.notifications.items.knowledgeUpdate.desc'),
      group: t('profile.notifications.groups.content'),
    },
    {
      key: 'uploadComplete',
      label: t('profile.notifications.items.uploadComplete.label'),
      desc: t('profile.notifications.items.uploadComplete.desc'),
      group: t('profile.notifications.groups.content'),
    },
    {
      key: 'weeklyReport',
      label: t('profile.notifications.items.weeklyReport.label'),
      desc: t('profile.notifications.items.weeklyReport.desc'),
      group: t('profile.notifications.groups.report'),
    },
    {
      key: 'emailDigest',
      label: t('profile.notifications.items.emailDigest.label'),
      desc: t('profile.notifications.items.emailDigest.desc'),
      group: t('profile.notifications.groups.report'),
    },
    {
      key: 'browserPush',
      label: t('profile.notifications.items.browserPush.label'),
      desc: t('profile.notifications.items.browserPush.desc'),
      group: t('profile.notifications.groups.push'),
    },
  ]

  const { data, isLoading } = useQuery({
    queryKey: ['notifications'],
    queryFn: () => profileApi.getNotifications(),
  })

  const [local, setLocal] = useState<Partial<NotificationSettings>>({})
  const merged = { ...data, ...local } as NotificationSettings

  const mutation = useMutation({
    mutationFn: () => profileApi.updateNotifications(merged),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['notifications'] })
      setLocal({})
      message.success(t('profile.notifications.saveSuccess'))
    },
  })

  const toggle = (key: keyof NotificationSettings) => {
    setLocal((prev) => ({ ...prev, [key]: !merged[key] }))
  }

  const groups = [...new Set(NOTIFICATION_ITEMS.map((i) => i.group))]

  return (
    <div className={styles.section}>
      <h3 className={styles.sectionTitle}>{t('profile.notifications.title')}</h3>

      {isLoading ? (
        <Skeleton active paragraph={{ rows: 5 }} />
      ) : (
        <>
          {groups.map((group, gi) => (
            <div key={group}>
              {gi > 0 && <Divider />}
              <div className={styles.notifGroup}>{group}</div>
              {NOTIFICATION_ITEMS.filter((i) => i.group === group).map((item) => (
                <div key={item.key} className={styles.notifRow}>
                  <div className={styles.notifText}>
                    <div className={styles.notifLabel}>{item.label}</div>
                    <div className={styles.notifDesc}>{item.desc}</div>
                  </div>
                  <Switch checked={!!merged[item.key]} onChange={() => toggle(item.key)} />
                </div>
              ))}
            </div>
          ))}

          <div style={{ marginTop: 24 }}>
            <Button
              type="primary"
              icon={<SaveOutlined />}
              loading={mutation.isPending}
              disabled={Object.keys(local).length === 0}
              onClick={() => mutation.mutate()}
              style={{ background: 'var(--kf-accent-gradient-r)', border: 'none' }}
            >
              {t('profile.notifications.saveBtn')}
            </Button>
          </div>
        </>
      )}
    </div>
  )
}
