import { useState } from 'react'
import { Table, Input, Select, DatePicker, Button, Tag, Space } from 'antd'
import { SearchOutlined, ReloadOutlined, FileTextOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { adminLogApi, type AdminActivityLog } from '@/api/admin'
import styles from './AdminPage.module.css'

export default function ActivityLogPage() {
  const [keyword, setKeyword] = useState('')
  const [action, setAction] = useState('')
  const [page, setPage] = useState(1)
  const [dateRange, setDateRange] = useState<[string, string] | null>(null)
  const { t } = useTranslation()

  const ACTION_OPTIONS = [
    { label: t('admin.activityLog.actions.all'), value: '' },
    { label: t('admin.activityLog.actions.userLogin'), value: 'user.login' },
    { label: t('admin.activityLog.actions.userLogout'), value: 'user.logout' },
    { label: t('admin.activityLog.actions.fileUpload'), value: 'file.upload' },
    { label: t('admin.activityLog.actions.fileDelete'), value: 'file.delete' },
    { label: t('admin.activityLog.actions.roleUpdate'), value: 'role.update' },
    { label: t('admin.activityLog.actions.apikeyCreate'), value: 'apikey.create' },
    { label: t('admin.activityLog.actions.systemConfig'), value: 'system.config' },
  ]

  const { data, isLoading, refetch } = useQuery({
    queryKey: ['admin-activity-logs', keyword, action, page, dateRange],
    queryFn: () =>
      adminLogApi.list({
        keyword: keyword || undefined,
        action: action || undefined,
        current: page,
        size: 20,
        startTime: dateRange?.[0],
        endTime: dateRange?.[1],
      }),
  })

  const columns = [
    {
      title: t('admin.activityLog.colTime'),
      dataIndex: 'createTime',
      width: 170,
      render: (v: string) => (
        <span style={{ fontFamily: 'var(--kf-font-mono)', fontSize: 12 }}>
          {new Date(v).toLocaleString()}
        </span>
      ),
    },
    {
      title: t('admin.activityLog.colOperator'),
      dataIndex: 'username',
      width: 120,
      render: (u: string, r: AdminActivityLog) => (
        <div>
          <div style={{ fontWeight: 600 }}>{u}</div>
          <div
            style={{
              fontSize: 11,
              color: 'var(--kf-muted-foreground)',
              fontFamily: 'var(--kf-font-mono)',
            }}
          >
            {r.ip}
          </div>
        </div>
      ),
    },
    {
      title: t('admin.activityLog.colAction'),
      dataIndex: 'action',
      width: 150,
      render: (a: string) => (
        <Tag style={{ fontFamily: 'var(--kf-font-mono)', fontSize: 11 }}>{a}</Tag>
      ),
    },
    {
      title: t('admin.activityLog.colResource'),
      dataIndex: 'resource',
      width: 100,
      render: (res: string, r: AdminActivityLog) => (
        <div>
          <div style={{ fontSize: 12 }}>{res}</div>
          {r.resourceId && (
            <div
              style={{
                fontSize: 11,
                color: 'var(--kf-muted-foreground)',
                fontFamily: 'var(--kf-font-mono)',
              }}
            >
              #{r.resourceId}
            </div>
          )}
        </div>
      ),
    },
    {
      title: t('admin.activityLog.colDetail'),
      dataIndex: 'detail',
      render: (d: string) => (
        <span style={{ fontSize: 13, color: 'var(--kf-muted-foreground)' }}>{d}</span>
      ),
    },
    {
      title: t('admin.activityLog.colStatus'),
      dataIndex: 'status',
      width: 75,
      render: (s: AdminActivityLog['status']) => {
        const colorMap: Record<string, string> = { success: 'success', failed: 'error' }
        const labelKey =
          s === 'success' ? 'admin.activityLog.statusSuccess' : 'admin.activityLog.statusFailed'
        return <Tag color={colorMap[s]}>{t(labelKey)}</Tag>
      },
    },
  ]

  return (
    <div className={styles.root}>
      <div className={styles.topBar}>
        <h2 className={styles.pageTitle}>
          <FileTextOutlined /> {t('admin.activityLog.title')}
        </h2>
        <Button icon={<ReloadOutlined />} onClick={() => refetch()}>
          {t('common.refresh')}
        </Button>
      </div>

      <div className={styles.filters}>
        <Input
          placeholder={t('admin.activityLog.searchPlaceholder')}
          prefix={<SearchOutlined />}
          style={{ width: 220 }}
          value={keyword}
          onChange={(e) => {
            setKeyword(e.target.value)
            setPage(1)
          }}
          allowClear
        />
        <Select
          style={{ width: 150 }}
          options={ACTION_OPTIONS}
          value={action}
          onChange={(v) => {
            setAction(v)
            setPage(1)
          }}
        />
        <DatePicker.RangePicker
          style={{ width: 280 }}
          onChange={(_, strs) => {
            const [s, e] = strs as [string, string]
            setDateRange(s && e ? [s, e] : null)
            setPage(1)
          }}
        />
        <Space>
          <Button
            type="link"
            size="small"
            onClick={() => {
              setKeyword('')
              setAction('')
              setDateRange(null)
              setPage(1)
            }}
          >
            {t('common.reset')}
          </Button>
        </Space>
      </div>

      <Table
        rowKey="id"
        columns={columns}
        dataSource={data?.records}
        loading={isLoading}
        pagination={{
          current: page,
          pageSize: 20,
          total: data?.total,
          onChange: setPage,
          showTotal: (total) => t('admin.activityLog.total', { count: total }),
        }}
        size="middle"
      />
    </div>
  )
}
