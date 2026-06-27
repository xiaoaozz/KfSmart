import { useState } from 'react'
import { Timeline, Button, Empty } from 'antd'
import { HistoryOutlined, ReloadOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { profileApi, type ActivityLog } from '@/api/profile'
import styles from './Section.module.css'

function timeAgo(dateStr: string): string {
  const diff = Date.now() - new Date(dateStr).getTime()
  const minutes = Math.floor(diff / 60000)
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes} 分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} 小时前`
  const days = Math.floor(hours / 24)
  if (days < 30) return `${days} 天前`
  return new Date(dateStr).toLocaleDateString()
}

const ACTION_COLOR: Record<string, string> = {
  login: 'green',
  logout: 'gray',
  upload: 'blue',
  delete: 'red',
  update: 'orange',
  create: 'cyan',
  publish: 'purple',
}

export default function ActivitySection() {
  const [page, setPage] = useState(1)
  const PAGE_SIZE = 20

  const { data, isLoading, refetch } = useQuery({
    queryKey: ['activity-logs', page],
    queryFn: () => profileApi.getActivityLogs({ current: page, size: PAGE_SIZE }),
  })

  const records = data?.records ?? []
  const hasMore = data ? data.total > page * PAGE_SIZE : false

  const items = records.map((log: ActivityLog) => ({
    color: ACTION_COLOR[log.action.toLowerCase()] ?? 'blue',
    children: (
      <div className={styles.activityItem}>
        <div className={styles.activityAction}>{log.action}</div>
        <div className={styles.activityDetail}>{log.detail}</div>
        <div className={styles.activityMeta}>
          <span>{log.ip}</span>
          <span>{timeAgo(log.createTime)}</span>
        </div>
      </div>
    ),
  }))

  return (
    <div className={styles.section}>
      <div className={styles.sectionHeader}>
        <h3 className={styles.sectionTitle}>操作记录</h3>
        <Button size="small" icon={<ReloadOutlined />} onClick={() => refetch()}>
          刷新
        </Button>
      </div>

      {isLoading ? (
        <div className={styles.listSkeleton}>
          {Array.from({ length: 6 }).map((_, i) => (
            <div key={i} className={styles.skeleton} />
          ))}
        </div>
      ) : !records.length ? (
        <Empty description="暂无操作记录" />
      ) : (
        <>
          <Timeline items={items} style={{ marginTop: 16 }} />
          {hasMore && (
            <Button type="link" icon={<HistoryOutlined />} onClick={() => setPage((p) => p + 1)}>
              加载更多
            </Button>
          )}
        </>
      )}
    </div>
  )
}
