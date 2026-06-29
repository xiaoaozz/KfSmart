import { Badge, Button, Progress, Skeleton, Space, Spin, Tag } from 'antd'
import {
  ApiOutlined,
  CheckCircleOutlined,
  ClearOutlined,
  CloseCircleOutlined,
  DashboardOutlined,
  DatabaseOutlined,
  DesktopOutlined,
  ExclamationCircleOutlined,
  FileTextOutlined,
  GlobalOutlined,
  HddOutlined,
  ReloadOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { message } from 'antd'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import {
  adminSystemApi,
  type AlertLevel,
  type ServiceStatus,
  type SystemMetrics,
} from '@/api/admin'
import styles from './AdminPage.module.css'

function formatBytes(bytes: number): string {
  if (bytes >= 1024 ** 3) return `${(bytes / 1024 ** 3).toFixed(1)} GB`
  if (bytes >= 1024 ** 2) return `${(bytes / 1024 ** 2).toFixed(0)} MB`
  return `${(bytes / 1024).toFixed(0)} KB`
}

function formatUptime(ms: number): string {
  const s = Math.floor(ms / 1000)
  const d = Math.floor(s / 86400)
  const h = Math.floor((s % 86400) / 3600)
  const m = Math.floor((s % 3600) / 60)
  if (d > 0) return `${d}d ${h}h ${m}m`
  if (h > 0) return `${h}h ${m}m`
  return `${m}m ${s % 60}s`
}

const STATUS_CFG = {
  normal: { badgeStatus: 'success' as const, label: 'statusNormal' },
  warning: { badgeStatus: 'warning' as const, label: 'statusWarning' },
  error: { badgeStatus: 'error' as const, label: 'statusError' },
}

const SVC_CFG: Record<ServiceStatus, { color: string; icon: React.ReactNode; labelKey: string }> = {
  up: { color: 'success', icon: <CheckCircleOutlined />, labelKey: 'statusUp' },
  degraded: { color: 'warning', icon: <ExclamationCircleOutlined />, labelKey: 'statusDegraded' },
  down: { color: 'error', icon: <CloseCircleOutlined />, labelKey: 'statusDown' },
}

const ALERT_CFG: Record<
  AlertLevel,
  { bg: string; border: string; text: string; tagColor: string }
> = {
  warning: { bg: '#fff7e6', border: '#ffd591', text: '#d46b08', tagColor: 'orange' },
  error: { bg: '#fff2f0', border: '#ffccc7', text: '#cf1322', tagColor: 'red' },
  critical: { bg: '#fff1f0', border: '#ff4d4f', text: '#a8071a', tagColor: 'red' },
}

function ResourceCard({
  icon,
  label,
  value,
  unit,
  sub,
  percent,
  color,
  loading,
}: {
  icon: React.ReactNode
  label: string
  value: string
  unit?: string
  sub?: string
  percent?: number
  color?: string
  loading?: boolean
}) {
  const strokeColor =
    (percent ?? 0) > 85
      ? '#ff4d4f'
      : (percent ?? 0) > 65
        ? '#fa8c16'
        : (color ?? 'var(--kf-primary)')
  return (
    <div className={styles.metricCard}>
      <div className={styles.metricHeader}>
        <span className={styles.metricLabel}>{label}</span>
        <span
          style={{ fontSize: 20, color: color ?? 'var(--kf-primary)', opacity: loading ? 0.35 : 1 }}
        >
          {icon}
        </span>
      </div>
      {loading ? (
        <Skeleton active title={{ width: '55%' }} paragraph={{ rows: 1, width: ['75%'] }} />
      ) : (
        <>
          <div>
            <span className={styles.metricValue}>{value}</span>
            {unit && <span className={styles.metricUnit}>{unit}</span>}
          </div>
          {percent !== undefined && (
            <Progress
              percent={Math.round(percent)}
              strokeColor={strokeColor}
              showInfo={false}
              size="small"
            />
          )}
          {sub && <div className={styles.metricSub}>{sub}</div>}
        </>
      )}
    </div>
  )
}

export default function SystemStatusPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [messageApi, contextHolder] = message.useMessage()

  const { data, isLoading, isFetching, dataUpdatedAt } = useQuery({
    queryKey: ['system-metrics'],
    queryFn: () => adminSystemApi.metrics(),
    refetchInterval: 10_000,
    placeholderData: keepPreviousData,
  })

  const clearMut = useMutation({
    mutationFn: () => adminSystemApi.clearCache(),
    onSuccess: () => {
      messageApi.success(t('admin.system.clearCacheSuccess'))
      queryClient.invalidateQueries({ queryKey: ['system-metrics'] })
    },
    onError: () => messageApi.error(t('common.error')),
  })

  // 始终渲染页面，data 未到时各区块显示骨架/检查中状态
  const m: SystemMetrics | null = data ?? null
  const cpuPct = m ? m.cpu.usage * 100 : 0
  const memPct =
    m && m.memory.systemTotal > 0 ? (m.memory.systemUsed / m.memory.systemTotal) * 100 : 0
  const heapPct =
    m && m.memory.jvmHeapMax > 0 ? (m.memory.jvmHeapUsed / m.memory.jvmHeapMax) * 100 : 0
  const diskPct = m && m.disk.total > 0 ? (m.disk.used / m.disk.total) * 100 : 0

  const statusCfg = m ? (STATUS_CFG[m.overview.status] ?? STATUS_CFG.normal) : STATUS_CFG.normal
  const lastUpdate = dataUpdatedAt ? new Date(dataUpdatedAt).toLocaleTimeString() : '--'

  return (
    <div className={styles.root}>
      {contextHolder}

      {/* ── 标题栏 ── */}
      <div className={styles.topBar}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
          {isLoading ? (
            <Skeleton.Input active size="small" style={{ width: 110 }} />
          ) : (
            <Badge
              status={statusCfg.badgeStatus}
              text={
                <span style={{ fontSize: 13, fontWeight: 600 }}>
                  {t(`admin.system.${statusCfg.label}`)}
                </span>
              }
            />
          )}
          {m && (
            <span style={{ fontSize: 12, color: 'var(--kf-muted-foreground)' }}>
              {t('admin.system.uptime', { time: formatUptime(m.overview.uptime) })}
            </span>
          )}
        </div>
        <Space size={6}>
          <span style={{ fontSize: 12, color: 'var(--kf-muted-foreground)' }}>
            {t('admin.system.lastUpdate', { time: lastUpdate })}
          </span>
          <Button
            size="small"
            icon={<ReloadOutlined />}
            loading={isFetching}
            onClick={() => queryClient.invalidateQueries({ queryKey: ['system-metrics'] })}
          >
            {t('admin.system.refreshNow')}
          </Button>
          <Button
            size="small"
            icon={<ClearOutlined />}
            loading={clearMut.isPending}
            onClick={() => clearMut.mutate()}
          >
            {t('admin.system.clearCache')}
          </Button>
          <Button
            size="small"
            icon={<FileTextOutlined />}
            onClick={() => navigate('/admin/activity-logs')}
          >
            {t('admin.system.viewLogs')}
          </Button>
        </Space>
      </div>

      {/* ── 资源监控 ── */}
      <div>
        <h3 className={styles.sectionTitle}>{t('admin.system.resources')}</h3>
        <div className={styles.metricsGrid}>
          <ResourceCard
            loading={isLoading}
            icon={<DashboardOutlined />}
            label={t('admin.system.cpu')}
            value={cpuPct.toFixed(1)}
            unit="%"
            percent={cpuPct}
            sub={
              m
                ? t('admin.system.coreLoad', { cores: m.cpu.cores, load: m.cpu.loadAvg.toFixed(2) })
                : ''
            }
            color="#f59e0b"
          />
          {!m || m.memory.systemTotal > 0 ? (
            <ResourceCard
              loading={isLoading}
              icon={<DesktopOutlined />}
              label={t('admin.system.systemMemory')}
              value={m ? formatBytes(m.memory.systemUsed) : ''}
              unit={m ? `/ ${formatBytes(m.memory.systemTotal)}` : ''}
              percent={memPct}
              sub={
                m
                  ? t('admin.system.jvmHeapSub', {
                      used: formatBytes(m.memory.jvmHeapUsed),
                      max: formatBytes(m.memory.jvmHeapMax),
                    })
                  : ''
              }
              color="#6366f1"
            />
          ) : (
            <ResourceCard
              loading={isLoading}
              icon={<DatabaseOutlined />}
              label={t('admin.system.jvmHeap')}
              value={m ? formatBytes(m.memory.jvmHeapUsed) : ''}
              unit={m ? `/ ${formatBytes(m.memory.jvmHeapMax)}` : ''}
              percent={heapPct}
              sub={m ? t('admin.system.uptime', { time: formatUptime(m.overview.uptime) }) : ''}
              color="#6366f1"
            />
          )}
          <ResourceCard
            loading={isLoading}
            icon={<HddOutlined />}
            label={t('admin.system.disk')}
            value={m ? formatBytes(m.disk.used) : ''}
            unit={m ? `/ ${formatBytes(m.disk.total)}` : ''}
            percent={diskPct}
            sub={m?.disk.path ?? ''}
            color="#10b981"
          />
        </div>
      </div>

      {/* ── 服务状态 + 在线统计 + 系统信息 ── */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
        {/* 服务状态 */}
        <div>
          <h3 className={styles.sectionTitle}>{t('admin.system.externalServices')}</h3>
          <div className={styles.serviceList}>
            {isLoading
              ? Array.from({ length: 5 }).map((_, i) => (
                  <div key={i} className={styles.serviceRow}>
                    <Skeleton.Input active size="small" style={{ width: 100 }} />
                    <Skeleton.Input active size="small" style={{ width: 56 }} />
                  </div>
                ))
              : m!.services.map((svc) => {
                  const cfg = SVC_CFG[svc.status] ?? SVC_CFG.down
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
                      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                        {svc.latencyMs > 0 && (
                          <span className={styles.serviceLatency}>{svc.latencyMs}ms</span>
                        )}
                        <Tag color={cfg.color} icon={cfg.icon}>
                          {t(`admin.system.${cfg.labelKey}`)}
                        </Tag>
                      </div>
                    </div>
                  )
                })}
          </div>
        </div>

        {/* 在线统计 + 系统信息 */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <div>
            <h3 className={styles.sectionTitle}>{t('admin.system.onlineStats')}</h3>
            <div className={styles.metricCard}>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
                {(['onlineUsers', 'activeConnections'] as const).map((key, i) => (
                  <div key={key}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 4 }}>
                      {i === 0 ? (
                        <ApiOutlined style={{ color: 'var(--kf-primary)', fontSize: 14 }} />
                      ) : (
                        <GlobalOutlined style={{ color: 'var(--kf-primary)', fontSize: 14 }} />
                      )}
                      <span className={styles.metricLabel}>{t(`admin.system.${key}`)}</span>
                    </div>
                    {isLoading ? (
                      <Skeleton.Input active size="default" style={{ width: 56 }} />
                    ) : (
                      <span
                        style={{
                          fontSize: 28,
                          fontWeight: 700,
                          fontFamily: 'var(--kf-font-mono)',
                          color: 'var(--kf-foreground)',
                        }}
                      >
                        {m!.online[key]}
                      </span>
                    )}
                  </div>
                ))}
              </div>
            </div>
          </div>

          <div>
            <h3 className={styles.sectionTitle}>{t('admin.system.systemInfo')}</h3>
            <div className={styles.metricCard} style={{ gap: 0 }}>
              {isLoading
                ? Array.from({ length: 5 }).map((_, i) => (
                    <div
                      key={i}
                      style={{
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center',
                        padding: '7px 0',
                        borderBottom: i < 4 ? '1px solid var(--kf-border)' : 'none',
                      }}
                    >
                      <Skeleton.Input active size="small" style={{ width: 72 }} />
                      <Skeleton.Input active size="small" style={{ width: 110 }} />
                    </div>
                  ))
                : (
                    [
                      [t('admin.system.appVersion'), m!.overview.appVersion],
                      [t('admin.system.hostname'), m!.overview.hostname],
                      [t('admin.system.ipAddress'), m!.overview.ipAddress],
                      [t('admin.system.osVersion'), m!.overview.osName],
                      [t('admin.system.javaVersion'), m!.overview.javaVersion],
                    ] as [string, string][]
                  ).map(([label, value], idx, arr) => (
                    <div
                      key={label}
                      style={{
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center',
                        fontSize: 12,
                        padding: '6px 0',
                        borderBottom: idx < arr.length - 1 ? '1px solid var(--kf-border)' : 'none',
                      }}
                    >
                      <span style={{ color: 'var(--kf-muted-foreground)' }}>{label}</span>
                      <span
                        style={{
                          fontFamily: 'var(--kf-font-mono)',
                          color: 'var(--kf-foreground)',
                          maxWidth: '62%',
                          textAlign: 'right',
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                          whiteSpace: 'nowrap',
                        }}
                      >
                        {value}
                      </span>
                    </div>
                  ))}
            </div>
          </div>
        </div>
      </div>

      {/* ── 告警中心 ── */}
      <div>
        <h3 className={styles.sectionTitle}>{t('admin.system.alertCenter')}</h3>
        {isLoading ? (
          <div
            style={{
              padding: '14px 18px',
              background: 'var(--kf-card)',
              border: '1px solid var(--kf-border)',
              borderRadius: 'var(--kf-radius-md)',
              display: 'flex',
              alignItems: 'center',
              gap: 10,
              color: 'var(--kf-muted-foreground)',
              fontSize: 13,
            }}
          >
            <Spin size="small" />
            <span>{t('admin.system.checkingAlerts', '正在检测告警…')}</span>
          </div>
        ) : m!.alerts.length === 0 ? (
          <div
            style={{
              padding: '14px 18px',
              background: 'var(--kf-card)',
              border: '1px solid var(--kf-border)',
              borderRadius: 'var(--kf-radius-md)',
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              color: 'var(--kf-muted-foreground)',
              fontSize: 13,
            }}
          >
            <CheckCircleOutlined style={{ color: '#52c41a', fontSize: 16 }} />
            {t('admin.system.noAlerts')}
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            {m!.alerts.map((alert) => {
              const cfg = ALERT_CFG[alert.level] ?? ALERT_CFG.warning
              return (
                <div
                  key={alert.id}
                  style={{
                    padding: '10px 14px',
                    background: cfg.bg,
                    border: `1px solid ${cfg.border}`,
                    borderRadius: 'var(--kf-radius-sm)',
                    display: 'flex',
                    alignItems: 'flex-start',
                    gap: 10,
                  }}
                >
                  <WarningOutlined style={{ color: cfg.text, fontSize: 15, marginTop: 1 }} />
                  <div style={{ flex: 1 }}>
                    <div style={{ fontWeight: 600, color: cfg.text, fontSize: 13 }}>
                      {alert.title}
                    </div>
                    <div
                      style={{ fontSize: 12, color: 'var(--kf-muted-foreground)', marginTop: 2 }}
                    >
                      {alert.message}
                    </div>
                  </div>
                  <Tag color={cfg.tagColor}>{t(`admin.system.alertLevel.${alert.level}`)}</Tag>
                </div>
              )
            })}
          </div>
        )}
      </div>
    </div>
  )
}
