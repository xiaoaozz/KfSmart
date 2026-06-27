import { Switch, Button, App, Skeleton, Divider } from 'antd'
import { SaveOutlined } from '@ant-design/icons'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { profileApi, type NotificationSettings } from '@/api/profile'
import styles from './Section.module.css'

const NOTIFICATION_ITEMS: Array<{
  key: keyof NotificationSettings
  label: string
  desc: string
  group: string
}> = [
  { key: 'systemAlert', label: '系统告警', desc: '系统级公告与异常告警', group: '站内通知' },
  { key: 'newMessage', label: '新消息', desc: '收到新消息时通知', group: '站内通知' },
  { key: 'mentionMe', label: '提及我', desc: '被他人提及时通知', group: '站内通知' },
  { key: 'knowledgeUpdate', label: '知识库更新', desc: '知识库内容变更时通知', group: '内容更新' },
  { key: 'uploadComplete', label: '上传完成', desc: '文档上传处理完成时通知', group: '内容更新' },
  { key: 'weeklyReport', label: '每周报告', desc: '每周一发送使用统计摘要', group: '报告' },
  { key: 'emailDigest', label: '邮件摘要', desc: '定期发送邮件摘要汇总', group: '报告' },
  { key: 'browserPush', label: '浏览器推送', desc: '允许浏览器实时消息推送', group: '推送' },
]

export default function NotificationSection() {
  const qc = useQueryClient()
  const { message } = App.useApp()

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
      message.success('通知设置已保存')
    },
  })

  const toggle = (key: keyof NotificationSettings) => {
    setLocal((prev) => ({ ...prev, [key]: !merged[key] }))
  }

  const groups = [...new Set(NOTIFICATION_ITEMS.map((i) => i.group))]

  return (
    <div className={styles.section}>
      <h3 className={styles.sectionTitle}>通知设置</h3>

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
              保存设置
            </Button>
          </div>
        </>
      )}
    </div>
  )
}
