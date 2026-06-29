import { Skeleton, Timeline, Row, Col } from 'antd'
import {
  DatabaseOutlined,
  FileTextOutlined,
  MessageOutlined,
  PlusOutlined,
  ArrowRightOutlined,
  RobotOutlined,
  ApartmentOutlined,
  ThunderboltOutlined,
  FileSearchOutlined,
  ToolOutlined,
  AppstoreOutlined,
} from '@ant-design/icons'
import { motion } from 'framer-motion'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { http } from '@/api/http'
import { profileApi, type UsageStats, type ActivityLog } from '@/api/profile'
import { agentApi } from '@/api/agent'
import { workflowApi } from '@/api/workflow'
import { skillApi, promptApi, mcpApi } from '@/api/skill'
import { GradientText, GradientCard } from '@/components/base'
import { useCurrentUser } from '@/hooks/usePermission'
import styles from './DashboardPage.module.css'

interface RecentChat {
  id: string
  title: string
  updatedAt: string
}

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

interface ModuleCardProps {
  icon: React.ReactNode
  label: string
  desc: string
  count: number | undefined
  path: string
  accentColor?: string
  delay: number
}

function ModuleCard({ icon, label, desc, count, path, delay }: ModuleCardProps) {
  const navigate = useNavigate()
  return (
    <motion.div
      initial={{ opacity: 0, scale: 0.95 }}
      animate={{ opacity: 1, scale: 1 }}
      transition={{ duration: 0.35, delay }}
    >
      <GradientCard hoverable className={styles.moduleCard} onClick={() => navigate(path)}>
        <div className={styles.moduleCardHeader}>
          <div className={styles.moduleIcon}>{icon}</div>
          <div className={styles.moduleCount}>
            {count === undefined ? (
              <Skeleton.Input size="small" active style={{ width: 40 }} />
            ) : (
              count
            )}
          </div>
        </div>
        <div className={styles.moduleLabel}>{label}</div>
        <div className={styles.moduleDesc}>{desc}</div>
        <div className={styles.moduleArrow}>
          <ArrowRightOutlined />
        </div>
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

  const { data: agentPage } = useQuery({
    queryKey: ['agents', 'count'],
    queryFn: () => agentApi.list({ size: 1 }),
    retry: false,
  })

  const { data: workflowPage } = useQuery({
    queryKey: ['workflows', 'count'],
    queryFn: () => workflowApi.list({ current: 1, size: 1 }),
    retry: false,
  })

  const { data: skillPage } = useQuery({
    queryKey: ['skills', 'count'],
    queryFn: () => skillApi.list({ size: 1 }),
    retry: false,
  })

  const { data: promptPage } = useQuery({
    queryKey: ['prompts', 'count'],
    queryFn: () => promptApi.list({ size: 1 }),
    retry: false,
  })

  const { data: mcpPage } = useQuery({
    queryKey: ['mcp-tools', 'count'],
    queryFn: () => mcpApi.list({ size: 1 }),
    retry: false,
  })

  const MODULES = [
    {
      icon: <DatabaseOutlined />,
      label: t('nav.knowledgeBase', '知识库'),
      desc: t('dashboard.actions.newKbDesc'),
      count: stats?.knowledgeBaseCount,
      path: '/knowledge-bases',
      delay: 0.05,
    },
    {
      icon: <FileTextOutlined />,
      label: t('nav.documents', '文档'),
      desc: t('dashboard.actions.uploadDocDesc'),
      count: stats?.totalDocuments,
      path: '/documents',
      delay: 0.1,
    },
    {
      icon: <RobotOutlined />,
      label: t('nav.agents', '智能体'),
      desc: t('dashboard.actions.newAgentDesc'),
      count: agentPage?.total,
      path: '/agents',
      delay: 0.15,
    },
    {
      icon: <ApartmentOutlined />,
      label: t('nav.workflows', '工作流'),
      desc: t('dashboard.actions.newWorkflowDesc'),
      count: workflowPage?.total,
      path: '/workflows',
      delay: 0.2,
    },
    {
      icon: <ThunderboltOutlined />,
      label: t('nav.skills', '技能'),
      desc: t('dashboard.actions.newSkillDesc'),
      count: skillPage?.total,
      path: '/skills',
      delay: 0.25,
    },
    {
      icon: <FileSearchOutlined />,
      label: t('nav.prompts', '提示词'),
      desc: t('dashboard.actions.newKbDesc'),
      count: promptPage?.total,
      path: '/skills/prompts',
      delay: 0.3,
    },
    {
      icon: <ToolOutlined />,
      label: t('nav.mcpTools', 'MCP 工具'),
      desc: 'MCP Tool integrations',
      count: mcpPage?.total,
      path: '/skills/mcp',
      delay: 0.35,
    },
    {
      icon: <MessageOutlined />,
      label: t('nav.chat', '对话'),
      desc: t('dashboard.actions.startChatDesc'),
      count: stats?.totalConversations,
      path: '/chat',
      delay: 0.4,
    },
  ]

  const QUICK_ACTIONS = [
    {
      icon: <DatabaseOutlined />,
      label: t('dashboard.actions.newKb'),
      desc: t('dashboard.actions.newKbDesc'),
      path: '/knowledge-bases',
    },
    {
      icon: <MessageOutlined />,
      label: t('dashboard.actions.startChat'),
      desc: t('dashboard.actions.startChatDesc'),
      path: '/chat',
    },
    {
      icon: <PlusOutlined />,
      label: t('dashboard.actions.uploadDoc'),
      desc: t('dashboard.actions.uploadDocDesc'),
      path: '/documents',
    },
    {
      icon: <RobotOutlined />,
      label: t('dashboard.actions.newAgent'),
      desc: t('dashboard.actions.newAgentDesc'),
      path: '/agents',
    },
    {
      icon: <ApartmentOutlined />,
      label: t('dashboard.actions.newWorkflow'),
      desc: t('dashboard.actions.newWorkflowDesc'),
      path: '/workflows',
    },
    {
      icon: <ThunderboltOutlined />,
      label: t('dashboard.actions.newSkill'),
      desc: t('dashboard.actions.newSkillDesc'),
      path: '/skills',
    },
  ]

  return (
    <div className={styles.root}>
      {/* Welcome */}
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

      {/* Platform modules overview */}
      <motion.div
        initial={{ opacity: 0, y: 16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4, delay: 0.2 }}
        style={{ marginTop: 24 }}
      >
        <div className={styles.sectionHeader}>
          <AppstoreOutlined className={styles.sectionIcon} />
          <span className={styles.panelTitle}>{t('dashboard.modules')}</span>
        </div>
        <Row gutter={[12, 12]} style={{ marginTop: 12 }}>
          {MODULES.map((m) => (
            <Col key={m.path} xs={12} sm={8} md={6} lg={3}>
              <ModuleCard {...m} />
            </Col>
          ))}
        </Row>
      </motion.div>

      <Row gutter={[16, 16]} style={{ marginTop: 24 }}>
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
        style={{ marginTop: 24 }}
      >
        <div className={styles.panelTitle} style={{ marginBottom: 12 }}>
          {t('dashboard.quickActions')}
        </div>
        <Row gutter={[12, 12]}>
          {QUICK_ACTIONS.map((a) => (
            <Col key={a.label} xs={12} sm={8} md={4}>
              <GradientCard className={styles.actionCard} onClick={() => navigate(a.path)}>
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
