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
import { useTranslation } from 'react-i18next'
import { profileApi, type Favorite } from '@/api/profile'
import styles from './Section.module.css'

type FavType = '' | Favorite['type']

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
  const { t } = useTranslation()
  const [activeType, setActiveType] = useState<FavType>('')

  const TYPE_TABS: Array<{ key: FavType; label: string; icon: React.ReactNode }> = [
    { key: '', label: t('profile.favorites.tabs.all'), icon: <StarOutlined /> },
    { key: 'knowledge', label: t('profile.favorites.tabs.knowledge'), icon: <DatabaseOutlined /> },
    { key: 'document', label: t('profile.favorites.tabs.document'), icon: <FileTextOutlined /> },
    { key: 'agent', label: t('profile.favorites.tabs.agent'), icon: <RobotOutlined /> },
    { key: 'workflow', label: t('profile.favorites.tabs.workflow'), icon: <ApartmentOutlined /> },
    { key: 'chat', label: t('profile.favorites.tabs.chat'), icon: <MessageOutlined /> },
    { key: 'prompt', label: t('profile.favorites.tabs.prompt'), icon: <FileOutlined /> },
  ]

  const TYPE_LABELS: Record<string, string> = {
    knowledge: t('profile.favorites.labels.knowledge'),
    document: t('profile.favorites.labels.document'),
    agent: t('profile.favorites.labels.agent'),
    workflow: t('profile.favorites.labels.workflow'),
    chat: t('profile.favorites.labels.chat'),
    prompt: t('profile.favorites.labels.prompt'),
    skill: t('profile.favorites.labels.skill'),
    mcp_tool: t('profile.favorites.labels.mcp_tool'),
    model: t('profile.favorites.labels.model'),
  }

  const { data, isLoading } = useQuery({
    queryKey: ['favorites', activeType],
    queryFn: () => profileApi.getFavorites(),
  })

  const removeMutation = useMutation({
    mutationFn: (id: number) => profileApi.removeFavorite(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['favorites'] })
      message.success(t('profile.favorites.unfavoriteSuccess'))
    },
  })

  const all = data ?? []
  const records = activeType ? all.filter((f) => f.type === activeType) : all

  return (
    <div className={styles.section}>
      <h3 className={styles.sectionTitle}>{t('profile.favorites.title')}</h3>

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
        <Empty description={t('profile.favorites.empty')} />
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
                <Tooltip title={t('profile.favorites.unfavoriteTooltip')}>
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
