import { useState } from 'react'
import { Input, Select, Tag, Tooltip, Popconfirm, Button, Modal, App } from 'antd'
import {
  SearchOutlined,
  UploadOutlined,
  EyeOutlined,
  DeleteOutlined,
  ReloadOutlined,
  FileTextOutlined,
  FilePdfOutlined,
  FileWordOutlined,
  FileExcelOutlined,
  FilePptOutlined,
  FileMarkdownOutlined,
} from '@ant-design/icons'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { docApi } from '@/api/document'
import type { Document, DocStatus } from '@/types/document'
import PageTable, { type TableColumnType } from '@/components/business/PageTable'
import ChunkedUploader from '@/components/business/ChunkedUploader'
import DocPreviewDrawer from '@/components/business/DocPreviewDrawer'
import { PermissionButton, FavoriteButton, PageBar } from '@/components/business'
import styles from './DocumentListPage.module.css'

function FileIcon({ type }: { type: string }) {
  const t = type.toLowerCase()
  if (t === 'pdf') return <FilePdfOutlined style={{ color: '#ff4d4f', fontSize: 18 }} />
  if (t === 'doc' || t === 'docx')
    return <FileWordOutlined style={{ color: '#1677ff', fontSize: 18 }} />
  if (t === 'xls' || t === 'xlsx' || t === 'csv')
    return <FileExcelOutlined style={{ color: '#52c41a', fontSize: 18 }} />
  if (t === 'ppt' || t === 'pptx')
    return <FilePptOutlined style={{ color: '#fa8c16', fontSize: 18 }} />
  if (t === 'md') return <FileMarkdownOutlined style={{ color: '#722ed1', fontSize: 18 }} />
  return <FileTextOutlined style={{ color: 'var(--kf-muted-foreground)', fontSize: 18 }} />
}

function formatBytes(n: number) {
  if (n < 1024) return `${n} B`
  if (n < 1024 ** 2) return `${(n / 1024).toFixed(1)} KB`
  return `${(n / 1024 ** 2).toFixed(1)} MB`
}

