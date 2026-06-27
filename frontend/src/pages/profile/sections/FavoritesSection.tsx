import { useState } from 'react'
import { Tabs, Button, App, Empty, Tag, Tooltip } from 'antd'
import {
  StarOutlined,
  DeleteOutlined,
  DatabaseOutlined,
  FileTextOutlined,
  RobotOutlined,
  ApartmentOutlined,
  FileOutlined,
  MessageOutlined,
} from '@ant-design/icons'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { motion } from 'framer-motion'
import { profileApi, type Favorite } from '@/api/profile'
import styles from './Section.module.css'

type FavType = '' | Favorite['type']

const TYPE_TABS: Array<{ key: FavType; label: string; icon: React.ReactNode }> = [
  { key: '', label: '全部', icon: <StarOutlined /> },
  { key: 'knowledge', label: '知识库', icon: <DatabaseOutlined /> },
  { key: 'document', label: '文档', icon: <FileTextOutlined /> },
  { key: 'agent', label: 'Agent', icon: <RobotOutlined /> },
  { key: 'workflow', label: '工作流', icon: <ApartmentOutlined /> },
  { key: 'chat', label: '对话', icon: <MessageOutlined /> },
  { key: 'prompt', label: 'Prompt', icon: <FileOutlined /> },
]

const TYPE_ROUTES: Record<string, string> = {
  knowledge: '/knowledge-bases',
  document: '/documents',
  agent: '/agents',
  workflow: '/workflows',
  chat: '/chat',
  prompt: '/skills',
  skill: '/skills',
  mcp_tool: '/skills',
  model: '/skills',
}

const TYPE_LABELS: Record<string, string> = {
  knowledge: '知识库',
  document: '文档',
  agent: 'Agent',
  workflow: '工作流',
  chat: '对话',
  prompt: 'Prompt',
  skill: '技能',
  mcp_tool: 'MCP 工具',
  model: '模型',
}

const TYPE_COLORS: Record<string, string> = {
  knowledge: 'blue',
  document: 'green',
  agent: 'purple',
  workflow: 'orange',
  chat: 'geekblue',
  prompt: 'cyan',
  skill: 'magenta',
  mcp_tool: 'gold',
  model: 'volcano',
}

export default function FavoritesSection() {
  const qc = useQueryClient()
  const navigate = useNavigate()
  const { message } = App.useApp()
  const [activeType, setActiveType] = useState<FavType>('')

  const { data, isLoading } = useQuery({
    queryKey: ['favorites', activeType],
    queryFn: () => profileApi.getFavorites(),
  })

  const removeMutation = useMutation({
    mutationFn: (id: number) => profileApi.removeFavorite(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['favorites'] })
      message.success('已取消收藏')
    },
  })

  const all = data ?? []
  const records = activeType ? all.filter((f) => f.type === activeType) : all

  return (
    <div className={styles.section}>
      <h3 className={styles.sectionTitle}>我的收藏</h3>

      <Tabs
        activeKey={activeType}
        onChange={(k) => setActiveType(k as FavType)}
        items={TYPE_TABS.map((t) => ({
          key: t.key,
          label: (
            <span>
              {t.icon} {t.label}
            </span>
          ),
        }))}
        style={{ marginBottom: 16 }}
      />

      {isLoading ? (
        <div className={styles.favSkeleton}>
          {Array.from({ length: 4 }).map((_, i) => (
            <div key={i} className={styles.skeleton} />
          ))}
        </div>
      ) : !records.length ? (
        <Empty description="暂无收藏" />
      ) : (
        <div className={styles.favList}>
          {records.map((fav: Favorite, i: number) => (
            <motion.div
              key={fav.id}
              initial={{ opacity: 0, x: -8 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ delay: i * 0.03 }}
              className={styles.favRow}
            >
              <div className={styles.favLeft}>
                <Tag color={TYPE_COLORS[fav.type] ?? 'default'}>
                  {TYPE_LABELS[fav.type] ?? fav.type}
                </Tag>
                <span
                  className={styles.favName}
                  onClick={() =>
                    TYPE_ROUTES[fav.type] && navigate(`${TYPE_ROUTES[fav.type]}/${fav.targetId}`)
                  }
                >
                  {fav.title}
                </span>
                {fav.desc && <span className={styles.favDesc}>{fav.desc}</span>}
              </div>
              <div className={styles.favRight}>
                <span className={styles.favTime}>
                  {fav.createdAt ? new Date(fav.createdAt).toLocaleDateString() : ''}
                </span>
                <Tooltip title="取消收藏">
                  <Button
                    size="small"
                    type="text"
                    danger
                    icon={<DeleteOutlined />}
                    loading={removeMutation.isPending}
                    onClick={() => removeMutation.mutate(fav.id)}
                  />
                </Tooltip>
              </div>
            </motion.div>
          ))}
        </div>
      )}
    </div>
  )
}
