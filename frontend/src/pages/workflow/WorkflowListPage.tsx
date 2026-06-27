import { useState } from 'react'
import { Button, Input, Tag, Modal, Form, App, Empty, Tooltip } from 'antd'
import {
  PlusOutlined,
  SearchOutlined,
  PlayCircleOutlined,
  EditOutlined,
  DeleteOutlined,
  ApiOutlined,
  CheckCircleOutlined,
  StopOutlined,
} from '@ant-design/icons'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { motion } from 'framer-motion'
import { workflowApi } from '@/api/workflow'
import type { WorkflowSummary } from '@/types/workflow'
import { GradientCard } from '@/components/base'
import { PermissionButton } from '@/components/business'
import styles from './WorkflowListPage.module.css'

const STATUS_CFG = {
  draft: { color: 'default', label: '草稿', icon: <EditOutlined /> },
  published: { color: 'success', label: '已发布', icon: <CheckCircleOutlined /> },
  disabled: { color: 'error', label: '已停用', icon: <StopOutlined /> },
}

export default function WorkflowListPage() {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const { message, modal } = App.useApp()
  const [keyword, setKeyword] = useState('')
  const [createOpen, setCreateOpen] = useState(false)
  const [form] = Form.useForm<{ name: string; description?: string }>()
  const [current, setCurrent] = useState(1)

  const { data, isLoading } = useQuery({
    queryKey: ['workflows', current, keyword],
    queryFn: () => workflowApi.list({ current, size: 12, keyword: keyword || undefined }),
  })

  const createMutation = useMutation({
    mutationFn: (v: { name: string; description?: string }) => workflowApi.create(v),
    onSuccess: (wf: WorkflowSummary) => {
      qc.invalidateQueries({ queryKey: ['workflows'] })
      setCreateOpen(false)
      form.resetFields()
      navigate(`/workflows/${wf.id}/edit`)
    },
  })

  const publishMutation = useMutation({
    mutationFn: (id: number) => workflowApi.publish(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['workflows'] })
      message.success('已发布')
    },
  })

  const disableMutation = useMutation({
    mutationFn: (id: number) => workflowApi.disable(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['workflows'] })
      message.success('已停用')
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => workflowApi.delete(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['workflows'] })
      message.success('已删除')
    },
  })

  const handleDelete = (wf: WorkflowSummary) => {
    modal.confirm({
      title: `删除工作流「${wf.name}」？`,
      content: '此操作不可撤销',
      okType: 'danger',
      onOk: () => deleteMutation.mutateAsync(wf.id),
    })
  }

  return (
    <div className={styles.root}>
      <div className={styles.topBar}>
        <h2 className={styles.pageTitle}>
          <ApiOutlined /> 工作流
        </h2>
        <div className={styles.actions}>
          <Input
            prefix={<SearchOutlined />}
            placeholder="搜索工作流…"
            value={keyword}
            onChange={(e) => {
              setKeyword(e.target.value)
              setCurrent(1)
            }}
            style={{ width: 220 }}
            allowClear
          />
          <PermissionButton permission="workflow:create">
            <Button
              type="primary"
              icon={<PlusOutlined />}
              style={{ background: 'var(--kf-accent-gradient-r)', border: 'none' }}
              onClick={() => setCreateOpen(true)}
            >
              新建工作流
            </Button>
          </PermissionButton>
        </div>
      </div>

      {isLoading ? (
        <div className={styles.skeletonGrid}>
          {Array.from({ length: 6 }).map((_, i) => (
            <div key={i} className={styles.skeleton} />
          ))}
        </div>
      ) : !data?.records.length ? (
        <Empty description="暂无工作流" />
      ) : (
        <div className={styles.grid}>
          {data.records.map((wf: WorkflowSummary, i: number) => (
            <motion.div
              key={wf.id}
              initial={{ opacity: 0, y: 16 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: i * 0.04 }}
            >
              <WorkflowCard
                wf={wf}
                onEdit={() => navigate(`/workflows/${wf.id}/edit`)}
                onPublish={() => publishMutation.mutate(wf.id)}
                onDisable={() => disableMutation.mutate(wf.id)}
                onDelete={() => handleDelete(wf)}
              />
            </motion.div>
          ))}
        </div>
      )}

      {data && data.total > 12 && (
        <div className={styles.pagination}>
          {Array.from({ length: Math.ceil(data.total / 12) }).map((_, i) => (
            <button
              key={i}
              className={`${styles.pageBtn} ${current === i + 1 ? styles.pageBtnActive : ''}`}
              onClick={() => setCurrent(i + 1)}
            >
              {i + 1}
            </button>
          ))}
        </div>
      )}

      <Modal
        title="新建工作流"
        open={createOpen}
        onCancel={() => {
          setCreateOpen(false)
          form.resetFields()
        }}
        onOk={() => form.submit()}
        confirmLoading={createMutation.isPending}
        destroyOnClose
      >
        <Form form={form} layout="vertical" onFinish={(v) => createMutation.mutate(v)}>
          <Form.Item
            name="name"
            label="名称"
            rules={[{ required: true, message: '请输入工作流名称' }]}
          >
            <Input placeholder="例：客服问答流程" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={3} placeholder="简要描述此工作流用途" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}

interface WorkflowCardProps {
  wf: WorkflowSummary
  onEdit: () => void
  onPublish: () => void
  onDisable: () => void
  onDelete: () => void
}

function WorkflowCard({ wf, onEdit, onPublish, onDisable, onDelete }: WorkflowCardProps) {
  const isPublished = wf.status === 'published'
  const cfg = STATUS_CFG[wf.status]

  return (
    <GradientCard featured={isPublished} className={styles.card}>
      <div className={styles.cardHeader}>
        <span className={styles.cardName}>{wf.name}</span>
        <Tag color={cfg.color} icon={cfg.icon}>
          {cfg.label}
        </Tag>
      </div>
      {wf.description && <p className={styles.cardDesc}>{wf.description}</p>}
      <div className={styles.cardMeta}>
        <span>
          <PlayCircleOutlined /> {wf.runCount} 次运行
        </span>
        <span>{new Date(wf.updateTime).toLocaleDateString()}</span>
      </div>
      <div className={styles.cardActions}>
        <Button size="small" icon={<EditOutlined />} onClick={onEdit}>
          编辑
        </Button>
        {wf.status !== 'published' ? (
          <PermissionButton permission="workflow:publish">
            <Button
              size="small"
              type="primary"
              onClick={onPublish}
              style={{ background: 'var(--kf-accent-gradient-r)', border: 'none' }}
            >
              发布
            </Button>
          </PermissionButton>
        ) : (
          <PermissionButton permission="workflow:publish">
            <Button size="small" danger onClick={onDisable}>
              停用
            </Button>
          </PermissionButton>
        )}
        <Tooltip title="删除">
          <PermissionButton permission="workflow:delete">
            <Button size="small" danger icon={<DeleteOutlined />} onClick={onDelete} />
          </PermissionButton>
        </Tooltip>
      </div>
    </GradientCard>
  )
}
