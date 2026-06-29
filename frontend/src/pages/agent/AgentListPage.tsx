import { useState } from 'react'
import { Input, Select, Tag, Tooltip, Popconfirm, Button, App, Row, Col, Skeleton } from 'antd'
import {
  PlusOutlined,
  SearchOutlined,
  EditOutlined,
  DeleteOutlined,
  RobotOutlined,
  ThunderboltOutlined,
  CheckCircleOutlined,
  PauseCircleOutlined,
  DatabaseOutlined,
  ToolOutlined,
} from '@ant-design/icons'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { motion } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import { agentApi } from '@/api/agent'
import type { Agent, AgentStatus } from '@/types/agent'
import { GradientCard, GradientButton } from '@/components/base'
import { EmptyState, PermissionButton, FavoriteButton, PageBar } from '@/components/business'
import styles from './AgentListPage.module.css'

/** Count comma-separated ID string from backend, e.g. "1,2,3" → 3 */
const countIds = (str?: string | null): number => {
  if (!str?.trim()) return 0
  return str.split(',').filter((s) => s.trim()).length
}

function AgentCard({
  agent,
  onEdit,
  onDelete,
  onPublish,
  onDisable,
}: {
  agent: Agent
  onEdit: (id: number) => void
  onDelete: (id: number) => void
  onPublish: (id: number) => void
  onDisable: (id: number) => void
}) {
  const navigate = useNavigate()
  const { t } = useTranslation()
  const STATUS_CFG: Record<AgentStatus, { color: string; label: string; icon: React.ReactNode }> = {
    draft: { color: 'default', label: t('agent.statusDraft'), icon: <EditOutlined /> },
    published: {
      color: 'success',
      label: t('agent.statusPublished'),
      icon: <CheckCircleOutlined />,
    },
    disabled: { color: 'warning', label: t('agent.statusDisabled'), icon: <PauseCircleOutlined /> },
  }
  const s = STATUS_CFG[agent.status]
  return (
    <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }}>
      <GradientCard className={styles.card}>
        <div className={styles.cardHeader}>
          <div className={styles.cardIconWrap}>
            <RobotOutlined />
          </div>
          <Tag color={s.color} icon={s.icon} style={{ fontSize: 12 }}>
            {s.label}
          </Tag>
        </div>

        <h4 className={styles.cardName}>{agent.name}</h4>
        {agent.description && <p className={styles.cardDesc}>{agent.description}</p>}

        <div className={styles.cardMeta}>
          {agent.models && (
            <span className={`${styles.metaItem} ${styles.metaModel}`}>
              <RobotOutlined />
              {agent.models}
            </span>
          )}
          {agent.temperature != null && (
            <span className={styles.metaItem}>
              {t('agent.chipTemp', { value: agent.temperature.toFixed(2) })}
            </span>
          )}
          {agent.maxTokens != null && (
            <span className={styles.metaItem}>
              {t('agent.chipMaxTokens', {
                value:
                  agent.maxTokens >= 1000
                    ? `${Math.round(agent.maxTokens / 1000)}K`
                    : `${agent.maxTokens}`,
              })}
            </span>
          )}
          {countIds(agent.knowledgeBases) > 0 && (
            <span className={`${styles.metaItem} ${styles.metaKb}`}>
              <DatabaseOutlined />
              {t('agent.chipKb', { count: countIds(agent.knowledgeBases) })}
            </span>
          )}
          {countIds(agent.skillRefs) > 0 && (
            <span className={`${styles.metaItem} ${styles.metaSkill}`}>
              <ToolOutlined />
              {t('agent.chipSkill', { count: countIds(agent.skillRefs) })}
            </span>
          )}
          {agent.callCount != null && agent.callCount > 0 && (
            <span className={styles.metaItem}>
              {t('agent.chipRuns', { count: agent.callCount })}
            </span>
          )}
        </div>

        <div className={styles.cardActions} onClick={(e) => e.stopPropagation()}>
          <Tooltip title={t('agent.executionsTooltip')}>
            <Button
              size="small"
              icon={<ThunderboltOutlined />}
              className={styles.btnBlue}
              onClick={() => navigate(`/agents/${agent.id}/executions`)}
            />
          </Tooltip>
          <PermissionButton permission="agent:write" mode="hide">
            <Tooltip title={t('agent.tooltipEdit')}>
              <Button
                size="small"
                icon={<EditOutlined />}
                className={styles.btnGray}
                onClick={() => onEdit(agent.id)}
              />
            </Tooltip>
          </PermissionButton>
          {agent.status === 'draft' && (
            <PermissionButton permission="agent:publish" mode="hide">
              <Tooltip title={t('agent.tooltipPublish')}>
                <Button
                  size="small"
                  icon={<CheckCircleOutlined />}
                  className={styles.btnGreen}
                  onClick={() => onPublish(agent.id)}
                />
              </Tooltip>
            </PermissionButton>
          )}
          {agent.status === 'published' && (
            <PermissionButton permission="agent:publish" mode="hide">
              <Tooltip title={t('agent.tooltipDisable')}>
                <Button
                  size="small"
                  icon={<PauseCircleOutlined />}
                  className={styles.btnOrange}
                  onClick={() => onDisable(agent.id)}
                />
              </Tooltip>
            </PermissionButton>
          )}
          <PermissionButton permission="agent:delete" mode="hide">
            <Tooltip title={t('common.delete')}>
              <Popconfirm
                title={t('agent.deleteConfirm')}
                onConfirm={() => onDelete(agent.id)}
                okText={t('common.delete')}
                okButtonProps={{ danger: true }}
                cancelText={t('common.cancel')}
              >
                <Button size="small" icon={<DeleteOutlined />} className={styles.btnRed} />
              </Popconfirm>
            </Tooltip>
          </PermissionButton>
          <FavoriteButton
            type="agent"
            targetId={String(agent.id)}
            title={agent.name}
            description={agent.description}
            className={styles.btnGold}
          />
        </div>
      </GradientCard>
    </motion.div>
  )
}

