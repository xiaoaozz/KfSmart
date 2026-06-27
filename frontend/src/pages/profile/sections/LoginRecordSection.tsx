import { useState } from 'react'
import { Tag, Tooltip } from 'antd'
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  GlobalOutlined,
  LaptopOutlined,
} from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { profileApi, type LoginRecord } from '@/api/profile'
import PageTable, { type TableColumnType } from '@/components/business/PageTable'
import styles from './Section.module.css'

export default function LoginRecordSection() {
  const [current, setCurrent] = useState(1)
  const [pageSize, setPageSize] = useState(10)

  const { data, isLoading } = useQuery({
    queryKey: ['login-records', current, pageSize],
    queryFn: () => profileApi.getLoginRecords({ page: current, size: pageSize }),
  })

  const columns: TableColumnType<LoginRecord>[] = [
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      render: (s: LoginRecord['status'], record: LoginRecord) =>
        s === 'SUCCESS' ? (
          <Tag color="success" icon={<CheckCircleOutlined />}>
            成功
          </Tag>
        ) : (
          <Tooltip title={record.failReason || '登录失败'}>
            <Tag color="error" icon={<CloseCircleOutlined />}>
              失败
            </Tag>
          </Tooltip>
        ),
    },
    {
      title: 'IP 地址',
      dataIndex: 'ipAddress',
      width: 140,
      render: (ip?: string) =>
        ip ? (
          <span style={{ fontFamily: 'var(--kf-font-mono)', fontSize: 12 }}>
            <GlobalOutlined style={{ marginRight: 4 }} />
            {ip}
          </span>
        ) : (
          <span style={{ color: 'var(--kf-muted-foreground)' }}>—</span>
        ),
    },
    {
      title: '设备 / 浏览器',
      dataIndex: 'deviceInfo',
      render: (d?: string) =>
        d ? (
          <Tooltip title={d}>
            <span>
              <LaptopOutlined style={{ marginRight: 4 }} />
              {d.slice(0, 40)}
              {d.length > 40 ? '…' : ''}
            </span>
          </Tooltip>
        ) : (
          <span style={{ color: 'var(--kf-muted-foreground)' }}>—</span>
        ),
    },
    {
      title: '位置',
      dataIndex: 'location',
      width: 120,
      render: (loc?: string) =>
        loc ?? <span style={{ color: 'var(--kf-muted-foreground)' }}>—</span>,
    },
    {
      title: '时间',
      dataIndex: 'loginTime',
      width: 160,
      render: (t?: string) => (t ? new Date(t).toLocaleString() : '—'),
    },
  ]

  return (
    <div className={styles.section}>
      <h3 className={styles.sectionTitle}>登录记录</h3>
      <PageTable<LoginRecord>
        rowKey="id"
        columns={columns}
        dataSource={data?.content}
        loading={isLoading}
        total={data?.totalElements}
        current={current}
        pageSize={pageSize}
        onPageChange={(p, s) => {
          setCurrent(p)
          setPageSize(s)
        }}
      />
    </div>
  )
}
