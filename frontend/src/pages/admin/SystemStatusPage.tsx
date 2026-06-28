import { Progress, Tag, Badge, Skeleton } from 'antd'
import {
  DashboardOutlined,
  DatabaseOutlined,
  HddOutlined,
  CloudServerOutlined,
  ThunderboltOutlined,
  CheckCircleOutlined,
  ExclamationCircleOutlined,
  CloseCircleOutlined,
} from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { adminSystemApi, type SystemMetrics } from '@/api/admin'
import styles from './AdminPage.module.css'

function formatBytes(bytes: number): string {
  if (bytes >= 1024 ** 3) return `${(bytes / 1024 ** 3).toFixed(1)} GB`
  if (bytes >= 1024 ** 2) return `${(bytes / 1024 ** 2).toFixed(0)} MB`
  return `${(bytes / 1024).toFixed(0)} KB`
}

function formatMs(ms: number): string {
  if (ms >= 3600000) return `${(ms / 3600000).toFixed(1)}h`
  if (ms >= 60000) return `${(ms / 60000).toFixed(0)}m`
  return `${(ms / 1000).toFixed(0)}s`
}

function MetricCard({
  icon,
  label,
  value,
  unit,
  sub,
  percent,
  color,
}: {
  icon: React.ReactNode
  label: string
  value: string
  unit?: string
  sub?: string
  percent?: number
  color?: string
}) {
  return (
    <div className={styles.metricCard}>
      <div className={styles.metricHeader}>
        <span className={styles.metricLabel}>{label}</span>
        <span style={{ fontSize: 20, color: color ?? 'var(--kf-primary)' }}>{icon}</span>
      </div>
      <div>
        <span className={styles.metricValue}>{value}</span>
        {unit && <span className={styles.metricUnit}>{unit}</span>}
      </div>
      {percent !== undefined && (
        <Progress
          percent={Math.round(percent)}
          strokeColor={percent > 85 ? '#ff4d4f' : percent > 65 ? '#fa8c16' : 'var(--kf-primary)'}
          showInfo={false}
          size="small"
        />
      )}
      {sub && <div className={styles.metricSub}>{sub}</div>}
    </div>
  )
}

export default function SystemStatusPage() {
  const { t } = useTranslation()
  const { data, isLoading } = useQuery({
    queryKey: ['system-metrics'],
    queryFn: () => adminSystemApi.metrics(),
    refetchInterval: 5000,
  })

  const SERVICE_STATUS_CFG = {
    up: { color: 'success', icon: <CheckCircleOutlined />, label: t('admin.system.statusUp') },
    degraded: {
      color: 'warning',
      icon: <ExclamationCircleOutlined />,
      label: t('admin.system.statusDegraded'),
    },
    down: { color: 'error', icon: <CloseCircleOutlined />, label: t('admin.system.statusDown') },
  }

  if (isLoading || !data) {
    return (
      <div className={styles.root}>
        <div className={styles.topBar}>
          <h2 className={styles.pageTitle}>
            <DashboardOutlined /> {t('admin.system.title')}
          </h2>
        </div>
        <div className={styles.metricsGrid}>
          {Array.from({ length: 6 }).map((_, i) => (
            <div key={i} className={styles.metricCard}>
              <Skeleton active paragraph={{ rows: 3 }} />
            </div>
          ))}
        </div>
      </div>
    )
  }

  const m: SystemMetrics = data
  const heapPct = (m.jvm.heapUsed / m.jvm.heapMax) * 100
  const diskPct = (m.disk.used / m.disk.total) * 100
  const dbPct = (m.db.activeConnections / m.db.maxConnections) * 100
  const cachePct = m.cache.hitRate * 100

  return (
    <div className={styles.root}>
      <div className={styles.topBar}>
        <h2 className={styles.pageTitle}>
          <DashboardOutlined /> {t('admin.system.title')}
        </h2>
        <span style={{ fontSize: 12, color: 'var(--kf-muted-foreground)' }}>
          {t('admin.system.autoRefresh')}
        </span>
      </div>

      <div className={styles.metricsGrid}>
        <MetricCard
          icon={<ThunderboltOutlined />}
          label={t('admin.system.jvmHeap')}
          value={formatBytes(m.jvm.heapUsed)}
          unit={`/ ${formatBytes(m.jvm.heapMax)}`}
          percent={heapPct}
          sub={t('admin.system.uptime', { time: formatMs(m.jvm.uptime) })}
          color="#6366f1"
        />
        <MetricCard
          icon={<DashboardOutlined />}
          label={t('admin.system.cpu')}
          value={`${(m.cpu.usage * 100).toFixed(1)}`}
          unit="%"
          percent={m.cpu.usage * 100}
          sub={t('admin.system.coreLoad', { cores: m.cpu.cores, load: m.cpu.loadAvg.toFixed(2) })}
          color="#f59e0b"
        />
        <MetricCard
          icon={<HddOutlined />}
          label={t('admin.system.disk')}
          value={formatBytes(m.disk.used)}
          unit={`/ ${formatBytes(m.disk.total)}`}
          percent={diskPct}
          sub={m.disk.path}
          color="#10b981"
        />
        <MetricCard
          icon={<DatabaseOutlined />}
          label={t('admin.system.dbConnections')}
          value={String(m.db.activeConnections)}
          unit={`/ ${m.db.maxConnections}`}
          percent={dbPct}
          sub={t('admin.system.dbQueries', { count: m.db.queryCount.toLocaleString() })}
          color="#0052ff"
        />
        <MetricCard
          icon={<CloudServerOutlined />}
          label={t('admin.system.cacheHitRate')}
          value={cachePct.toFixed(1)}
          unit="%"
          percent={cachePct}
          sub={t('admin.system.cacheKeys', {
            count: m.cache.keyCount.toLocaleString(),
            mem: formatBytes(m.cache.memoryUsed),
          })}
          color="#8b5cf6"
        />
      </div>

      <h3 style={{ fontSize: 16, fontWeight: 600, color: 'var(--kf-foreground)', margin: '8px 0' }}>
        {t('admin.system.externalServices')}
      </h3>
      <div className={styles.serviceList}>
        {m.services.map((svc) => {
          const cfg = SERVICE_STATUS_CFG[svc.status]
          return (
            <div key={svc.name} className={styles.serviceRow}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                <Badge
                  status={
                    svc.status === 'up'
                      ? 'success'
                      : svc.status === 'degraded'
                        ? 'warning'
                        : 'error'
                  }
                />
                <span className={styles.serviceName}>{svc.name}</span>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                <span className={styles.serviceLatency}>{svc.latencyMs}ms</span>
                <Tag color={cfg.color} icon={cfg.icon}>
                  {cfg.label}
                </Tag>
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
