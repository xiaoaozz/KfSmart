import { useState } from 'react'
import { Tag, Tooltip } from 'antd'
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  GlobalOutlined,
  LaptopOutlined,
} from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { profileApi, type LoginRecord } from '@/api/profile'
import PageTable, { type TableColumnType } from '@/components/business/PageTable'
import styles from './Section.module.css'

export default function LoginRecordSection() {
  const [current, setCurrent] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const { t } = useTranslation()

  const { data, isLoading } = useQuery({
    queryKey: ['login-records', current, pageSize],
    queryFn: () => profileApi.getLoginRecords({ page: current, size: pageSize }),
  })

  const columns: TableColumnType<LoginRecord>[] = [
    {
      title: t('profile.loginRecord.colStatus'),
      dataIndex: 'status',
      width: 80,
      render: (s: LoginRecord['status'], record: LoginRecord) =>
        s === 'SUCCESS' ? (
          <Tag color="success" icon={<CheckCircleOutlined />}>
            {t('profile.loginRecord.statusSuccess')}
          </Tag>
        ) : (
          <Tooltip title={record.failReason || t('profile.loginRecord.defaultFailReason')}>
            <Tag color="error" icon={<CloseCircleOutlined />}>
              {t('profile.loginRecord.statusFailed')}
            </Tag>
          </Tooltip>
        ),
    },
    {
      title: t('profile.loginRecord.colIp'),
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
      title: t('profile.loginRecord.colDevice'),
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
      title: t('profile.loginRecord.colLocation'),
      dataIndex: 'location',
      width: 120,
      render: (loc?: string) =>
        loc ?? <span style={{ color: 'var(--kf-muted-foreground)' }}>—</span>,
    },
    {
      title: t('profile.loginRecord.colTime'),
      dataIndex: 'loginTime',
      width: 160,
      render: (v?: string) => (v ? new Date(v).toLocaleString() : '—'),
    },
  ]

  return (
    <div className={styles.section}>
      <h3 className={styles.sectionTitle}>{t('profile.loginRecord.title')}</h3>
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
