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
import { useTranslation } from 'react-i18next'
import { workflowApi } from '@/api/workflow'
import type { WorkflowSummary } from '@/types/workflow'
import { GradientCard } from '@/components/base'
import { PermissionButton } from '@/components/business'
import styles from './WorkflowListPage.module.css'

export default function WorkflowListPage() {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const { message, modal } = App.useApp()
  const { t } = useTranslation()
  const [keyword, setKeyword] = useState('')
  const [createOpen, setCreateOpen] = useState(false)
  const [form] = Form.useForm<{ name: string; description?: string }>()
  const [current, setCurrent] = useState(1)

  const STATUS_CFG = {
    draft: { color: 'default', label: t('workflow.statusDraft'), icon: <EditOutlined /> },
    published: {
      color: 'success',
      label: t('workflow.statusPublished'),
      icon: <CheckCircleOutlined />,
    },
    disabled: { color: 'error', label: t('workflow.statusDisabled'), icon: <StopOutlined /> },
  }

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
      message.success(t('workflow.publishSuccess'))
    },
  })

  const disableMutation = useMutation({
    mutationFn: (id: number) => workflowApi.disable(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['workflows'] })
      message.success(t('workflow.disableSuccess'))
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => workflowApi.delete(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['workflows'] })
      message.success(t('workflow.deleteSuccess'))
    },
  })

  const handleDelete = (wf: WorkflowSummary) => {
    modal.confirm({
      title: t('workflow.deleteConfirm', { name: wf.name }),
      content: t('workflow.deleteContent'),
      okType: 'danger',
      onOk: () => deleteMutation.mutateAsync(wf.id),
    })
  }

  return (
    <div className={styles.root}>
      <div className={styles.topBar}>
        <h2 className={styles.pageTitle}>
          <ApiOutlined /> {t('workflow.title')}
        </h2>
        <div className={styles.actions}>
          <Input
            prefix={<SearchOutlined />}
            placeholder={t('workflow.searchPlaceholder')}
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
              {t('workflow.createBtn')}
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
        <Empty description={t('workflow.empty')} />
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
                statusCfg={STATUS_CFG}
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
        title={t('workflow.createModalTitle')}
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
            label={t('workflow.fieldName')}
            rules={[{ required: true, message: t('workflow.nameRequired') }]}
          >
            <Input placeholder={t('workflow.namePlaceholder')} />
          </Form.Item>
          <Form.Item name="description" label={t('workflow.fieldDesc')}>
            <Input.TextArea rows={3} placeholder={t('workflow.descPlaceholder')} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}

type StatusCfg = Record<string, { color: string; label: string; icon: React.ReactNode }>

interface WorkflowCardProps {
  wf: WorkflowSummary
  statusCfg: StatusCfg
  onEdit: () => void
  onPublish: () => void
  onDisable: () => void
  onDelete: () => void
}

function WorkflowCard({
  wf,
  statusCfg,
  onEdit,
  onPublish,
  onDisable,
  onDelete,
}: WorkflowCardProps) {
  const { t } = useTranslation()
  const isPublished = wf.status === 'published'
  const cfg = statusCfg[wf.status]

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
          <PlayCircleOutlined /> {t('workflow.runCount', { count: wf.runCount })}
        </span>
        <span>{new Date(wf.updateTime).toLocaleDateString()}</span>
      </div>
      <div className={styles.cardActions}>
        <Button size="small" icon={<EditOutlined />} onClick={onEdit}>
          {t('common.edit')}
        </Button>
        {wf.status !== 'published' ? (
          <PermissionButton permission="workflow:publish">
            <Button
              size="small"
              type="primary"
              onClick={onPublish}
              style={{ background: 'var(--kf-accent-gradient-r)', border: 'none' }}
            >
              {t('common.publish')}
            </Button>
          </PermissionButton>
        ) : (
          <PermissionButton permission="workflow:publish">
            <Button size="small" danger onClick={onDisable}>
              {t('common.disable')}
            </Button>
          </PermissionButton>
        )}
        <Tooltip title={t('common.delete')}>
          <PermissionButton permission="workflow:delete">
            <Button size="small" danger icon={<DeleteOutlined />} onClick={onDelete} />
          </PermissionButton>
        </Tooltip>
      </div>
    </GradientCard>
  )
}