export default function DocumentListPage() {
  const [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState<DocStatus | undefined>()
  const [current, setCurrent] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [uploadOpen, setUploadOpen] = useState(false)
  const [previewDoc, setPreviewDoc] = useState<import('@/types/document').Document | null>(null)
  const qc = useQueryClient()
  const { message } = App.useApp()
  const { t } = useTranslation()

  const STATUS_CFG: Record<DocStatus, { color: string; label: string }> = {
    pending: { color: 'default', label: t('doc.statusPending') },
    processing: { color: 'processing', label: t('doc.statusProcessing') },
    done: { color: 'success', label: t('doc.statusDone') },
    error: { color: 'error', label: t('doc.statusError') },
  }

  const queryParams = { keyword: keyword || undefined, status, current, size: pageSize }

  const { data, isLoading } = useQuery({
    queryKey: ['documents', queryParams],
    queryFn: () => docApi.list(queryParams),
    // Poll while any doc is processing
    refetchInterval: (query) =>
      query.state.data?.records.some((d) => d.status === 'processing') ? 3000 : false,
  })

  const deleteMutation = useMutation({
    mutationFn: docApi.delete,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['documents'] })
      message.success(t('doc.deleteSuccess'))
    },
  })

  const reparseMutation = useMutation({
    mutationFn: docApi.reparse,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['documents'] })
      message.success(t('doc.reparseSuccess'))
    },
  })

  const columns: TableColumnType<Document>[] = [
    {
      title: t('doc.colFileName'),
      dataIndex: 'fileName',
      ellipsis: true,
      render: (name: string, row) => (
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <FileIcon type={row.fileType} />
          <span style={{ fontWeight: 500 }}>{name}</span>
        </div>
      ),
    },
    {
      title: t('doc.colType'),
      dataIndex: 'fileType',
      width: 80,
      render: (v: string) => v.toUpperCase(),
    },
    {
      title: t('doc.colSize'),
      dataIndex: 'fileSize',
      width: 90,
      render: (n: number) => formatBytes(n),
    },
    {
      title: t('doc.colStatus'),
      dataIndex: 'status',
      width: 100,
      render: (s: DocStatus) => <Tag color={STATUS_CFG[s].color}>{STATUS_CFG[s].label}</Tag>,
    },
    {
      title: t('doc.colKb'),
      dataIndex: 'knowledgeBaseName',
      ellipsis: true,
      render: (v?: string) => v ?? '—',
    },
    {
      title: t('doc.colUploadedBy'),
      dataIndex: 'uploadedBy',
      width: 100,
    },
    {
      title: t('doc.colUploadTime'),
      dataIndex: 'createTime',
      width: 160,
      render: (v: string) => new Date(v).toLocaleString(),
    },
    {
      title: t('doc.colActions'),
      key: 'action',
      fixed: 'right' as const,
      width: 120,
      render: (_: unknown, row) => (
        <div style={{ display: 'flex', gap: 4 }}>
          <FavoriteButton type="document" targetId={row.fileMd5} title={row.fileName} />
          <Tooltip title={t('doc.previewTooltip')}>
            <Button
              type="text"
              size="small"
              icon={<EyeOutlined />}
              onClick={() => setPreviewDoc(row)}
            />
          </Tooltip>
          {row.status === 'error' && (
            <Tooltip title={t('doc.reparseTooltip')}>
              <PermissionButton permission="doc:write" mode="hide">
                <Button
                  type="text"
                  size="small"
                  icon={<ReloadOutlined />}
                  loading={reparseMutation.isPending}
                  onClick={() => reparseMutation.mutate(row.fileMd5)}
                />
              </PermissionButton>
            </Tooltip>
          )}
          <PermissionButton permission="doc:delete" mode="hide">
            <Popconfirm
              title={t('doc.deleteConfirm')}
              description={t('doc.deleteDesc')}
              onConfirm={() => deleteMutation.mutate(row.fileMd5)}
              okText={t('common.delete')}
              okButtonProps={{ danger: true }}
              cancelText={t('common.cancel')}
            >
              <Tooltip title={t('doc.deleteTooltip')}>
                <Button type="text" size="small" danger icon={<DeleteOutlined />} />
              </Tooltip>
            </Popconfirm>
          </PermissionButton>
        </div>
      ),
    },
  ]

  return (
    <div className={styles.root}>
      <div className={styles.pageHeader}>
        <div className={styles.toolbar}>
          <div className={styles.filters}>
            <Input
              prefix={<SearchOutlined />}
              placeholder={t('doc.searchPlaceholder')}
              value={keyword}
              onChange={(e) => {
                setKeyword(e.target.value)
                setCurrent(1)
              }}
              allowClear
              style={{ width: 240 }}
            />
            <Select
              placeholder={t('doc.statusPlaceholder')}
              allowClear
              value={status}
              onChange={(v) => {
                setStatus(v)
                setCurrent(1)
              }}
              style={{ width: 130 }}
              options={Object.entries(STATUS_CFG).map(([k, v]) => ({ label: v.label, value: k }))}
            />
          </div>
          <PermissionButton permission="doc:write" mode="hide">
            <Button
              type="primary"
              icon={<UploadOutlined />}
              style={{ background: 'var(--kf-accent-gradient-r)', border: 'none' }}
              onClick={() => setUploadOpen(true)}
            >
              {t('doc.uploadBtn')}
            </Button>
          </PermissionButton>
        </div>
      </div>

      <div className={styles.body}>
        <PageTable<Document>
          columns={columns}
          dataSource={data?.records}
          loading={isLoading}
          showPagination={false}
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

      {/* Upload Modal */}
      <Modal
        title={t('doc.uploadModalTitle')}
        open={uploadOpen}
        onCancel={() => setUploadOpen(false)}
        footer={null}
        width={560}
        destroyOnClose
      >
        <ChunkedUploader
          onSuccess={(_, name) => {
            qc.invalidateQueries({ queryKey: ['documents'] })
            message.success(t('doc.uploadSuccess', { name }))
          }}
        />
      </Modal>

      {/* Preview Drawer */}
      <DocPreviewDrawer doc={previewDoc} open={!!previewDoc} onClose={() => setPreviewDoc(null)} />
    </div>
  )
}
