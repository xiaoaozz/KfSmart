import { useState } from 'react'
import { Input, Tag, Row, Col, Skeleton } from 'antd'
import {
  SearchOutlined,
  RobotOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  ApiOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons'
import { motion } from 'framer-motion'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { modelApi } from '@/api/skill'
import type { ModelConfig } from '@/types/skill'
import { GradientCard } from '@/components/base'
import { EmptyState } from '@/components/business'
import styles from './ModelConfigPage.module.css'

const PROVIDER_LABEL_MAP: Record<string, string> = {
  DeepSeek: 'skill.model.providerDeepseek',
  OpenAI: 'skill.model.providerOpenai',
  通义千问: 'skill.model.providerQwen',
  '智谱 AI': 'skill.model.providerZhipu',
  文心一言: 'skill.model.providerErnie',
  Anthropic: 'skill.model.providerAnthropic',
  Ollama: 'skill.model.providerOllama',
  其他: 'skill.model.providerOther',
}

const CATEGORY_MAP: Record<string, string> = {
  向量模型: 'skill.model.categoryEmbedding',
  多模态模型: 'skill.model.categoryMultimodal',
  代码模型: 'skill.model.categoryCode',
  本地模型: 'skill.model.categoryLocal',
  对话模型: 'skill.model.categoryChat',
}

const STATUS_MAP: Record<string, string> = {
  激活中: 'skill.model.statusActive',
  可用: 'skill.model.statusAvailable',
}

function ModelCard({
  model,
  t,
}: {
  model: ModelConfig
  t: (key: string, opts?: Record<string, unknown>) => string
}) {
  const providerLabel = t(PROVIDER_LABEL_MAP[model.providerLabel] ?? model.providerLabel)
  const category = t(CATEGORY_MAP[model.category] ?? model.category)
  const statusLabel = t(STATUS_MAP[model.status] ?? model.status)
  const description = model.remark
    ? model.remark
    : t('skill.model.autoDescription', {
        modelName: model.modelName,
        provider: providerLabel,
        category,
      })

  return (
    <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }}>
      <GradientCard className={styles.card}>
        <div className={styles.cardHeader}>
          <div className={styles.titleRow}>
            <span className={styles.cardIcon}>{model.icon}</span>
            <div className={styles.titleInfo}>
              <h3 className={styles.cardName}>{model.modelName}</h3>
              <span className={styles.cardSubName}>{model.name}</span>
            </div>
          </div>
          {model.active ? (
            <Tag color="success" icon={<CheckCircleOutlined />} style={{ fontSize: 12 }}>
              {statusLabel}
            </Tag>
          ) : (
            <Tag color="default" icon={<ClockCircleOutlined />} style={{ fontSize: 12 }}>
              {statusLabel}
            </Tag>
          )}
        </div>

        {description && <p className={styles.cardDesc}>{description}</p>}

        <div className={styles.cardMeta}>
          <Tag color="blue" style={{ fontSize: 12 }}>
            {providerLabel}
          </Tag>
          <Tag style={{ fontSize: 12 }}>{category}</Tag>
          <Tag style={{ fontSize: 12 }}>{model.authType}</Tag>
        </div>

        <div className={styles.cardParams}>
          <span className={styles.paramItem}>
            <ThunderboltOutlined /> {t('skill.model.paramTemperature')}: {model.temperature}
          </span>
          <span className={styles.paramItem}>
            <RobotOutlined /> {t('skill.model.paramMaxTokens')}: {model.maxTokens}
          </span>
          <span className={styles.paramItem}>
            {t('skill.model.paramTopP')}: {model.topP}
          </span>
        </div>

        <div className={styles.cardFooter}>
          <span className={styles.apiUrl} title={model.apiUrl}>
            <ApiOutlined style={{ fontSize: 12, marginRight: 4 }} />
            <code>{model.apiUrl}</code>
          </span>
        </div>
      </GradientCard>
    </motion.div>
  )
}

export default function ModelConfigPage() {
  const { t } = useTranslation()
  const [keyword, setKeyword] = useState('')

  const { data: models, isLoading } = useQuery({
    queryKey: ['models'],
    queryFn: () => modelApi.list(),
    staleTime: 5 * 60 * 1000,
  })

  const safeModels = models ?? []
  const filtered = keyword
    ? safeModels.filter(
        (m) =>
          m.modelName.toLowerCase().includes(keyword.toLowerCase()) ||
          m.name.toLowerCase().includes(keyword.toLowerCase()) ||
          m.providerLabel.toLowerCase().includes(keyword.toLowerCase()) ||
          m.category.toLowerCase().includes(keyword.toLowerCase()),
      )
    : safeModels

  return (
    <div className={styles.root}>
      <div className={styles.toolbar}>
        <Input
          prefix={<SearchOutlined />}
          placeholder={t('skill.model.searchPlaceholder')}
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          allowClear
          style={{ width: 240 }}
        />
      </div>

      {isLoading ? (
        <Row gutter={[16, 16]}>
          {[1, 2, 3, 4, 5, 6].map((i) => (
            <Col key={i} xs={24} sm={12} lg={8}>
              <Skeleton active />
            </Col>
          ))}
        </Row>
      ) : filtered.length === 0 ? (
        <EmptyState title={t('skill.model.empty')} />
      ) : (
        <Row gutter={[16, 16]}>
          {filtered.map((m) => (
            <Col key={m.id} xs={24} sm={12} lg={8}>
              <ModelCard model={m} t={t} />
            </Col>
          ))}
        </Row>
      )}
    </div>
  )
}
