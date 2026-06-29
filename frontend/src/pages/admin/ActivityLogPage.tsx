import { useState } from 'react'
import { Input, Select, DatePicker, Button, Tag, Space, Tooltip } from 'antd'
import { SearchOutlined, ReloadOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { adminLogApi, type AdminActivityLog } from '@/api/admin'
import PageTable, { type TableColumnType } from '@/components/business/PageTable'
import { PageBar } from '@/components/business'
import styles from './AdminPage.module.css'

function fmtTime(isoStr: string): string {
  const d = new Date(isoStr)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

export default function ActivityLogPage() {
  const [keyword, setKeyword] = useState('')
  const [action, setAction] = useState('')
  const [current, setCurrent] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [dateRange, setDateRange] = useState<[string, string] | null>(null)
  const { t } = useTranslation()

  const ACTION_OPTIONS = [
    { label: t('admin.activityLog.actions.all'), value: '' },
    { label: t('admin.activityLog.actions.kbCreate'), value: '创建知识库' },
    { label: t('admin.activityLog.actions.kbUpdate'), value: '更新知识库' },
    { label: t('admin.activityLog.actions.docUpload'), value: '上传文档' },
    { label: t('admin.activityLog.actions.docUpdate'), value: '更新文档' },
    { label: t('admin.activityLog.actions.userJoin'), value: '用户加入' },
  ]

  const { data, isLoading, refetch } = useQuery({
    queryKey: ['admin-activity-logs', keyword, action, current, pageSize, dateRange],
    queryFn: () =>
      adminLogApi.list({
        keyword: keyword || undefined,
        action: action || undefined,
        current,
        size: pageSize,
        startTime: dateRange?.[0],
        endTime: dateRange?.[1],
      }),
    staleTime: 0,
    refetchInterval: 30_000,
  })

  const columns: TableColumnType<AdminActivityLog>[] = [
    {
      title: t('admin.activityLog.colTime'),
      dataIndex: 'occurredAt',
      width: 130,
      render: (v: string) => (
        <Tooltip title={v ? new Date(v).toLocaleString('zh-CN') : '-'}>
          <span style={{ fontFamily: 'var(--kf-font-mono)', fontSize: 12, cursor: 'default' }}>
            {v ? fmtTime(v) : '-'}
          </span>
        </Tooltip>
      ),
    },
    {
      title: t('admin.activityLog.colAction'),
      dataIndex: 'title',
      width: 130,
      render: (a: string, r: AdminActivityLog) => (
        <Tag color={r.color} style={{ fontSize: 12, margin: 0 }}>
          {a}
        </Tag>
      ),
    },
    {
      title: t('admin.activityLog.colDetail'),
      dataIndex: 'description',
      ellipsis: { showTitle: false },
      render: (d: string) => (
        <Tooltip title={d} placement="topLeft">
          <span style={{ fontSize: 13, color: 'var(--kf-muted-foreground)' }}>{d}</span>
        </Tooltip>
      ),
    },
  ]

  return (
    <div className={styles.root}>
      <div className={styles.topBar}>
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
            setCurrent(1)
          }}
          allowClear
        />
        <Select
          style={{ width: 150 }}
          options={ACTION_OPTIONS}
          value={action}
          onChange={(v) => {
            setAction(v)
            setCurrent(1)
          }}
        />
        <DatePicker.RangePicker
          style={{ width: 280 }}
          onChange={(_, strs) => {
            const [s, e] = strs as [string, string]
            setDateRange(s && e ? [s, e] : null)
            setCurrent(1)
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
              setCurrent(1)
            }}
          >
            {t('common.reset')}
          </Button>
        </Space>
      </div>

      <PageTable<AdminActivityLog>
        rowKey="id"
        columns={columns}
        dataSource={data?.records}
        loading={isLoading}
        showPagination={false}
      />
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
