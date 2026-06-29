import { useState } from 'react'
import { Button, Input, Tag, Modal, Form, App, Row, Col, Skeleton, Tooltip, Popconfirm } from 'antd'
import {
  PlusOutlined,
  SearchOutlined,
  PlayCircleOutlined,
  EditOutlined,
  DeleteOutlined,
  ApiOutlined,
  CheckCircleOutlined,
  StopOutlined,
  HistoryOutlined,
} from '@ant-design/icons'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { motion } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import { workflowApi } from '@/api/workflow'
import type { WorkflowSummary } from '@/types/workflow'
import { GradientCard, GradientButton } from '@/components/base'
import { EmptyState, PermissionButton, PageBar } from '@/components/business'
import styles from './WorkflowListPage.module.css'

type WorkflowStatus = 'draft' | 'published' | 'disabled'
type StatusCfg = Record<WorkflowStatus, { color: string; label: string; icon: React.ReactNode }>

interface WorkflowCardProps {
  wf: WorkflowSummary
  statusCfg: StatusCfg
  onEdit: () => void
  onPublish: () => void
  onDisable: () => void
  onDelete: () => void
  onHistory: () => void
}

function WorkflowCard({
  wf,
  statusCfg,
  onEdit,
  onPublish,
  onDisable,
  onDelete,
  onHistory,
}: WorkflowCardProps) {
  const { t } = useTranslation()
  const cfg = statusCfg[wf.status as WorkflowStatus] ?? {
    color: 'default' as const,
    label: wf.status,
    icon: null,
  }

  return (
    <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }}>
      <GradientCard className={styles.card}>
        <div className={styles.cardHeader}>
          <div className={styles.cardIconWrap}>
            <ApiOutlined />
          </div>
          <Tag color={cfg.color} icon={cfg.icon} style={{ fontSize: 12 }}>
            {cfg.label}
          </Tag>
        </div>

        <h4 className={styles.cardName}>{wf.name}</h4>
        {wf.description && <p className={styles.cardDesc}>{wf.description}</p>}

        <div className={styles.cardMeta}>
          {(wf.callCount ?? 0) > 0 && (
            <span className={styles.metaItem}>
              <PlayCircleOutlined /> {t('workflow.runCount', { count: wf.callCount })}
            </span>
          )}
          {wf.updatedAt && !Number.isNaN(new Date(wf.updatedAt).getTime()) && (
            <span className={styles.metaItem}>{new Date(wf.updatedAt).toLocaleDateString()}</span>
          )}
        </div>

        <div className={styles.cardActions} onClick={(e) => e.stopPropagation()}>
          <Tooltip title={t('workflow.executions.historyBtn')}>
            <Button
              size="small"
              icon={<HistoryOutlined />}
              className={styles.btnBlue}
              onClick={onHistory}
            />
          </Tooltip>
          <Tooltip title={t('common.edit')}>
            <Button
              size="small"
              icon={<EditOutlined />}
              className={styles.btnGray}
              onClick={onEdit}
            />
          </Tooltip>
          {wf.status !== 'published' ? (
            <PermissionButton permission="workflow:publish">
              <Tooltip title={t('common.publish')}>
                <Button
                  size="small"
                  icon={<CheckCircleOutlined />}
                  className={styles.btnGreen}
                  onClick={onPublish}
                />
              </Tooltip>
            </PermissionButton>
          ) : (
            <PermissionButton permission="workflow:publish">
              <Tooltip title={t('common.disable')}>
                <Button
                  size="small"
                  icon={<StopOutlined />}
                  className={styles.btnOrange}
                  onClick={onDisable}
                />
              </Tooltip>
            </PermissionButton>
          )}
          <PermissionButton permission="workflow:delete">
            <Tooltip title={t('common.delete')}>
              <Popconfirm
                title={t('workflow.deleteConfirm', { name: wf.name })}
                description={t('workflow.deleteContent')}
                onConfirm={onDelete}
                okText={t('common.delete')}
                okButtonProps={{ danger: true }}
                cancelText={t('common.cancel')}
              >
                <Button size="small" icon={<DeleteOutlined />} className={styles.btnRed} />
              </Popconfirm>
            </Tooltip>
          </PermissionButton>
        </div>
      </GradientCard>
    </motion.div>
  )
}

export default function WorkflowListPage() {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const { message } = App.useApp()
  const { t } = useTranslation()
  const [keyword, setKeyword] = useState('')
  const [createOpen, setCreateOpen] = useState(false)
  const [form] = Form.useForm<{ name: string; description?: string }>()
  const [current, setCurrent] = useState(1)
  const [pageSize, setPageSize] = useState(20)

  const STATUS_CFG: StatusCfg = {
    draft: { color: 'default', label: t('workflow.statusDraft'), icon: <EditOutlined /> },
    published: {
      color: 'success',
      label: t('workflow.statusPublished'),
      icon: <CheckCircleOutlined />,
    },
    disabled: { color: 'error', label: t('workflow.statusDisabled'), icon: <StopOutlined /> },
  }

  const { data, isLoading } = useQuery({
    queryKey: ['workflows', current, pageSize, keyword],
    queryFn: () => workflowApi.list({ current, size: pageSize, keyword: keyword || undefined }),
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

  return (
    <div className={styles.root}>
      <div className={styles.pageHeader}>
        <div className={styles.toolbar}>
          <div className={styles.filters}>
            <Input
              prefix={<SearchOutlined />}
              placeholder={t('workflow.searchPlaceholder')}
              value={keyword}
              onChange={(e) => {
                setKeyword(e.target.value)
                setCurrent(1)
              }}
              allowClear
              style={{ width: 240 }}
            />
          </div>
          <PermissionButton permission="workflow:create">
            <GradientButton icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
              {t('workflow.createBtn')}
            </GradientButton>
          </PermissionButton>
        </div>
      </div>

      <div className={styles.body}>
        {isLoading ? (
          <Row gutter={[16, 16]}>
            {[1, 2, 3].map((i) => (
              <Col key={i} xs={24} sm={12} lg={8}>
                <Skeleton active />
              </Col>
            ))}
          </Row>
        ) : !data?.records.length ? (
          <EmptyState
            title={t('workflow.empty')}
            action={
              <PermissionButton permission="workflow:create">
                <GradientButton icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
                  {t('workflow.createBtn')}
                </GradientButton>
              </PermissionButton>
            }
          />
        ) : (
          <Row gutter={[16, 16]}>
            {data.records.map((wf: WorkflowSummary) => (
              <Col key={wf.id} xs={24} sm={12} lg={8}>
                <WorkflowCard
                  wf={wf}
                  statusCfg={STATUS_CFG}
                  onEdit={() => navigate(`/workflows/${wf.id}/edit`)}
                  onPublish={() => publishMutation.mutate(wf.id)}
                  onDisable={() => disableMutation.mutate(wf.id)}
                  onDelete={() => deleteMutation.mutate(wf.id)}
                  onHistory={() => navigate(`/workflows/${wf.id}/executions`)}
                />
              </Col>
            ))}
          </Row>
        )}
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
