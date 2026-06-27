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
import { docApi } from '@/api/document'
import type { Document, DocStatus } from '@/types/document'
import PageTable, { type TableColumnType } from '@/components/business/PageTable'
import ChunkedUploader from '@/components/business/ChunkedUploader'
import DocPreviewDrawer from '@/components/business/DocPreviewDrawer'
import { PermissionButton } from '@/components/business'
import styles from './DocumentListPage.module.css'

const STATUS_CFG: Record<DocStatus, { color: string; label: string }> = {
  pending: { color: 'default', label: '等待解析' },
  processing: { color: 'processing', label: '解析中' },
  done: { color: 'success', label: '已就绪' },
  error: { color: 'error', label: '解析失败' },
}

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
      message.success('删除成功')
    },
  })

  const reparseMutation = useMutation({
    mutationFn: docApi.reparse,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['documents'] })
      message.success('已重新触发解析')
    },
  })

  const columns: TableColumnType<Document>[] = [
    {
      title: '文件名',
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
      title: '类型',
      dataIndex: 'fileType',
      width: 80,
      render: (t: string) => t.toUpperCase(),
    },
    {
      title: '大小',
      dataIndex: 'fileSize',
      width: 90,
      render: (n: number) => formatBytes(n),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (s: DocStatus) => <Tag color={STATUS_CFG[s].color}>{STATUS_CFG[s].label}</Tag>,
    },
    {
      title: '所属知识库',
      dataIndex: 'knowledgeBaseName',
      ellipsis: true,
      render: (v?: string) => v ?? '—',
    },
    {
      title: '上传者',
      dataIndex: 'uploadedBy',
      width: 100,
    },
    {
      title: '上传时间',
      dataIndex: 'createTime',
      width: 160,
      render: (t: string) => new Date(t).toLocaleString(),
    },
    {
      title: '操作',
      key: 'action',
      fixed: 'right' as const,
      width: 120,
      render: (_: unknown, row) => (
        <div style={{ display: 'flex', gap: 4 }}>
          <Tooltip title="预览详情">
            <Button
              type="text"
              size="small"
              icon={<EyeOutlined />}
              onClick={() => setPreviewDoc(row)}
            />
          </Tooltip>
          {row.status === 'error' && (
            <Tooltip title="重新解析">
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
              title="确认删除此文档？"
              description="删除后数据及向量索引均不可恢复"
              onConfirm={() => deleteMutation.mutate(row.fileMd5)}
              okText="删除"
              okButtonProps={{ danger: true }}
              cancelText="取消"
            >
              <Tooltip title="删除">
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
      {/* Toolbar */}
      <div className={styles.toolbar}>
        <div className={styles.filters}>
          <Input
            prefix={<SearchOutlined />}
            placeholder="搜索文档名"
            value={keyword}
            onChange={(e) => {
              setKeyword(e.target.value)
              setCurrent(1)
            }}
            allowClear
            style={{ width: 240 }}
          />
          <Select
            placeholder="解析状态"
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
            上传文档
          </Button>
        </PermissionButton>
      </div>

      <PageTable<Document>
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
      />

      {/* Upload Modal */}
      <Modal
        title="上传文档"
        open={uploadOpen}
        onCancel={() => setUploadOpen(false)}
        footer={null}
        width={560}
        destroyOnClose
      >
        <ChunkedUploader
          onSuccess={(_, name) => {
            qc.invalidateQueries({ queryKey: ['documents'] })
            message.success(`${name} 上传成功，正在解析`)
          }}
        />
      </Modal>

      {/* Preview Drawer */}
      <DocPreviewDrawer doc={previewDoc} open={!!previewDoc} onClose={() => setPreviewDoc(null)} />
    </div>
  )
}
