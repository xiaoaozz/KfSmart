import { Skeleton, Timeline, Row, Col } from 'antd'
import {
  DatabaseOutlined,
  FileTextOutlined,
  MessageOutlined,
  StarOutlined,
  PlusOutlined,
  ArrowRightOutlined,
} from '@ant-design/icons'
import { motion } from 'framer-motion'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { http } from '@/api/http'
import { profileApi, type UsageStats, type ActivityLog } from '@/api/profile'
import { GradientText, GradientCard } from '@/components/base'
import { useCurrentUser } from '@/hooks/usePermission'
import styles from './DashboardPage.module.css'

interface RecentChat {
  id: string
  title: string
  updatedAt: string
}

// 后端 operation-records 字段 → 时间轴展示
function toTimelineItem(a: ActivityLog) {
  const colorByType: Record<string, string> = {
    login: 'green',
    upload: 'blue',
    chat: 'purple',
    knowledge: 'cyan',
  }
  return {
    key: a.id,
    color: colorByType[a.type] ?? 'blue',
    content: a.detail || a.action,
    time: a.time,
  }
}

function StatCard({
  icon,
  label,
  value,
  delay,
}: {
  icon: React.ReactNode
  label: string
  value: number | undefined
  delay: number
}) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4, delay }}
    >
      <GradientCard hoverable className={styles.statCard}>
        <div className={styles.statIcon}>{icon}</div>
        <div className={styles.statValue}>
          {value === undefined ? <Skeleton.Input size="small" active /> : value.toLocaleString()}
        </div>
        <div className={styles.statLabel}>{label}</div>
      </GradientCard>
    </motion.div>
  )
}

