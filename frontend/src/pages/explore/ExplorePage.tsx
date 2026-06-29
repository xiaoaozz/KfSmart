import { useState } from 'react'
import { Input, Tabs, Tag, Row, Col, Skeleton, Badge, Empty } from 'antd'
import {
  SearchOutlined,
  RobotOutlined,
  ApartmentOutlined,
  PlayCircleOutlined,
  CheckCircleOutlined,
} from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { motion } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import { agentApi } from '@/api/agent'
import { workflowApi } from '@/api/workflow'
import type { Agent } from '@/types/agent'
import type { WorkflowSummary } from '@/types/workflow'
import { GradientCard, GradientButton } from '@/components/base'
import styles from './ExplorePage.module.css'

// ------------------------------------------------------------------ Agent Card
function ExploreAgentCard({ agent, onRun }: { agent: Agent; onRun: (agent: Agent) => void }) {
  const { t } = useTranslation()
  return (
    <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }}>
      <GradientCard className={styles.card}>
        <div className={styles.cardHeader}>
          <div className={styles.cardIconWrap}>
            <RobotOutlined />
          </div>
          <Tag color="success" icon={<CheckCircleOutlined />} style={{ fontSize: 12 }}>
            {t('agent.statusPublished')}
          </Tag>
        </div>

        <h3 className={styles.cardName}>{agent.name}</h3>
        {agent.description && <p className={styles.cardDesc}>{agent.description}</p>}

        <div className={styles.cardMeta}>
          <span className={styles.metaItem}>
            {t('agent.metaModel', { name: agent.models ?? '-' })}
          </span>
        </div>

        <div className={styles.cardActions}>
          <GradientButton size="sm" icon={<PlayCircleOutlined />} onClick={() => onRun(agent)}>
            {t('common.run')}
          </GradientButton>
        </div>
      </GradientCard>
    </motion.div>
  )
}

// ------------------------------------------------------------------ Workflow Card
function ExploreWorkflowCard({
  workflow,
  onRun,
}: {
  workflow: WorkflowSummary
  onRun: (wf: WorkflowSummary) => void
}) {
  const { t } = useTranslation()
  return (
    <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }}>
      <GradientCard className={styles.card}>
        <div className={styles.cardHeader}>
          <div
            className={styles.cardIconWrap}
            style={{ background: 'var(--kf-accent-gradient-r)' }}
          >
            <ApartmentOutlined />
          </div>
          <Tag color="success" icon={<CheckCircleOutlined />} style={{ fontSize: 12 }}>
            {t('agent.statusPublished')}
          </Tag>
        </div>

        <h3 className={styles.cardName}>{workflow.name}</h3>
        {workflow.description && <p className={styles.cardDesc}>{workflow.description}</p>}

        <div className={styles.cardMeta}>
          {(workflow.callCount ?? 0) > 0 && (
            <span className={styles.metaItem}>
              {t('explore.runCount', { count: workflow.callCount })}
            </span>
          )}
        </div>

        <div className={styles.cardActions}>
          <GradientButton size="sm" icon={<PlayCircleOutlined />} onClick={() => onRun(workflow)}>
            {t('common.run')}
          </GradientButton>
        </div>
      </GradientCard>
    </motion.div>
  )
}

// ------------------------------------------------------------------ Main Page
export default function ExplorePage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [tab, setTab] = useState<'agents' | 'workflows'>('agents')
  const [agentKeyword, setAgentKeyword] = useState('')
  const [workflowKeyword, setWorkflowKeyword] = useState('')

  const { data: agentData, isLoading: agentLoading } = useQuery({
    queryKey: ['explore-agents', agentKeyword],
    queryFn: () =>
      agentApi.list({ keyword: agentKeyword || undefined, status: 'published', size: 100 }),
    enabled: tab === 'agents',
  })

  const { data: workflowData, isLoading: workflowLoading } = useQuery({
    queryKey: ['explore-workflows', workflowKeyword],
    queryFn: () =>
      workflowApi.list({ keyword: workflowKeyword || undefined, status: 'published', size: 100 }),
    enabled: tab === 'workflows',
  })

  const agents = (agentData?.records ?? []).filter((a) => a.status === 'published')
  const workflows = (workflowData?.records ?? []).filter((w) => w.status === 'published')

  const tabItems = [
    {
      key: 'agents',
      label: (
        <span>
          <RobotOutlined />
          {t('nav.agents')}
          {agents.length > 0 && (
            <Badge
              count={agents.length}
              size="small"
              style={{ marginLeft: 6, backgroundColor: 'var(--kf-accent)' }}
            />
          )}
        </span>
      ),
      children: (
        <div className={styles.tabBody}>
          <div className={styles.searchBar}>
            <Input
              prefix={<SearchOutlined />}
              placeholder={t('explore.searchAgents')}
              value={agentKeyword}
              onChange={(e) => setAgentKeyword(e.target.value)}
              allowClear
              style={{ width: 280 }}
            />
          </div>
          <div className={styles.scrollArea}>
            {agentLoading ? (
              <Row gutter={[16, 16]}>
                {[1, 2, 3].map((i) => (
                  <Col key={i} xs={24} sm={12} lg={8}>
                    <Skeleton active />
                  </Col>
                ))}
              </Row>
            ) : agents.length === 0 ? (
              <Empty description={t('explore.agentEmpty')} style={{ marginTop: 60 }} />
            ) : (
              <Row gutter={[16, 16]}>
                {agents.map((a) => (
                  <Col key={a.id} xs={24} sm={12} lg={8}>
                    <ExploreAgentCard
                      agent={a}
                      onRun={(ag) => navigate(`/explore/agent/${ag.id}`)}
                    />
                  </Col>
                ))}
              </Row>
            )}
          </div>
        </div>
      ),
    },
    {
      key: 'workflows',
      label: (
        <span>
          <ApartmentOutlined />
          {t('nav.workflows')}
          {workflows.length > 0 && (
            <Badge
              count={workflows.length}
              size="small"
              style={{ marginLeft: 6, backgroundColor: 'var(--kf-accent)' }}
            />
          )}
        </span>
      ),
      children: (
        <div className={styles.tabBody}>
          <div className={styles.searchBar}>
            <Input
              prefix={<SearchOutlined />}
              placeholder={t('explore.searchWorkflows')}
              value={workflowKeyword}
              onChange={(e) => setWorkflowKeyword(e.target.value)}
              allowClear
              style={{ width: 280 }}
            />
          </div>
          <div className={styles.scrollArea}>
            {workflowLoading ? (
              <Row gutter={[16, 16]}>
                {[1, 2, 3].map((i) => (
                  <Col key={i} xs={24} sm={12} lg={8}>
                    <Skeleton active />
                  </Col>
                ))}
              </Row>
            ) : workflows.length === 0 ? (
              <Empty description={t('explore.workflowEmpty')} style={{ marginTop: 60 }} />
            ) : (
              <Row gutter={[16, 16]}>
                {workflows.map((w) => (
                  <Col key={w.id} xs={24} sm={12} lg={8}>
                    <ExploreWorkflowCard
                      workflow={w}
                      onRun={(wf) => navigate(`/explore/workflow/${wf.id}`)}
                    />
                  </Col>
                ))}
              </Row>
            )}
          </div>
        </div>
      ),
    },
  ]

  return (
    <div className={styles.root}>
      <Tabs
        activeKey={tab}
        onChange={(k) => setTab(k as 'agents' | 'workflows')}
        items={tabItems}
        className={styles.tabs}
        size="large"
      />
    </div>
  )
}
