import { Skeleton, Timeline, Row, Col } from 'antd'
import {
  DatabaseOutlined,
  FileTextOutlined,
  MessageOutlined,
  RobotOutlined,
  PlusOutlined,
  ArrowRightOutlined,
} from '@ant-design/icons'
import { motion } from 'framer-motion'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { http } from '@/api/http'
import { GradientText, GradientCard } from '@/components/base'
import { useCurrentUser } from '@/hooks/usePermission'
import styles from './DashboardPage.module.css'

interface UsageStats {
  kbCount: number
  docCount: number
  chatCount: number
  agentCount: number
}

interface RecentChat {
  id: string
  title: string
  updatedAt: string
}

interface Activity {
  id: string
  content: string
  createdAt: string
  color?: string
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

  const { data: stats } = useQuery<UsageStats>({
    queryKey: ['users', 'usage-stats'],
    queryFn: () => http.get<UsageStats>('/users/usage-stats').then((r) => r.data),
    retry: false,
  })

  const { data: recentChats } = useQuery<RecentChat[]>({
    queryKey: ['conversations', 'recent'],
    queryFn: () => http.get<RecentChat[]>('/conversations?size=5&current=1').then((r) => r.data),
    retry: false,
  })

  const { data: activities } = useQuery<Activity[]>({
    queryKey: ['users', 'activities'],
    queryFn: () => http.get<Activity[]>('/users/activities?size=6').then((r) => r.data),
    retry: false,
  })

  const STATS = [
    {
      icon: <DatabaseOutlined />,
      label: '知识库',
      key: 'kbCount' as keyof UsageStats,
      delay: 0.05,
    },
    { icon: <FileTextOutlined />, label: '文档', key: 'docCount' as keyof UsageStats, delay: 0.1 },
    { icon: <MessageOutlined />, label: '对话', key: 'chatCount' as keyof UsageStats, delay: 0.15 },
    { icon: <RobotOutlined />, label: 'Agent', key: 'agentCount' as keyof UsageStats, delay: 0.2 },
  ]

  const QUICK_ACTIONS = [
    {
      icon: <DatabaseOutlined />,
      label: '新建知识库',
      desc: '创建知识库，上传文档',
      path: '/knowledge-bases',
      accent: true,
    },
    {
      icon: <MessageOutlined />,
      label: '开始对话',
      desc: '与 AI 助手交流',
      path: '/chat',
      accent: false,
    },
    {
      icon: <PlusOutlined />,
      label: '上传文档',
      desc: '支持 PDF / Word / Markdown',
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
          你好，<GradientText>{user?.username ?? '...'}</GradientText>
        </h1>
        <p className={styles.welcomeSub}>KfSmart AI Platform 为您的团队提供智能知识管理能力</p>
      </motion.div>

      {/* Stats */}
      <Row gutter={[16, 16]} className={styles.statsRow}>
        {STATS.map((s) => (
          <Col key={s.key} xs={12} sm={12} md={6}>
            <StatCard icon={s.icon} label={s.label} value={stats?.[s.key]} delay={s.delay} />
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
                <span className={styles.panelTitle}>最近对话</span>
                <span className={styles.panelMore} onClick={() => navigate('/chat')}>
                  全部 <ArrowRightOutlined />
                </span>
              </div>
              {!recentChats ? (
                <Skeleton active paragraph={{ rows: 4 }} />
              ) : recentChats.length === 0 ? (
                <div className={styles.empty}>暂无对话，点击上方"开始对话"试试</div>
              ) : (
                <ul className={styles.chatList}>
                  {recentChats.map((c) => (
                    <li
                      key={c.id}
                      className={styles.chatItem}
                      onClick={() => navigate(`/chat/${c.id}`)}
                    >
                      <MessageOutlined className={styles.chatIcon} />
                      <span className={styles.chatTitle}>{c.title}</span>
                      <span className={styles.chatTime}>
                        {new Date(c.updatedAt).toLocaleDateString()}
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
                <span className={styles.panelTitle}>近期操作</span>
              </div>
              {!activities ? (
                <Skeleton active paragraph={{ rows: 5 }} />
              ) : (
                <Timeline
                  items={activities.map((a) => ({
                    key: a.id,
                    color: a.color ?? 'blue',
                    children: (
                      <div>
                        <div style={{ fontSize: 13, color: 'var(--kf-foreground)' }}>
                          {a.content}
                        </div>
                        <div style={{ fontSize: 12, color: 'var(--kf-muted-foreground)' }}>
                          {new Date(a.createdAt).toLocaleString()}
                        </div>
                      </div>
                    ),
                  }))}
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
          快捷入口
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