export default function AgentListPage() {
  const [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState<AgentStatus | undefined>()
  const [current, setCurrent] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const navigate = useNavigate()
  const qc = useQueryClient()
  const { message } = App.useApp()
  const { t } = useTranslation()

  const STATUS_CFG: Record<AgentStatus, { color: string; label: string; icon: React.ReactNode }> = {
    draft: { color: 'default', label: t('agent.statusDraft'), icon: <EditOutlined /> },
    published: {
      color: 'success',
      label: t('agent.statusPublished'),
      icon: <CheckCircleOutlined />,
    },
    disabled: { color: 'warning', label: t('agent.statusDisabled'), icon: <PauseCircleOutlined /> },
  }

  const { data, isLoading } = useQuery({
    queryKey: ['agents', { keyword, status, current, pageSize }],
    queryFn: () =>
      agentApi.list({ keyword: keyword || undefined, status, current, size: pageSize }),
  })

  const deleteMutation = useMutation({
    mutationFn: agentApi.delete,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['agents'] })
      message.success(t('agent.deleteSuccess'))
    },
  })
  const publishMutation = useMutation({
    mutationFn: agentApi.publish,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['agents'] })
      message.success(t('agent.publishSuccess'))
    },
  })
  const disableMutation = useMutation({
    mutationFn: agentApi.disable,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['agents'] })
      message.success(t('agent.disableSuccess'))
    },
  })

  const agents = data?.records ?? []

  return (
    <div className={styles.root}>
      <div className={styles.pageHeader}>
        <div className={styles.toolbar}>
          <div className={styles.filters}>
            <Input
              prefix={<SearchOutlined />}
              placeholder={t('agent.searchPlaceholder')}
              value={keyword}
              onChange={(e) => {
                setKeyword(e.target.value)
                setCurrent(1)
              }}
              allowClear
              style={{ width: 240 }}
            />
            <Select
              placeholder={t('agent.statusPlaceholder')}
              allowClear
              value={status}
              onChange={(v) => {
                setStatus(v)
                setCurrent(1)
              }}
              style={{ width: 120 }}
              options={Object.entries(STATUS_CFG).map(([k, v]) => ({ label: v.label, value: k }))}
            />
          </div>
          <PermissionButton permission="agent:write" mode="hide">
            <GradientButton icon={<PlusOutlined />} onClick={() => navigate('/agents/new')}>
              {t('agent.createBtn')}
            </GradientButton>
          </PermissionButton>
        </div>
      </div>

      <div className={styles.body}>
        {isLoading ? (
          <Row gutter={[16, 16]}>
            {[1, 2, 3].map((i) => (
              <Col key={i} xs={24} sm={12} lg={8}>
                <Skeleton active />
              </Col>
            ))}
          </Row>
        ) : agents.length === 0 ? (
          <EmptyState
            title={t('agent.emptyTitle')}
            description={t('agent.emptyDesc')}
            action={
              <PermissionButton permission="agent:write" mode="hide">
                <GradientButton icon={<PlusOutlined />} onClick={() => navigate('/agents/new')}>
                  {t('agent.createBtn')}
                </GradientButton>
              </PermissionButton>
            }
          />
        ) : (
          <Row gutter={[16, 16]}>
            {agents.map((a) => (
              <Col key={a.id} xs={24} sm={12} lg={8}>
                <AgentCard
                  agent={a}
                  onEdit={(id) => navigate(`/agents/${id}/edit`)}
                  onDelete={(id) => deleteMutation.mutate(id)}
                  onPublish={(id) => publishMutation.mutate(id)}
                  onDisable={(id) => disableMutation.mutate(id)}
                />
              </Col>
            ))}
          </Row>
        )}
      </div>

      {(data?.total ?? 0) > 0 && (
        <div
          style={{
            display: 'flex',
            justifyContent: 'flex-end',
            alignItems: 'center',
            flexShrink: 0,
            padding: '12px 20px',
            borderTop: '1px solid var(--kf-border)',
          }}
        >
          <PageBar
            current={current}
            pageSize={pageSize}
            total={data!.total}
            onChange={(page, size) => {
              setCurrent(page)
              setPageSize(size)
            }}
          />
        </div>
      )}
    </div>
  )
}
