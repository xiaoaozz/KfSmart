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
import { useTranslation } from 'react-i18next'
import { promptApi } from '@/api/skill'
import type { PromptSummary, Prompt } from '@/types/skill'
import { PermissionButton } from '@/components/business'
import styles from './PromptListPage.module.css'

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
  const { t } = useTranslation()

  const CATEGORY_TABS = [
    { key: '', label: t('skill.prompt.categoryAll') },
    { key: 'chat', label: t('skill.prompt.categoryChat') },
    { key: 'summary', label: t('skill.prompt.categorySummary') },
    { key: 'translation', label: t('skill.prompt.categoryTranslation') },
    { key: 'code', label: t('skill.prompt.categoryCode') },
    { key: 'analysis', label: t('skill.prompt.categoryAnalysis') },
    { key: 'custom', label: t('skill.prompt.categoryCustom') },
  ]

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
      message.success(t('skill.prompt.createSuccess'))
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
      message.success(t('skill.prompt.updateSuccess'))
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => promptApi.delete(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['prompts'] })
      message.success(t('skill.prompt.deleteSuccess'))
    },
  })

  const handleDelete = (p: PromptSummary) => {
    modal.confirm({
      title: t('skill.prompt.deleteConfirm', { name: p.name }),
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
      navigator.clipboard
        .writeText(p.content)
        .then(() => message.success(t('skill.prompt.copySuccess')))
    })
  }

  const records = data?.records ?? []

  return (
    <div className={styles.root}>
      <div className={styles.topBar}>
        <h2 className={styles.pageTitle}>
          <FileTextOutlined /> {t('skill.prompt.title')}
        </h2>
        <div className={styles.actions}>
          <Input
            prefix={<SearchOutlined />}
            placeholder={t('skill.prompt.searchPlaceholder')}
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
              {t('skill.prompt.createBtn')}
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
        <Empty description={t('skill.prompt.empty')} />
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
                  <span className={styles.rowUsage}>
                    {t('skill.prompt.usageCount', { count: p.useCount })}
                  </span>
                </div>
              </div>
              <div className={styles.rowActions}>
                <Button size="small" icon={<CopyOutlined />} onClick={() => copyContent(p.id)}>
                  {t('skill.prompt.copyBtn')}
                </Button>
                <Button size="small" icon={<EditOutlined />} onClick={() => handleEditOpen(p)}>
                  {t('skill.prompt.editBtn')}
                </Button>
                <Button
                  size="small"
                  icon={<HistoryOutlined />}
                  onClick={() => setHistoryPromptId(p.id)}
                >
                  {t('skill.prompt.historyBtn')}
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
        title={t('skill.prompt.createModalTitle')}
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
            <Form.Item
              name="name"
              label={t('skill.prompt.fieldName')}
              rules={[{ required: true }]}
              style={{ flex: 1 }}
            >
              <Input />
            </Form.Item>
            <Form.Item
              name="category"
              label={t('skill.prompt.fieldCategory')}
              initialValue="custom"
              style={{ width: 130 }}
            >
              <Select options={CATEGORY_TABS.slice(1)} />
            </Form.Item>
          </div>
          <Form.Item name="description" label={t('skill.prompt.fieldDesc')}>
            <Input />
          </Form.Item>
          <Form.Item
            name="content"
            label={t('skill.prompt.fieldContent')}
            rules={[{ required: true }]}
          >
            <Input.TextArea
              rows={10}
              style={{ fontFamily: 'var(--kf-font-mono)', fontSize: 13 }}
              placeholder={t('skill.prompt.contentPlaceholder')}
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* Edit Modal */}
      <Modal
        title={t('skill.prompt.editModalTitle')}
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
            <Form.Item
              name="name"
              label={t('skill.prompt.fieldName')}
              rules={[{ required: true }]}
              style={{ flex: 1 }}
            >
              <Input />
            </Form.Item>
            <Form.Item
              name="category"
              label={t('skill.prompt.fieldCategory')}
              style={{ width: 130 }}
            >
              <Select options={CATEGORY_TABS.slice(1)} />
            </Form.Item>
          </div>
          <Form.Item name="description" label={t('skill.prompt.fieldDesc')}>
            <Input />
          </Form.Item>
          <Form.Item
            name="content"
            label={t('skill.prompt.fieldContent')}
            rules={[{ required: true }]}
          >
            <Input.TextArea rows={10} style={{ fontFamily: 'var(--kf-font-mono)', fontSize: 13 }} />
          </Form.Item>
          <Form.Item name="note" label={t('skill.prompt.fieldNote')}>
            <Input placeholder={t('skill.prompt.notePlaceholder')} />
          </Form.Item>
        </Form>
      </Modal>

      {/* History Drawer */}
      <Drawer
        title={t('skill.prompt.historyDrawerTitle')}
        open={!!historyPromptId}
        onClose={() => setHistoryPromptId(null)}
        width={400}
      >
        {versionsLoading ? (
          <div style={{ display: 'flex', justifyContent: 'center', padding: 40 }}>
            {t('skill.prompt.historyLoading')}
          </div>
        ) : !versions?.length ? (
          <Empty description={t('skill.prompt.historyEmpty')} />
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
