import { Collapse, Tag, Descriptions, Badge, Empty } from 'antd'
import { RobotOutlined, StarFilled } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { modelApi } from '@/api/skill'
import type { ModelInfo, ModelProvider } from '@/types/skill'
import styles from './ModelConfigPage.module.css'

const CAPABILITY_COLORS: Record<string, string> = {
  chat: 'blue',
  embedding: 'purple',
  vision: 'orange',
  code: 'green',
  reasoning: 'cyan',
  function_calling: 'magenta',
}

function formatPrice(price: number) {
  return `¥${price.toFixed(4)} / 1K token`
}

function formatContext(n: number) {
  if (n >= 1000000) return `${(n / 1000000).toFixed(0)}M`
  if (n >= 1000) return `${(n / 1000).toFixed(0)}K`
  return String(n)
}

function ModelCard({ model }: { model: ModelInfo }) {
  return (
    <div className={styles.modelCard}>
      <div className={styles.modelHeader}>
        <span className={styles.modelName}>{model.name}</span>
        {model.isDefault && (
          <Tag color="gold" icon={<StarFilled />}>
            默认
          </Tag>
        )}
      </div>
      {model.description && <p className={styles.modelDesc}>{model.description}</p>}
      <Descriptions size="small" column={2} style={{ marginTop: 8 }}>
        <Descriptions.Item label="上下文窗口">
          {formatContext(model.contextLength)}
        </Descriptions.Item>
        <Descriptions.Item label="输入价格">{formatPrice(model.inputPrice)}</Descriptions.Item>
        <Descriptions.Item label="输出价格">{formatPrice(model.outputPrice)}</Descriptions.Item>
      </Descriptions>
      {model.capabilities.length > 0 && (
        <div className={styles.capabilities}>
          {model.capabilities.map((cap) => (
            <Tag key={cap} color={CAPABILITY_COLORS[cap] ?? 'default'}>
              {cap}
            </Tag>
          ))}
        </div>
      )}
    </div>
  )
}

export default function ModelConfigPage() {
  const { data: providers, isLoading } = useQuery({
    queryKey: ['models'],
    queryFn: () => modelApi.list(),
    staleTime: 5 * 60 * 1000,
  })

  if (isLoading) {
    return (
      <div className={styles.root}>
        <div className={styles.topBar}>
          <h2 className={styles.pageTitle}>
            <RobotOutlined /> 模型配置
          </h2>
        </div>
        <div className={styles.skeleton} />
        <div className={styles.skeleton} />
      </div>
    )
  }

  const items = (providers ?? []).map((p: ModelProvider) => ({
    key: p.id,
    label: (
      <div className={styles.providerLabel}>
        <span className={styles.providerName}>{p.name}</span>
        <Badge count={p.models.length} color="blue" />
        <Tag color="processing">
          <Badge status="processing" /> 已接入
        </Tag>
      </div>
    ),
    children: (
      <div className={styles.modelGrid}>
        {p.models.map((m) => (
          <ModelCard key={m.id} model={m} />
        ))}
      </div>
    ),
  }))

  return (
    <div className={styles.root}>
      <div className={styles.topBar}>
        <h2 className={styles.pageTitle}>
          <RobotOutlined /> 模型配置
        </h2>
        <p className={styles.subtitle}>
          以下模型由系统管理员配置，仅供查看。如需更改请联系管理员。
        </p>
      </div>

      {!items.length ? (
        <Empty description="暂无模型配置" />
      ) : (
        <Collapse
          defaultActiveKey={providers?.map((p) => p.id) ?? []}
          items={items}
          style={{ background: 'transparent' }}
        />
      )}
    </div>
  )
}
