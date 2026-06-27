import { useState } from 'react'
import { Table, Input, Select, DatePicker, Button, Tag, Space } from 'antd'
import { SearchOutlined, ReloadOutlined, FileTextOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { adminLogApi, type AdminActivityLog } from '@/api/admin'
import styles from './AdminPage.module.css'

const ACTION_OPTIONS = [
  { label: '全部操作', value: '' },
  { label: '用户登录', value: 'user.login' },
  { label: '用户登出', value: 'user.logout' },
  { label: '文件上传', value: 'file.upload' },
  { label: '文件删除', value: 'file.delete' },
  { label: '角色变更', value: 'role.update' },
  { label: 'API Key 创建', value: 'apikey.create' },
  { label: '系统配置', value: 'system.config' },
]

const STATUS_COLOR: Record<string, string> = { success: 'success', failed: 'error' }
const STATUS_LABEL: Record<string, string> = { success: '成功', failed: '失败' }

export default function ActivityLogPage() {
  const [keyword, setKeyword] = useState('')
  const [action, setAction] = useState('')
  const [page, setPage] = useState(1)
  const [dateRange, setDateRange] = useState<[string, string] | null>(null)

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
      title: '时间',
      dataIndex: 'createTime',
      width: 170,
      render: (t: string) => (
        <span style={{ fontFamily: 'var(--kf-font-mono)', fontSize: 12 }}>
          {new Date(t).toLocaleString()}
        </span>
      ),
    },
    {
      title: '操作人',
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
      title: '操作',
      dataIndex: 'action',
      width: 150,
      render: (a: string) => (
        <Tag style={{ fontFamily: 'var(--kf-font-mono)', fontSize: 11 }}>{a}</Tag>
      ),
    },
    {
      title: '资源',
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
      title: '详情',
      dataIndex: 'detail',
      render: (d: string) => (
        <span style={{ fontSize: 13, color: 'var(--kf-muted-foreground)' }}>{d}</span>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 75,
      render: (s: AdminActivityLog['status']) => (
        <Tag color={STATUS_COLOR[s]}>{STATUS_LABEL[s]}</Tag>
      ),
    },
  ]

  return (
    <div className={styles.root}>
      <div className={styles.topBar}>
        <h2 className={styles.pageTitle}>
          <FileTextOutlined /> 操作日志
        </h2>
        <Button icon={<ReloadOutlined />} onClick={() => refetch()}>
          刷新
        </Button>
      </div>

      <div className={styles.filters}>
        <Input
          placeholder="搜索用户名 / IP / 详情"
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
            重置
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
          showTotal: (t) => `共 ${t} 条`,
        }}
        size="middle"
      />
    </div>
  )
}
