import { useState } from 'react'
import { Tag, Collapse, Button, App } from 'antd'
import { ArrowLeftOutlined, ThunderboltOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { useParams, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { agentApi } from '@/api/agent'
import type { AgentExecution } from '@/types/agent'
import PageTable, { type TableColumnType } from '@/components/business/PageTable'
import styles from './AgentExecutionPage.module.css'

function formatMs(ms: number) {
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(2)}s`
}

export default function AgentExecutionPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { message } = App.useApp()
  const { t } = useTranslation()
  const [current, setCurrent] = useState(1)
  const [pageSize, setPageSize] = useState(20)

  const STATUS_CFG = {
    running: { color: 'processing', label: t('agent.executions.statusRunning') },
    success: { color: 'success', label: t('agent.executions.statusSuccess') },
    failed: { color: 'error', label: t('agent.executions.statusFailed') },
  }

  const { data, isLoading } = useQuery({
    queryKey: ['agent-executions', id, current, pageSize],
    queryFn: () => agentApi.listExecutions(Number(id), { current, size: pageSize }),
    enabled: !!id,
    refetchInterval: (query) =>
      query.state.data?.records.some((e) => e.status === 'running') ? 3000 : false,
  })

  const columns: TableColumnType<AgentExecution>[] = [
    {
      title: t('agent.executions.colStatus'),
      dataIndex: 'status',
      width: 90,
      render: (s: AgentExecution['status']) => (
        <Tag color={STATUS_CFG[s].color}>{STATUS_CFG[s].label}</Tag>
      ),
    },
    {
      title: t('agent.executions.colInput'),
      dataIndex: 'input',
      ellipsis: true,
      render: (v: string) => v.slice(0, 80),
    },
    {
      title: 'Token',
      dataIndex: 'tokens',
      width: 80,
      render: (n: number) => n.toLocaleString(),
    },
    {
      title: t('agent.executions.colDuration'),
      dataIndex: 'durationMs',
      width: 80,
      render: (ms: number) => formatMs(ms),
    },
    {
      title: t('agent.executions.colTime'),
      dataIndex: 'createTime',
      width: 160,
      render: (v: string) => new Date(v).toLocaleString(),
    },
  ]

  const handleCopy = (text: string) => {
    navigator.clipboard
      .writeText(text)
      .then(() => message.success(t('agent.executions.copySuccess')))
  }

  return (
    <div className={styles.root}>
      <div className={styles.header}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/agents')}>
          {t('agent.executions.backBtn')}
        </Button>
        <h2 className={styles.title}>
          <ThunderboltOutlined /> {t('agent.executions.title')}
        </h2>
      </div>

      <PageTable<AgentExecution>
        rowKey="id"
        columns={columns}
        dataSource={data?.records}
        loading={isLoading}
        total={data?.total}
        current={current}
        pageSize={pageSize}
        onPageChange={(p, s) => {
          setCurrent(p)
          setPageSize(s)
        }}
        expandable={{
          expandedRowRender: (row) => (
            <Collapse
              size="small"
              items={[
                {
                  key: 'input',
                  label: t('agent.executions.expandInput'),
                  children: (
                    <div className={styles.codeBlock}>
                      <pre>{row.input}</pre>
                      <Button size="small" onClick={() => handleCopy(row.input)}>
                        {t('common.copy')}
                      </Button>
                    </div>
                  ),
                },
                ...(row.output
                  ? [
                      {
                        key: 'output',
                        label: t('agent.executions.expandOutput'),
                        children: (
                          <div className={styles.codeBlock}>
                            <pre>{row.output}</pre>
                            <Button size="small" onClick={() => handleCopy(row.output!)}>
                              {t('common.copy')}
                            </Button>
                          </div>
                        ),
                      },
                    ]
                  : []),
              ]}
            />
          ),
        }}
      />
    </div>
  )
}
