import { useState } from 'react'
import {
  Button,
  Input,
  Tag,
  Tabs,
  Drawer,
  Modal,
  Form,
  Select,
  App,
  Empty,
  Timeline,
  Divider,
} from 'antd'
import {
  PlusOutlined,
  SearchOutlined,
  EditOutlined,
  DeleteOutlined,
  HistoryOutlined,
  CopyOutlined,
  FileTextOutlined,
} from '@ant-design/icons'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { motion } from 'framer-motion'
import { promptApi } from '@/api/skill'
import type { PromptSummary, Prompt } from '@/types/skill'
import { PermissionButton } from '@/components/business'
import styles from './PromptListPage.module.css'

const CATEGORY_TABS = [
  { key: '', label: '全部' },
  { key: 'chat', label: '对话' },
  { key: 'summary', label: '摘要' },
  { key: 'translation', label: '翻译' },
  { key: 'code', label: '代码' },
  { key: 'analysis', label: '分析' },
  { key: 'custom', label: '自定义' },
]

const CATEGORY_COLORS: Record<string, string> = {
  chat: 'blue',
  summary: 'cyan',
  translation: 'green',
  code: 'purple',
  analysis: 'orange',
  custom: 'default',
}

export default function PromptListPage() {
  const qc = useQueryClient()
  const { message, modal } = App.useApp()

  const [keyword, setKeyword] = useState('')
  const [category, setCategory] = useState('')
  const [editPrompt, setEditPrompt] = useState<Prompt | null>(null)
  const [historyPromptId, setHistoryPromptId] = useState<number | null>(null)
  const [createOpen, setCreateOpen] = useState(false)
  const [createForm] = Form.useForm<{
    name: string
    description?: string
    category: string
    content: string
  }>()
  const [editForm] = Form.useForm<{
    name: string
    description?: string
    category: string
    content: string
    note?: string
  }>()

  const { data, isLoading } = useQuery({
    queryKey: ['prompts', category, keyword],
    queryFn: () =>
      promptApi.list({ size: 50, keyword: keyword || undefined, category: category || undefined }),
  })

  const { data: versions, isLoading: versionsLoading } = useQuery({
    queryKey: ['prompt-versions', historyPromptId],
    queryFn: () => promptApi.histories(historyPromptId!),
    enabled: !!historyPromptId,
  })

  const createMutation = useMutation({
    mutationFn: (v: { name: string; description?: string; category: string; content: string }) =>
      promptApi.create(v),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['prompts'] })
      setCreateOpen(false)
      createForm.resetFields()
      message.success('已创建')
    },
  })

  const updateMutation = useMutation({
    mutationFn: (v: {
      name: string
      description?: string
      category: string
      content: string
      note?: string
    }) => promptApi.update(editPrompt!.id, v),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['prompts'] })
      setEditPrompt(null)
      editForm.resetFields()
      message.success('已保存')
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => promptApi.delete(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['prompts'] })
      message.success('已删除')
    },
  })

  const handleDelete = (p: PromptSummary) => {
    modal.confirm({
      title: `删除 Prompt「${p.name}」？`,
      okType: 'danger',
      onOk: () => deleteMutation.mutateAsync(p.id),
    })
  }

  const handleEditOpen = (p: PromptSummary) => {
    promptApi.get(p.id).then((full) => {
      setEditPrompt(full)
      editForm.setFieldsValue({
        name: full.name,
        description: full.description,
        category: full.category,
        content: full.content,
      })
    })
  }

  const copyContent = (id: number) => {
    promptApi.get(id).then((p) => {
      navigator.clipboard.writeText(p.content).then(() => message.success('已复制'))
    })
  }

  const records = data?.records ?? []

  return (
    <div className={styles.root}>
      <div className={styles.topBar}>
        <h2 className={styles.pageTitle}>
          <FileTextOutlined /> Prompt 模板
        </h2>
        <div className={styles.actions}>
          <Input
            prefix={<SearchOutlined />}
            placeholder="搜索 Prompt…"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            style={{ width: 200 }}
            allowClear
          />
          <PermissionButton permission="prompt:create">
            <Button
              type="primary"
              icon={<PlusOutlined />}
              style={{ background: 'var(--kf-accent-gradient-r)', border: 'none' }}
              onClick={() => setCreateOpen(true)}
            >
              新建 Prompt
            </Button>
          </PermissionButton>
        </div>
      </div>

      <Tabs
        activeKey={category}
        onChange={setCategory}
        items={CATEGORY_TABS}
        style={{ marginBottom: 16 }}
      />

      {isLoading ? (
        <div className={styles.listSkeleton}>
          {Array.from({ length: 5 }).map((_, i) => (
            <div key={i} className={styles.skeleton} />
          ))}
        </div>
      ) : !records.length ? (
        <Empty description="暂无 Prompt 模板" />
      ) : (
        <div className={styles.list}>
          {records.map((p: PromptSummary, i: number) => (
            <motion.div
              key={p.id}
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: i * 0.03 }}
              className={styles.row}
            >
              <div className={styles.rowLeft}>
                <div className={styles.rowName}>{p.name}</div>
                <div className={styles.rowMeta}>
                  <Tag color={CATEGORY_COLORS[p.category]}>{p.category}</Tag>
                  {p.description && <span className={styles.rowDesc}>{p.description}</span>}
                  <span className={styles.rowUsage}>使用 {p.useCount} 次</span>
                </div>
              </div>
              <div className={styles.rowActions}>
                <Button size="small" icon={<CopyOutlined />} onClick={() => copyContent(p.id)}>
                  复制
                </Button>
                <Button size="small" icon={<EditOutlined />} onClick={() => handleEditOpen(p)}>
                  编辑
                </Button>
                <Button
                  size="small"
                  icon={<HistoryOutlined />}
                  onClick={() => setHistoryPromptId(p.id)}
                >
                  历史
                </Button>
                <PermissionButton permission="prompt:delete">
                  <Button
                    size="small"
                    danger
                    icon={<DeleteOutlined />}
                    onClick={() => handleDelete(p)}
                  />
                </PermissionButton>
              </div>
            </motion.div>
          ))}
        </div>
      )}

      {/* Create Modal */}
      <Modal
        title="新建 Prompt"
        open={createOpen}
        onCancel={() => {
          setCreateOpen(false)
          createForm.resetFields()
        }}
        onOk={() => createForm.submit()}
        confirmLoading={createMutation.isPending}
        destroyOnClose
        width={640}
      >
        <Form form={createForm} layout="vertical" onFinish={(v) => createMutation.mutate(v)}>
          <div style={{ display: 'flex', gap: 12 }}>
            <Form.Item name="name" label="名称" rules={[{ required: true }]} style={{ flex: 1 }}>
              <Input />
            </Form.Item>
            <Form.Item name="category" label="分类" initialValue="custom" style={{ width: 130 }}>
              <Select options={CATEGORY_TABS.slice(1)} />
            </Form.Item>
          </div>
          <Form.Item name="description" label="描述">
            <Input />
          </Form.Item>
          <Form.Item name="content" label="Prompt 内容" rules={[{ required: true }]}>
            <Input.TextArea
              rows={10}
              style={{ fontFamily: 'var(--kf-font-mono)', fontSize: 13 }}
              placeholder="你是一个...&#10;&#10;用户输入：{{input}}"
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* Edit Modal */}
      <Modal
        title="编辑 Prompt"
        open={!!editPrompt}
        onCancel={() => {
          setEditPrompt(null)
          editForm.resetFields()
        }}
        onOk={() => editForm.submit()}
        confirmLoading={updateMutation.isPending}
        destroyOnClose
        width={640}
      >
        <Form form={editForm} layout="vertical" onFinish={(v) => updateMutation.mutate(v)}>
          <div style={{ display: 'flex', gap: 12 }}>
            <Form.Item name="name" label="名称" rules={[{ required: true }]} style={{ flex: 1 }}>
              <Input />
            </Form.Item>
            <Form.Item name="category" label="分类" style={{ width: 130 }}>
              <Select options={CATEGORY_TABS.slice(1)} />
            </Form.Item>
          </div>
          <Form.Item name="description" label="描述">
            <Input />
          </Form.Item>
          <Form.Item name="content" label="Prompt 内容" rules={[{ required: true }]}>
            <Input.TextArea rows={10} style={{ fontFamily: 'var(--kf-font-mono)', fontSize: 13 }} />
          </Form.Item>
          <Form.Item name="note" label="版本备注">
            <Input placeholder="描述本次改动内容（可选）" />
          </Form.Item>
        </Form>
      </Modal>

      {/* History Drawer */}
      <Drawer
        title="版本历史"
        open={!!historyPromptId}
        onClose={() => setHistoryPromptId(null)}
        width={400}
      >
        {versionsLoading ? (
          <div style={{ display: 'flex', justifyContent: 'center', padding: 40 }}>加载中…</div>
        ) : !versions?.length ? (
          <Empty description="暂无历史版本" />
        ) : (
          <Timeline
            items={versions.map((v) => ({
              color: 'blue',
              children: (
                <div className={styles.versionItem}>
                  <div className={styles.versionHeader}>
                    <Tag color="blue">v{v.version}</Tag>
                    <span className={styles.versionTime}>
                      {new Date(v.createTime).toLocaleString()}
                    </span>
                  </div>
                  {v.note && <div className={styles.versionNote}>{v.note}</div>}
                  <Divider style={{ margin: '8px 0' }} />
                  <pre className={styles.versionContent}>
                    {v.content.slice(0, 200)}
                    {v.content.length > 200 ? '…' : ''}
                  </pre>
                </div>
              ),
            }))}
          />
        )}
      </Drawer>
    </div>
  )
}
