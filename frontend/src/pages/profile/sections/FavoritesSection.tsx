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
} from '@ant-design/icons'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { motion } from 'framer-motion'
import { profileApi, type Favorite } from '@/api/profile'
import styles from './Section.module.css'

type FavType = Favorite['resourceType'] | ''

const TYPE_TABS: Array<{ key: FavType; label: string; icon: React.ReactNode }> = [
  { key: '', label: '全部', icon: <StarOutlined /> },
  { key: 'knowledge_base', label: '知识库', icon: <DatabaseOutlined /> },
  { key: 'document', label: '文档', icon: <FileTextOutlined /> },
  { key: 'agent', label: 'Agent', icon: <RobotOutlined /> },
  { key: 'workflow', label: '工作流', icon: <ApartmentOutlined /> },
  { key: 'prompt', label: 'Prompt', icon: <FileOutlined /> },
]

const TYPE_ROUTES: Record<string, string> = {
  knowledge_base: '/knowledge-bases',
  document: '/documents',
  agent: '/agents',
  workflow: '/workflows',
  prompt: '/prompts',
}

const TYPE_COLORS: Record<string, string> = {
  knowledge_base: 'blue',
  document: 'green',
  agent: 'purple',
  workflow: 'orange',
  prompt: 'cyan',
}

export default function FavoritesSection() {
  const qc = useQueryClient()
  const navigate = useNavigate()
  const { message } = App.useApp()
  const [activeType, setActiveType] = useState<FavType>('')

  const { data, isLoading } = useQuery({
    queryKey: ['favorites', activeType],
    queryFn: () => profileApi.getFavorites({ resourceType: activeType || undefined, size: 50 }),
  })

  const removeMutation = useMutation({
    mutationFn: (id: number) => profileApi.removeFavorite(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['favorites'] })
      message.success('已取消收藏')
    },
  })

  const records = data?.records ?? []

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
                <Tag color={TYPE_COLORS[fav.resourceType]}>
                  {fav.resourceType.replace('_', ' ')}
                </Tag>
                <span
                  className={styles.favName}
                  onClick={() => navigate(`${TYPE_ROUTES[fav.resourceType]}/${fav.resourceId}`)}
                >
                  {fav.resourceName}
                </span>
                {fav.resourceDesc && <span className={styles.favDesc}>{fav.resourceDesc}</span>}
              </div>
              <div className={styles.favRight}>
                <span className={styles.favTime}>
                  {new Date(fav.createTime).toLocaleDateString()}
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
