import { useState } from 'react'
import { Tag, Collapse, Button, App } from 'antd'
import { ArrowLeftOutlined, ThunderboltOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { useParams, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { agentApi } from '@/api/agent'
import type { AgentExecution } from '@/types/agent'
import PageTable, { type TableColumnType } from '@/components/business/PageTable'
import { PageBar } from '@/components/business'
import styles from './AgentExecutionPage.module.css'

function formatMs(ms: number) {
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(2)}s`
}

/** Render a backend IO JSON string readably. Agent input is `{"query":"..."}`,
 *  output is `{"answer":"..."}`; extract the inner text. Other JSON is
 *  pretty-printed; non-JSON falls back to raw. */
function displayIo(v?: string | null): string {
  if (!v) return ''
  try {
    const parsed = JSON.parse(v)
    if (parsed != null && typeof parsed === 'object') {
      const obj = parsed as Record<string, unknown>
      for (const key of ['query', 'answer', 'input', 'output', 'result']) {
        if (key in obj && typeof obj[key] === 'string') return obj[key] as string
      }
      return JSON.stringify(parsed, null, 2)
    }
    return typeof parsed === 'string' ? parsed : JSON.stringify(parsed, null, 2)
  } catch {
    return v
  }
}

/** Safe date formatter — never returns "Invalid Date". */
function formatDate(v?: string | null): string {
  if (!v) return '—'
  const d = new Date(v)
  return Number.isNaN(d.getTime()) ? '—' : d.toLocaleString()
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
      query.state.data?.records?.some((e) => e.status === 'running') ? 3000 : false,
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
      dataIndex: 'inputJson',
      ellipsis: true,
      render: (v: string) => {
        const text = displayIo(v)
        return text ? text.slice(0, 80) : '—'
      },
    },
    {
      title: 'Token',
      dataIndex: 'totalTokens',
      width: 80,
      render: (n: number) => (n != null ? n.toLocaleString() : '—'),
    },
    {
      title: t('agent.executions.colDuration'),
      dataIndex: 'durationMs',
      width: 80,
      render: (ms: number) => (ms != null ? formatMs(ms) : '—'),
    },
    {
      title: t('agent.executions.colTime'),
      dataIndex: 'startedAt',
      width: 160,
      render: (v: string) => formatDate(v),
    },
  ]

  const handleCopy = (text: string) => {
    navigator.clipboard
      .writeText(text)
      .then(() => message.success(t('agent.executions.copySuccess')))
  }

  return (
    <div className={styles.root}>
      <div className={styles.pageHeader}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/agents')}>
          {t('agent.executions.backBtn')}
        </Button>
        <h2 className={styles.title}>
          <ThunderboltOutlined /> {t('agent.executions.title')}
        </h2>
      </div>

      <div className={styles.body}>
        <PageTable<AgentExecution>
          rowKey="id"
          columns={columns}
          dataSource={data?.records}
          loading={isLoading}
          expandable={{
            expandedRowRender: (row) => {
              const inputText = displayIo(row.inputJson)
              const outputText = displayIo(row.outputJson)
              return (
                <Collapse
                  size="small"
                  items={[
                    {
                      key: 'input',
                      label: t('agent.executions.expandInput'),
                      children: (
                        <div className={styles.codeBlock}>
                          <pre>{inputText || '—'}</pre>
                          {inputText && (
                            <Button size="small" onClick={() => handleCopy(inputText)}>
                              {t('common.copy')}
                            </Button>
                          )}
                        </div>
                      ),
                    },
                    ...(outputText
                      ? [
                          {
                            key: 'output',
                            label: t('agent.executions.expandOutput'),
                            children: (
                              <div className={styles.codeBlock}>
                                <pre>{outputText}</pre>
                                <Button size="small" onClick={() => handleCopy(outputText)}>
                                  {t('common.copy')}
                                </Button>
                              </div>
                            ),
                          },
                        ]
                      : []),
                  ]}
                />
              )
            },
          }}
        />
      </div>
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
