import { useState } from 'react'
import { Timeline, Button, Empty } from 'antd'
import { HistoryOutlined, ReloadOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { profileApi, type ActivityLog } from '@/api/profile'
import styles from './Section.module.css'

type TFunc = (key: string, opts?: Record<string, unknown>) => string

function timeAgo(dateStr: string, t: TFunc): string {
  if (!dateStr) return ''
  const ts = new Date(dateStr).getTime()
  if (Number.isNaN(ts)) return dateStr
  const diff = Date.now() - ts
  const minutes = Math.floor(diff / 60000)
  if (minutes < 1) return t('profile.activity.justNow')
  if (minutes < 60) return t('profile.activity.minutesAgo', { n: minutes })
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return t('profile.activity.hoursAgo', { n: hours })
  const days = Math.floor(hours / 24)
  if (days < 30) return t('profile.activity.daysAgo', { n: days })
  return new Date(dateStr).toLocaleDateString()
}

// 后端按 type 着色：login / upload / chat / knowledge
const TYPE_COLOR: Record<string, string> = {
  login: 'green',
  upload: 'blue',
  chat: 'purple',
  knowledge: 'cyan',
}

const PAGE_SIZE = 20

export default function ActivitySection() {
  const [page, setPage] = useState(1)
  const { t } = useTranslation()

  const { data, isLoading, refetch } = useQuery({
    queryKey: ['activity-logs'],
    queryFn: () => profileApi.getActivityLogs(),
  })

  const all = data ?? []
  const records = all.slice(0, page * PAGE_SIZE)
  const hasMore = all.length > page * PAGE_SIZE

  const items = records.map((log: ActivityLog) => ({
    color: TYPE_COLOR[log.type] ?? 'blue',
    children: (
      <div className={styles.activityItem}>
        <div className={styles.activityAction}>{log.action}</div>
        <div className={styles.activityDetail}>{log.detail}</div>
        <div className={styles.activityMeta}>
          <span>{log.ip || '—'}</span>
          <span>{timeAgo(log.time, t as TFunc)}</span>
        </div>
      </div>
    ),
  }))

  return (
    <div className={styles.section}>
      <div className={styles.sectionHeader}>
        <h3 className={styles.sectionTitle}>{t('profile.activity.title')}</h3>
        <Button size="small" icon={<ReloadOutlined />} onClick={() => refetch()}>
          {t('common.refresh')}
        </Button>
      </div>

      {isLoading ? (
        <div className={styles.listSkeleton}>
          {Array.from({ length: 6 }).map((_, i) => (
            <div key={i} className={styles.skeleton} />
          ))}
        </div>
      ) : !records.length ? (
        <Empty description={t('profile.activity.empty')} />
      ) : (
        <>
          <Timeline items={items} style={{ marginTop: 16 }} />
          {hasMore && (
            <Button type="link" icon={<HistoryOutlined />} onClick={() => setPage((p) => p + 1)}>
              {t('profile.activity.loadMore')}
            </Button>
          )}
        </>
      )}
    </div>
  )
}
