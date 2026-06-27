import { useState, useRef } from 'react'
import { Upload, Progress, App, Button } from 'antd'
import { InboxOutlined, CheckCircleOutlined, CloseCircleOutlined } from '@ant-design/icons'
import type { UploadFile } from 'antd'
import { uploadApi } from '@/api/upload'

const { Dragger } = Upload

const CHUNK_SIZE = 5 * 1024 * 1024 // 5 MB
const CONCURRENCY = 3
const SIMPLE_THRESHOLD = 10 * 1024 * 1024 // files ≤ 10 MB use simple upload

interface FileTask {
  uid: string
  name: string
  size: number
  percent: number
  status: 'waiting' | 'uploading' | 'done' | 'error'
  error?: string
  documentId?: number
}

interface ChunkedUploaderProps {
  knowledgeBaseId?: number
  onSuccess?: (documentId: number, fileName: string) => void
  maxCount?: number
  accept?: string
}

async function runConcurrent<T>(tasks: (() => Promise<T>)[], concurrency: number): Promise<T[]> {
  const results: T[] = []
  let i = 0
  const workers = Array.from({ length: Math.min(concurrency, tasks.length) }, async () => {
    while (i < tasks.length) {
      const idx = i++
      results[idx] = await tasks[idx]()
    }
  })
  await Promise.all(workers)
  return results
}

export default function ChunkedUploader({
  knowledgeBaseId,
  onSuccess,
  maxCount = 10,
  accept = '.pdf,.doc,.docx,.md,.txt,.csv,.xlsx,.pptx',
}: ChunkedUploaderProps) {
  const [tasks, setTasks] = useState<FileTask[]>([])
  const abortRefs = useRef<Record<string, boolean>>({})
  const { message } = App.useApp()

  const updateTask = (uid: string, patch: Partial<FileTask>) =>
    setTasks((prev) => prev.map((t) => (t.uid === uid ? { ...t, ...patch } : t)))

  const uploadFile = async (file: File, uid: string) => {
    abortRefs.current[uid] = false
    updateTask(uid, { status: 'uploading', percent: 0 })

    try {
      // Small file → simple upload
      if (file.size <= SIMPLE_THRESHOLD) {
        const result = await uploadApi.simple(file, knowledgeBaseId)
        updateTask(uid, { status: 'done', percent: 100, documentId: result.documentId })
        onSuccess?.(result.documentId, file.name)
        return
      }

      // Large file → chunked upload
      // fileMd5 is a pseudo-key until real Worker-based MD5 is implemented
      const fileMd5 = `${file.name}-${file.size}-${file.lastModified}`
      const totalChunks = Math.ceil(file.size / CHUNK_SIZE)

      // Check for existing chunks (resume support)
      let uploadedChunks: number[] = []
      try {
        const status = await uploadApi.status(fileMd5)
        if (status.finished) {
          updateTask(uid, { status: 'done', percent: 100 })
          return
        }
        uploadedChunks = status.uploadedChunks
      } catch {
        // No existing task, start fresh
      }

      const pendingChunks = Array.from({ length: totalChunks }, (_, i) => i).filter(
        (i) => !uploadedChunks.includes(i),
      )

      let doneCount = uploadedChunks.length

      const chunkTasks = pendingChunks.map((chunkIndex) => async () => {
        if (abortRefs.current[uid]) throw new Error('cancelled')
        const start = chunkIndex * CHUNK_SIZE
        const chunk = file.slice(start, start + CHUNK_SIZE)
        await uploadApi.uploadChunk({
          fileMd5,
          fileName: file.name,
          chunkIndex,
          totalSize: file.size,
          chunk,
          knowledgeBaseId,
        })
        doneCount++
        updateTask(uid, { percent: Math.round((doneCount / totalChunks) * 95) })
      })

      await runConcurrent(chunkTasks, CONCURRENCY)

      if (abortRefs.current[uid]) {
        updateTask(uid, { status: 'error', error: '已取消' })
        return
      }

      // Merge
      const result = await uploadApi.merge(fileMd5, file.name, knowledgeBaseId)
      updateTask(uid, { status: 'done', percent: 100, documentId: result.documentId })
      onSuccess?.(result.documentId, file.name)
    } catch (e) {
      const msg = e instanceof Error ? e.message : '上传失败'
      if (msg !== 'cancelled') {
        updateTask(uid, { status: 'error', error: msg })
        message.error(`${file.name} 上传失败：${msg}`)
      }
    }
  }

  const handleBeforeUpload = (file: File) => {
    const uid = `${Date.now()}-${Math.random()}`
    const task: FileTask = {
      uid,
      name: file.name,
      size: file.size,
      percent: 0,
      status: 'waiting',
    }
    setTasks((prev) => [...prev, task])
    // Kick off async — return false to prevent antd's built-in upload
    setTimeout(() => uploadFile(file, uid), 0)
    return false
  }

  const handleCancel = (uid: string) => {
    abortRefs.current[uid] = true
    updateTask(uid, { status: 'error', error: '已取消' })
  }

  const uploadFiles = tasks.filter((t) => t.status !== 'done')

  return (
    <div>
      <Dragger
        multiple
        maxCount={maxCount}
        accept={accept}
        showUploadList={false}
        beforeUpload={handleBeforeUpload}
        fileList={[] as UploadFile[]}
      >
        <p className="ant-upload-drag-icon">
          <InboxOutlined style={{ color: 'var(--kf-accent)', fontSize: 40 }} />
        </p>
        <p className="ant-upload-text">点击或拖拽文件到此区域上传</p>
        <p className="ant-upload-hint">
          支持 PDF / Word / Markdown / TXT / CSV / Excel / PPT，单次最多 {maxCount} 个
        </p>
      </Dragger>

      {tasks.length > 0 && (
        <div style={{ marginTop: 16, display: 'flex', flexDirection: 'column', gap: 8 }}>
          {tasks.map((t) => (
            <div
              key={t.uid}
              style={{
                padding: '10px 14px',
                borderRadius: 'var(--kf-radius-sm)',
                background: 'var(--kf-muted)',
                border: '1px solid var(--kf-border)',
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
                <span style={{ fontSize: 13, color: 'var(--kf-foreground)', fontWeight: 500 }}>
                  {t.name}
                </span>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  {t.status === 'done' && (
                    <CheckCircleOutlined style={{ color: 'var(--kf-success)' }} />
                  )}
                  {t.status === 'error' && (
                    <CloseCircleOutlined style={{ color: 'var(--kf-danger)' }} />
                  )}
                  {t.status === 'uploading' && (
                    <Button size="small" danger onClick={() => handleCancel(t.uid)}>
                      取消
                    </Button>
                  )}
                </div>
              </div>

              {t.status === 'uploading' && (
                <Progress
                  percent={t.percent}
                  size="small"
                  strokeColor="var(--kf-accent)"
                  showInfo={false}
                />
              )}
              {t.status === 'error' && t.error && (
                <span style={{ fontSize: 12, color: 'var(--kf-danger)' }}>{t.error}</span>
              )}
              {t.status === 'done' && (
                <span style={{ fontSize: 12, color: 'var(--kf-success)' }}>
                  上传成功，已触发解析
                </span>
              )}
            </div>
          ))}
          {uploadFiles.length === 0 && tasks.length > 0 && (
            <Button size="small" onClick={() => setTasks([])}>
              清空列表
            </Button>
          )}
        </div>
      )}
    </div>
  )
}
