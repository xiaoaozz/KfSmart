import { Drawer, Descriptions, Tag, Button, Typography } from 'antd'
import { DownloadOutlined } from '@ant-design/icons'
import { docApi } from '@/api/document'
import type { Document, DocStatus } from '@/types/document'

const STATUS_MAP: Record<DocStatus, { color: string; label: string }> = {
  pending: { color: 'default', label: '等待中' },
  processing: { color: 'processing', label: '解析中' },
  done: { color: 'success', label: '已就绪' },
  error: { color: 'error', label: '解析失败' },
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

interface DocPreviewDrawerProps {
  doc: Document | null
  open: boolean
  onClose: () => void
}

export default function DocPreviewDrawer({ doc, open, onClose }: DocPreviewDrawerProps) {
  const handleDownload = async () => {
    if (!doc) return
    const blob = await docApi.download(doc.fileName)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = doc.fileName
    a.click()
    URL.revokeObjectURL(url)
  }

  const status = doc ? STATUS_MAP[doc.status] : null

  return (
    <Drawer
      title="文档详情"
      open={open}
      onClose={onClose}
      width={480}
      extra={
        doc && (
          <Button icon={<DownloadOutlined />} onClick={handleDownload}>
            下载
          </Button>
        )
      }
    >
      {doc ? (
        <>
          <Descriptions column={1} bordered size="small">
            <Descriptions.Item label="文件名">
              <Typography.Text copyable>{doc.fileName}</Typography.Text>
            </Descriptions.Item>
            <Descriptions.Item label="文件类型">{doc.fileType.toUpperCase()}</Descriptions.Item>
            <Descriptions.Item label="文件大小">{formatBytes(doc.fileSize)}</Descriptions.Item>
            <Descriptions.Item label="状态">
              {status && <Tag color={status.color}>{status.label}</Tag>}
            </Descriptions.Item>
            <Descriptions.Item label="所属知识库">{doc.knowledgeBaseName ?? '—'}</Descriptions.Item>
            <Descriptions.Item label="分片数">
              {doc.status === 'done' ? doc.chunkCount : '—'}
            </Descriptions.Item>
            <Descriptions.Item label="上传者">{doc.uploadedBy}</Descriptions.Item>
            <Descriptions.Item label="上传时间">
              {new Date(doc.createTime).toLocaleString()}
            </Descriptions.Item>
            <Descriptions.Item label="更新时间">
              {new Date(doc.updateTime).toLocaleString()}
            </Descriptions.Item>
          </Descriptions>
        </>
      ) : null}
    </Drawer>
  )
}
