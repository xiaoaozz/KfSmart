import { useState } from 'react'
import {
  Button,
  Tag,
  Tooltip,
  Popconfirm,
  Modal,
  Form,
  Input,
  Switch,
  App,
  Skeleton,
  Table,
  Badge,
  Space,
} from 'antd'
import {
  ArrowLeftOutlined,
  UploadOutlined,
  EditOutlined,
  DeleteOutlined,
  ReloadOutlined,
  FileTextOutlined,
  FilePdfOutlined,
  FileWordOutlined,
  FileExcelOutlined,
  FilePptOutlined,
  FileMarkdownOutlined,
  DatabaseOutlined,
  FileOutlined,
  InboxOutlined,
  LockOutlined,
} from '@ant-design/icons'
import { useParams, useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { kbApi } from '@/api/knowledge-base'
import { docApi } from '@/api/document'
import type { KbFormValues, KbDocument } from '@/types/knowledge-base'
import { GradientButton } from '@/components/base'
import { PermissionButton, EmptyState, ErrorRetry } from '@/components/business'
import { usePermission } from '@/hooks/usePermission'
import ChunkedUploader from '@/components/business/ChunkedUploader'
import styles from './KbDetailPage.module.css'

// ─── helpers ────────────────────────────────────────────────────────────────

function formatBytes(n: number) {
  if (n < 1024) return `${n} B`
  if (n < 1024 ** 2) return `${(n / 1024).toFixed(1)} KB`
  return `${(n / 1024 ** 2).toFixed(1)} MB`
}

function FileIcon({ name }: { name: string }) {
  const ext = name.split('.').pop()?.toLowerCase() ?? ''
  if (ext === 'pdf') return <FilePdfOutlined style={{ color: '#ff4d4f', fontSize: 16 }} />
  if (ext === 'doc' || ext === 'docx')
    return <FileWordOutlined style={{ color: '#1677ff', fontSize: 16 }} />
  if (ext === 'xls' || ext === 'xlsx' || ext === 'csv')
    return <FileExcelOutlined style={{ color: '#52c41a', fontSize: 16 }} />
  if (ext === 'ppt' || ext === 'pptx')
    return <FilePptOutlined style={{ color: '#fa8c16', fontSize: 16 }} />
  if (ext === 'md') return <FileMarkdownOutlined style={{ color: '#722ed1', fontSize: 16 }} />
  if (ext === 'txt')
    return <FileTextOutlined style={{ color: 'var(--kf-muted-foreground)', fontSize: 16 }} />
  return <FileOutlined style={{ color: 'var(--kf-muted-foreground)', fontSize: 16 }} />
}

// ─── component ───────────────────────────────────────────────────────────────

export default function KbDetailPage() {
  const { kbId } = useParams<{ kbId: string }>()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const { message } = App.useApp()
  const { t } = useTranslation()

  const { user, isAdmin } = usePermission()
  const [editOpen, setEditOpen] = useState(false)
  const [uploadOpen, setUploadOpen] = useState(false)
  const [form] = Form.useForm<KbFormValues>()

  const canManageDoc = (doc: KbDocument) =>
    isAdmin || (user != null && String(user.id) === doc.userId)

  // ── queries ──
  const {
    data: kb,
    isLoading: kbLoading,
    isError: kbError,
    refetch: refetchKb,
  } = useQuery({
    queryKey: ['kb-detail', kbId],
    queryFn: () => kbApi.get(kbId!),
    enabled: !!kbId,
  })

  const {
    data: docs,
    isLoading: docsLoading,
    isError: docsError,
    refetch: refetchDocs,
  } = useQuery({
    queryKey: ['kb-documents', kbId],
    queryFn: () => kbApi.getDocuments(kbId!),
    enabled: !!kbId,
    refetchInterval: (query) => (query.state.data?.some((d) => d.status === 0) ? 3000 : false),
  })

  // ── mutations ──
  const updateMutation = useMutation({
    mutationFn: (data: Partial<KbFormValues>) => kbApi.update(kbId!, data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['kb-detail', kbId] })
      qc.invalidateQueries({ queryKey: ['knowledge-bases'] })
      message.success(t('kb.detail.updateSuccess'))
      setEditOpen(false)
    },
    onError: () => message.error(t('kb.detail.updateError')),
  })

  const deleteMutation = useMutation({
    mutationFn: () => kbApi.delete(kbId!),
    onSuccess: () => {
      message.success(t('kb.detail.deleteKbSuccess'))
      navigate('/knowledge-bases')
    },
    onError: () => message.error(t('kb.detail.deleteKbError')),
  })

  const deleteDocMutation = useMutation({
    mutationFn: (fileMd5: string) => docApi.delete(fileMd5),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['kb-documents', kbId] })
      qc.invalidateQueries({ queryKey: ['kb-detail', kbId] })
      message.success(t('kb.detail.deleteDocSuccess'))
    },
    onError: () => message.error(t('kb.detail.deleteDocError')),
  })

  const reparseMutation = useMutation({
    mutationFn: (fileMd5: string) => docApi.reparse(fileMd5),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['kb-documents', kbId] })
      message.success(t('kb.detail.reparseSuccess'))
    },
    onError: () => message.error(t('kb.detail.reparseError')),
  })

  // ── event handlers ──
  const handleOpenEdit = () => {
    if (!kb) return
    form.setFieldsValue({
      name: kb.name,
      description: kb.description,
      orgTag: kb.orgTag,
      isPublic: kb.isPublic,
    })
    setEditOpen(true)
  }

  const handleUploadSuccess = () => {
    qc.invalidateQueries({ queryKey: ['kb-documents', kbId] })
    qc.invalidateQueries({ queryKey: ['kb-detail', kbId] })
  }

  // ── document table columns ──
  const columns = [
    {
      title: t('kb.detail.colFileName'),
      dataIndex: 'fileName',
      key: 'fileName',
      render: (name: string) => (
        <Space>
          <FileIcon name={name} />
          <span style={{ fontSize: 13, color: 'var(--kf-foreground)' }}>{name}</span>
        </Space>
      ),
    },
    {
      title: t('kb.detail.colSize'),
      dataIndex: 'totalSize',
      key: 'totalSize',
      width: 100,
      render: (size: number) => (
        <span style={{ fontSize: 12, color: 'var(--kf-muted-foreground)' }}>
          {formatBytes(size)}
        </span>
      ),
    },
    {
      title: t('kb.detail.colStatus'),
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status: number) =>
        status === 1 ? (
          <Badge status="success" text={t('kb.detail.statusDone')} />
        ) : (
          <Badge status="processing" text={t('kb.detail.statusProcessing')} />
        ),
    },
    {
      title: t('kb.detail.colUploadTime'),
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 140,
      render: (val: string) => (
        <span style={{ fontSize: 12, color: 'var(--kf-muted-foreground)' }}>
          {val ? new Date(val).toLocaleString() : '—'}
        </span>
      ),
    },
    {
      title: t('kb.detail.colActions'),
      key: 'actions',
      width: 110,
      render: (_: unknown, doc: KbDocument) => {
        if (!canManageDoc(doc)) {
          return (
            <Tooltip title={t('kb.detail.noPermissionTip')}>
              <span className={styles.noPermission}>
                <LockOutlined style={{ marginRight: 4 }} />
                {t('kb.detail.noPermission')}
              </span>
            </Tooltip>
          )
        }
        return (
          <Space size={4}>
            <Tooltip title={t('kb.detail.reparseTooltip')}>
              <Button
                type="text"
                size="small"
                icon={<ReloadOutlined />}
                loading={reparseMutation.isPending}
                onClick={() => reparseMutation.mutate(doc.fileMd5)}
              />
            </Tooltip>
            <Popconfirm
              title={t('kb.detail.deleteDocConfirm')}
              description={t('kb.detail.deleteDocDesc')}
              onConfirm={() => deleteDocMutation.mutate(doc.fileMd5)}
              okText={t('common.delete')}
              okButtonProps={{ danger: true }}
              cancelText={t('common.cancel')}
            >
              <Tooltip title={t('common.delete')}>
                <Button
                  type="text"
                  size="small"
                  danger
                  icon={<DeleteOutlined />}
                  loading={deleteDocMutation.isPending}
                />
              </Tooltip>
            </Popconfirm>
          </Space>
        )
      },
    },
  ]

  // ── loading / error states ──
  if (kbLoading) {
    return (
      <div className={styles.root}>
        <Skeleton active paragraph={{ rows: 4 }} />
      </div>
    )
  }

  if (kbError || !kb) {
    return (
      <div className={styles.root}>
        <ErrorRetry onRetry={refetchKb} />
      </div>
    )
  }

  return (
    <div className={styles.root}>
      {/* Breadcrumb */}
      <div className={styles.breadcrumb}>
        <Button
          type="text"
          icon={<ArrowLeftOutlined />}
          onClick={() => navigate('/knowledge-bases')}
          style={{ color: 'var(--kf-muted-foreground)', paddingLeft: 0 }}
        >
          {t('kb.detail.backBtn')}
        </Button>
      </div>

      {/* KB Header */}
      <div className={styles.header}>
        <div className={styles.headerLeft}>
          <div className={styles.kbIcon}>
            <DatabaseOutlined />
          </div>
          <div className={styles.headerInfo}>
            <div className={styles.headerTitle}>
              <h1 className={styles.kbName}>{kb.name}</h1>
              <Tag color={kb.isPublic ? 'green' : 'default'} style={{ marginLeft: 8 }}>
                {kb.isPublic ? t('common.public') : t('common.private')}
              </Tag>
              {kb.orgTag && (
                <Tag color="blue" style={{ marginLeft: 4 }}>
                  {kb.orgTag}
                </Tag>
              )}
            </div>
            {kb.description && <p className={styles.kbDesc}>{kb.description}</p>}
            <div className={styles.kbMeta}>
              <span>{t('common.createdBy', { name: kb.createdBy })}</span>
              <span>·</span>
              <span>
                {t('kb.detail.updatedAt', {
                  date: kb.updatedAt ? new Date(kb.updatedAt).toLocaleDateString() : '—',
                })}
              </span>
            </div>
          </div>
        </div>

        <div className={styles.headerActions}>
          <PermissionButton permission="kb:write" mode="hide">
            <Button icon={<EditOutlined />} onClick={handleOpenEdit}>
              {t('common.edit')}
            </Button>
          </PermissionButton>
          <PermissionButton permission="kb:delete" mode="hide">
            <Popconfirm
              title={t('kb.detail.deleteKbConfirm')}
              description={t('kb.detail.deleteKbDesc')}
              onConfirm={() => deleteMutation.mutate()}
              okText={t('common.delete')}
              okButtonProps={{ danger: true }}
              cancelText={t('common.cancel')}
            >
              <Button danger icon={<DeleteOutlined />} loading={deleteMutation.isPending}>
                {t('common.delete')}
              </Button>
            </Popconfirm>
          </PermissionButton>
        </div>
      </div>

      {/* Stats */}
      <div className={styles.statsRow}>
        <div className={styles.statCard}>
          <span className={styles.statValue}>{kb.fileCount}</span>
          <span className={styles.statLabel}>{t('kb.detail.statDocCount')}</span>
        </div>
        <div className={styles.statCard}>
          <span className={styles.statValue}>{formatBytes(kb.totalSize)}</span>
          <span className={styles.statLabel}>{t('kb.detail.statTotalSize')}</span>
        </div>
        <div className={styles.statCard}>
          <span className={styles.statValue}>{kb.chunkCount}</span>
          <span className={styles.statLabel}>{t('kb.detail.statChunkCount')}</span>
        </div>
        <div className={styles.statCard}>
          <span
            className={styles.statValue}
            style={{
              color: kb.status === 'NORMAL' ? 'var(--kf-success)' : 'var(--kf-muted-foreground)',
            }}
          >
            {t(kb.status === 'NORMAL' ? 'kb.detail.statusNormal' : 'kb.detail.statusEmpty')}
          </span>
          <span className={styles.statLabel}>{t('kb.detail.statStatus')}</span>
        </div>
      </div>

      {/* Documents Section */}
      <div className={styles.section}>
        <div className={styles.sectionHeader}>
          <h2 className={styles.sectionTitle}>
            <InboxOutlined style={{ marginRight: 8 }} />
            {t('kb.detail.docsSection')}
          </h2>
          <PermissionButton permission="doc:write" mode="hide">
            <GradientButton icon={<UploadOutlined />} size="sm" onClick={() => setUploadOpen(true)}>
              {t('kb.detail.uploadBtn')}
            </GradientButton>
          </PermissionButton>
        </div>

        {docsError ? (
          <ErrorRetry onRetry={refetchDocs} />
        ) : docs?.length === 0 && !docsLoading ? (
          <EmptyState
            title={t('kb.detail.emptyDocsTitle')}
            description={t('kb.detail.emptyDocsDesc')}
            action={
              <PermissionButton permission="doc:write" mode="hide">
                <GradientButton icon={<UploadOutlined />} onClick={() => setUploadOpen(true)}>
                  {t('kb.detail.uploadBtn')}
                </GradientButton>
              </PermissionButton>
            }
          />
        ) : (
          <Table
            rowKey="fileMd5"
            columns={columns}
            dataSource={docs}
            loading={docsLoading}
            pagination={false}
            size="middle"
            style={{ marginTop: 4 }}
          />
        )}
      </div>

      {/* Upload Modal */}
      <Modal
        title={t('kb.detail.uploadBtn')}
        open={uploadOpen}
        onCancel={() => setUploadOpen(false)}
        footer={null}
        destroyOnClose
        width={560}
      >
        <ChunkedUploader kbId={kbId} onSuccess={handleUploadSuccess} />
      </Modal>

      {/* Edit KB Modal */}
      <Modal
        title={t('kb.editTitle')}
        open={editOpen}
        onCancel={() => setEditOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={updateMutation.isPending}
        okText={t('kb.saveOk')}
        destroyOnClose
      >
        <Form form={form} layout="vertical" onFinish={(values) => updateMutation.mutate(values)}>
          <Form.Item
            name="name"
            label={t('kb.form.name')}
            rules={[{ required: true, message: t('kb.form.nameRequired') }]}
          >
            <Input placeholder={t('kb.form.namePlaceholder')} />
          </Form.Item>
          <Form.Item name="description" label={t('kb.form.description')}>
            <Input.TextArea rows={3} placeholder={t('kb.form.descriptionPlaceholder')} />
          </Form.Item>
          <Form.Item name="orgTag" label={t('kb.form.orgTag')}>
            <Input placeholder={t('kb.form.orgTagPlaceholder')} />
          </Form.Item>
          <Form.Item name="isPublic" label={t('kb.form.isPublic')} valuePropName="checked">
            <Switch checkedChildren={t('common.public')} unCheckedChildren={t('common.private')} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