export default function DashboardPage() {
  const navigate = useNavigate()
  const { data: user } = useCurrentUser()
  const { t } = useTranslation()

  const { data: stats } = useQuery<UsageStats>({
    queryKey: ['users', 'usage-stats'],
    queryFn: () => profileApi.getUsageStats(7),
    retry: false,
  })

  const { data: recentChats } = useQuery<RecentChat[]>({
    queryKey: ['conversations', 'sessions', 'recent'],
    queryFn: () => http.get<RecentChat[]>('/users/conversation/sessions').then((r) => r.data),
    retry: false,
  })

  const { data: activities } = useQuery<ActivityLog[]>({
    queryKey: ['users', 'operation-records'],
    queryFn: () => profileApi.getActivityLogs(),
    retry: false,
  })

  // 后端 usage-stats 返回：knowledgeBaseCount / totalDocuments / totalConversations / favoriteCount
  const STATS = [
    {
      icon: <DatabaseOutlined />,
      label: t('dashboard.stats.knowledgeBase'),
      key: 'knowledgeBaseCount' as keyof UsageStats,
      delay: 0.05,
    },
    {
      icon: <FileTextOutlined />,
      label: t('dashboard.stats.documents'),
      key: 'totalDocuments' as keyof UsageStats,
      delay: 0.1,
    },
    {
      icon: <MessageOutlined />,
      label: t('dashboard.stats.conversations'),
      key: 'totalConversations' as keyof UsageStats,
      delay: 0.15,
    },
    {
      icon: <StarOutlined />,
      label: t('dashboard.stats.favorites'),
      key: 'favoriteCount' as keyof UsageStats,
      delay: 0.2,
    },
  ]

  const QUICK_ACTIONS = [
    {
      icon: <DatabaseOutlined />,
      label: t('dashboard.actions.newKb'),
      desc: t('dashboard.actions.newKbDesc'),
      path: '/knowledge-bases',
      accent: false,
    },
    {
      icon: <MessageOutlined />,
      label: t('dashboard.actions.startChat'),
      desc: t('dashboard.actions.startChatDesc'),
      path: '/chat',
      accent: false,
    },
    {
      icon: <PlusOutlined />,
      label: t('dashboard.actions.uploadDoc'),
      desc: t('dashboard.actions.uploadDocDesc'),
      path: '/documents',
      accent: false,
    },
  ]

  return (
    <div className={styles.root}>
      {/* Welcome heading */}
      <motion.div
        className={styles.welcome}
        initial={{ opacity: 0, y: 16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5 }}
      >
        <h1 className={styles.welcomeTitle}>
          {t('dashboard.greeting')}
          <GradientText>{user?.username ?? '...'}</GradientText>
        </h1>
        <p className={styles.welcomeSub}>{t('dashboard.subtitle')}</p>
      </motion.div>

      {/* Stats */}
      <Row gutter={[16, 16]} className={styles.statsRow}>
        {STATS.map((s) => (
          <Col key={s.key} xs={12} sm={12} md={6}>
            <StatCard
              icon={s.icon}
              label={s.label}
              value={stats?.[s.key] as number | undefined}
              delay={s.delay}
            />
          </Col>
        ))}
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        {/* Recent chats */}
        <Col xs={24} md={14}>
          <motion.div
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.4, delay: 0.25 }}
          >
            <GradientCard className={styles.panel}>
              <div className={styles.panelHeader}>
                <span className={styles.panelTitle}>{t('dashboard.recentChats')}</span>
                <span className={styles.panelMore} onClick={() => navigate('/chat')}>
                  {t('dashboard.viewAll')} <ArrowRightOutlined />
                </span>
              </div>
              {!recentChats ? (
                <Skeleton active paragraph={{ rows: 4 }} />
              ) : recentChats.length === 0 ? (
                <div className={styles.empty}>{t('dashboard.emptyChats')}</div>
              ) : (
                <ul className={styles.chatList}>
                  {recentChats.slice(0, 5).map((c) => (
                    <li
                      key={c.id}
                      className={styles.chatItem}
                      onClick={() => navigate(`/chat/${c.id}`)}
                    >
                      <MessageOutlined className={styles.chatIcon} />
                      <span className={styles.chatTitle}>{c.title}</span>
                      <span className={styles.chatTime}>
                        {c.updatedAt ? new Date(c.updatedAt).toLocaleDateString() : ''}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </GradientCard>
          </motion.div>
        </Col>

        {/* Activity timeline */}
        <Col xs={24} md={10}>
          <motion.div
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.4, delay: 0.3 }}
          >
            <GradientCard className={styles.panel}>
              <div className={styles.panelHeader}>
                <span className={styles.panelTitle}>{t('dashboard.recentActivity')}</span>
              </div>
              {!activities ? (
                <Skeleton active paragraph={{ rows: 5 }} />
              ) : activities.length === 0 ? (
                <div className={styles.empty}>{t('dashboard.emptyActivity')}</div>
              ) : (
                <Timeline
                  items={activities.slice(0, 6).map((a) => {
                    const item = toTimelineItem(a)
                    return {
                      key: item.key,
                      color: item.color,
                      children: (
                        <div>
                          <div style={{ fontSize: 13, color: 'var(--kf-foreground)' }}>
                            {item.content}
                          </div>
                          <div style={{ fontSize: 12, color: 'var(--kf-muted-foreground)' }}>
                            {item.time ? new Date(item.time).toLocaleString() : ''}
                          </div>
                        </div>
                      ),
                    }
                  })}
                />
              )}
            </GradientCard>
          </motion.div>
        </Col>
      </Row>

      {/* Quick actions */}
      <motion.div
        initial={{ opacity: 0, y: 16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4, delay: 0.35 }}
        style={{ marginTop: 16 }}
      >
        <div className={styles.panelTitle} style={{ marginBottom: 12 }}>
          {t('dashboard.quickActions')}
        </div>
        <Row gutter={[16, 16]}>
          {QUICK_ACTIONS.map((a) => (
            <Col key={a.label} xs={24} sm={8}>
              <GradientCard
                featured={a.accent}
                className={styles.actionCard}
                onClick={() => navigate(a.path)}
              >
                <div className={styles.actionIcon}>{a.icon}</div>
                <div className={styles.actionLabel}>{a.label}</div>
                <div className={styles.actionDesc}>{a.desc}</div>
              </GradientCard>
            </Col>
          ))}
        </Row>
      </motion.div>
    </div>
  )
}
