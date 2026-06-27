import { useState } from 'react'
import { Tag, Collapse, Button, App } from 'antd'
import { ArrowLeftOutlined, ThunderboltOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { useParams, useNavigate } from 'react-router-dom'
import { agentApi } from '@/api/agent'
import type { AgentExecution } from '@/types/agent'
import PageTable, { type TableColumnType } from '@/components/business/PageTable'
import styles from './AgentExecutionPage.module.css'

const STATUS_CFG = {
  running: { color: 'processing', label: '运行中' },
  success: { color: 'success', label: '成功' },
  failed: { color: 'error', label: '失败' },
}

function formatMs(ms: number) {
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(2)}s`
}

export default function AgentExecutionPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { message } = App.useApp()
  const [current, setCurrent] = useState(1)
  const [pageSize, setPageSize] = useState(20)

  const { data, isLoading } = useQuery({
    queryKey: ['agent-executions', id, current, pageSize],
    queryFn: () => agentApi.listExecutions(Number(id), { current, size: pageSize }),
    enabled: !!id,
    refetchInterval: (query) =>
      query.state.data?.records.some((e) => e.status === 'running') ? 3000 : false,
  })

  const columns: TableColumnType<AgentExecution>[] = [
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (s: AgentExecution['status']) => (
        <Tag color={STATUS_CFG[s].color}>{STATUS_CFG[s].label}</Tag>
      ),
    },
    {
      title: '输入摘要',
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
      title: '耗时',
      dataIndex: 'durationMs',
      width: 80,
      render: (ms: number) => formatMs(ms),
    },
    {
      title: '时间',
      dataIndex: 'createTime',
      width: 160,
      render: (t: string) => new Date(t).toLocaleString(),
    },
  ]

  const handleCopy = (text: string) => {
    navigator.clipboard.writeText(text).then(() => message.success('已复制'))
  }

  return (
    <div className={styles.root}>
      <div className={styles.header}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/agents')}>
          返回列表
        </Button>
        <h2 className={styles.title}>
          <ThunderboltOutlined /> 执行记录
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
                  label: '完整输入',
                  children: (
                    <div className={styles.codeBlock}>
                      <pre>{row.input}</pre>
                      <Button size="small" onClick={() => handleCopy(row.input)}>
                        复制
                      </Button>
                    </div>
                  ),
                },
                ...(row.output
                  ? [
                      {
                        key: 'output',
                        label: '完整输出',
                        children: (
                          <div className={styles.codeBlock}>
                            <pre>{row.output}</pre>
                            <Button size="small" onClick={() => handleCopy(row.output!)}>
                              复制
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
