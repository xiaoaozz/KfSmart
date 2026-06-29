import { useState } from 'react'
import { Drawer, Descriptions, Tag, Button, Typography, Tabs } from 'antd'
import { DownloadOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { docApi } from '@/api/document'
import type { Document, DocStatus } from '@/types/document'
import DocContentPreview from './DocContentPreview'

interface DocPreviewDrawerProps {
  doc: Document | null
  open: boolean
  onClose: () => void
}

const STATUS_COLOR: Record<DocStatus, string> = {
  pending: 'default',
  processing: 'processing',
  done: 'success',
  error: 'error',
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

export default function DocPreviewDrawer({ doc, open, onClose }: DocPreviewDrawerProps) {
  const { t } = useTranslation()
  const [activeTab, setActiveTab] = useState('basic')

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

  const statusLabelKey: Record<DocStatus, string> = {
    pending: 'doc.statusPending',
    processing: 'doc.statusProcessing',
    done: 'doc.statusDone',
    error: 'doc.statusError',
  }

  return (
    <Drawer
      title={t('doc.detailTitle')}
      open={open}
      onClose={onClose}
      width={640}
      extra={
        doc && (
          <Button icon={<DownloadOutlined />} onClick={handleDownload}>
            {t('doc.download')}
          </Button>
        )
      }
    >
      {doc ? (
        <Tabs
          activeKey={activeTab}
          onChange={setActiveTab}
          defaultActiveKey="basic"
          items={[
            {
              key: 'basic',
              label: t('doc.tabBasic'),
              children: (
                <Descriptions column={1} bordered size="small">
                  <Descriptions.Item label={t('doc.colFileName')}>
                    <Typography.Text copyable>{doc.fileName}</Typography.Text>
                  </Descriptions.Item>
                  <Descriptions.Item label={t('doc.colType')}>
                    {doc.fileType.toUpperCase()}
                  </Descriptions.Item>
                  <Descriptions.Item label={t('doc.colSize')}>
                    {formatBytes(doc.fileSize)}
                  </Descriptions.Item>
                  <Descriptions.Item label={t('doc.colStatus')}>
                    <Tag color={STATUS_COLOR[doc.status]}>{t(statusLabelKey[doc.status])}</Tag>
                  </Descriptions.Item>
                  <Descriptions.Item label={t('doc.colKb')}>
                    {doc.knowledgeBaseName ?? '—'}
                  </Descriptions.Item>
                  <Descriptions.Item label={t('doc.chunkCount')}>
                    {doc.status === 'done' ? doc.chunkCount : '—'}
                  </Descriptions.Item>
                  <Descriptions.Item label={t('doc.colUploadedBy')}>
                    {doc.uploadedBy}
                  </Descriptions.Item>
                  <Descriptions.Item label={t('doc.colUploadTime')}>
                    {new Date(doc.createTime).toLocaleString()}
                  </Descriptions.Item>
                  <Descriptions.Item label={t('doc.updateTime')}>
                    {new Date(doc.updateTime).toLocaleString()}
                  </Descriptions.Item>
                </Descriptions>
              ),
            },
            {
              key: 'content',
              label: t('doc.tabContent'),
              // Lazy: only mount the (query-fetching) content view when this tab is active.
              children: activeTab === 'content' ? <DocContentPreview doc={doc} /> : null,
            },
          ]}
        />
      ) : null}
    </Drawer>
  )
}
