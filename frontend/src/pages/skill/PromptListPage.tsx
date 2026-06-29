import { useState } from 'react'
import {
  Button,
  Input,
  Tag,
  Drawer,
  Modal,
  Form,
  Select,
  App,
  Empty,
  Timeline,
  Divider,
  Tooltip,
  Spin,
  Descriptions,
} from 'antd'
import {
  PlusOutlined,
  SearchOutlined,
  EditOutlined,
  DeleteOutlined,
  HistoryOutlined,
  CopyOutlined,
  EyeOutlined,
  FileTextOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { motion } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import { promptApi } from '@/api/skill'
import type { PromptSummary, Prompt } from '@/types/skill'
import { GradientButton, GradientCard } from '@/components/base'
import { PermissionButton, FavoriteButton, PageBar, EmptyState } from '@/components/business'
import styles from './PromptListPage.module.css'

const CATEGORY_COLORS: Record<string, string> = {
  chat: 'blue',
  summary: 'cyan',
  translation: 'green',
  code: 'purple',
  analysis: 'orange',
  custom: 'default',
}

// Solid accent color per category, used for the colored category pill
const CATEGORY_ACCENT: Record<string, string> = {
  chat: '#1677ff',
  summary: '#13c2c2',
  translation: '#52c41a',
  code: '#722ed1',
  analysis: '#fa8c16',
  custom: '#8c8c8c',
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

  // Select options require { label, value } — using key as value
  const CATEGORY_SELECT_OPTIONS = CATEGORY_TABS.slice(1).map((tab) => ({
    label: tab.label,
    value: tab.key,
  }))
  const CATEGORY_FILTER_OPTIONS = CATEGORY_TABS.map((tab) => ({
    label: tab.label,
    value: tab.key,
  }))

  const [keyword, setKeyword] = useState('')
  const [category, setCategory] = useState('')
  const [current, setCurrent] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [editPrompt, setEditPrompt] = useState<Prompt | null>(null)
  const [viewPrompt, setViewPrompt] = useState<Prompt | null>(null)
  const [viewLoading, setViewLoading] = useState(false)
  const [historyPromptId, setHistoryPromptId] = useState<string | null>(null)
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

  const { data, isLoading, isError } = useQuery({
    queryKey: ['prompts', category, keyword, current, pageSize],
    queryFn: () =>
      promptApi.list({
        page: current,
        size: pageSize,
        keyword: keyword || undefined,
        category: category || undefined,
      }),
    staleTime: 0,
  })

  const { data: versions, isLoading: versionsLoading } = useQuery({
    queryKey: ['prompt-versions', historyPromptId],
    queryFn: () => promptApi.histories(historyPromptId!),
    enabled: !!historyPromptId,
  })

  const invalidatePrompts = () =>
    qc.invalidateQueries({ queryKey: ['prompts'], refetchType: 'active' })

  const createMutation = useMutation({
    mutationFn: (v: { name: string; description?: string; category: string; content: string }) =>
      promptApi.create(v),
    onSuccess: () => {
      invalidatePrompts()
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
    }) => promptApi.update(editPrompt!.templateId, { ...v, changeDescription: v.note }),
    onSuccess: () => {
      invalidatePrompts()
      setEditPrompt(null)
      editForm.resetFields()
      message.success(t('skill.prompt.updateSuccess'))
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (templateId: string) => promptApi.delete(templateId),
    onSuccess: () => {
      invalidatePrompts()
      message.success(t('skill.prompt.deleteSuccess'))
    },
  })

  const handleDelete = (p: PromptSummary) => {
    modal.confirm({
      title: t('skill.prompt.deleteConfirm', { name: p.name }),
      okType: 'danger',
      onOk: () => deleteMutation.mutateAsync(p.templateId),
    })
  }

  const handleEditOpen = (p: PromptSummary) => {
    promptApi.get(p.templateId).then((full) => {
      setEditPrompt(full)
      editForm.setFieldsValue({
        name: full.name,
        description: full.description,
        category: full.category,
        content: full.content,
      })
    })
  }

  const handleViewOpen = (p: PromptSummary) => {
    setViewLoading(true)
    setViewPrompt(null)
    promptApi
      .get(p.templateId)
      .then((full) => setViewPrompt(full))
      .catch(() => message.error(t('skill.prompt.loadError')))
      .finally(() => setViewLoading(false))
  }

  const copyContent = (templateId: string) => {
    promptApi.get(templateId).then((p) => {
      navigator.clipboard
        .writeText(p.content)
        .then(() => message.success(t('skill.prompt.copySuccess')))
    })
  }

  const records = data?.records ?? []

  return (
    <div className={styles.root}>
      <div className={styles.pageHeader}>
        <div className={styles.topBar}>
          <div className={styles.filters}>
            <Input
              prefix={<SearchOutlined />}
              placeholder={t('skill.prompt.searchPlaceholder')}
              value={keyword}
              onChange={(e) => {
                setKeyword(e.target.value)
                setCurrent(1)
              }}
              style={{ width: 220 }}
              allowClear
            />
            <Select
              placeholder={t('skill.prompt.fieldCategory')}
              allowClear
              value={category || undefined}
              onChange={(v) => {
                setCategory(v ?? '')
                setCurrent(1)
              }}
              style={{ width: 160 }}
              options={CATEGORY_FILTER_OPTIONS}
            />
          </div>
          <PermissionButton permission="prompt:create">
            <GradientButton icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
              {t('skill.prompt.createBtn')}
            </GradientButton>
          </PermissionButton>
        </div>
      </div>

      <div className={styles.body}>
        {isLoading ? (
          <div className={styles.grid}>
            {Array.from({ length: 6 }).map((_, i) => (
              <div key={i} className={styles.skeleton} />
            ))}
          </div>
        ) : isError ? (
          <EmptyState title={t('skill.prompt.loadError')} />
        ) : !records.length ? (
          <EmptyState
            title={t('skill.prompt.empty')}
            description={t('skill.prompt.searchPlaceholder')}
            action={
              <PermissionButton permission="prompt:create">
                <GradientButton icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
                  {t('skill.prompt.createBtn')}
                </GradientButton>
              </PermissionButton>
            }
          />
        ) : (
          <div className={styles.grid}>
            {records.map((p: PromptSummary, i: number) => {
              const accent = CATEGORY_ACCENT[p.category] ?? '#8c8c8c'
              return (
                <motion.div
                  key={p.id}
                  initial={{ opacity: 0, y: 14 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: i * 0.04 }}
                >
                  <GradientCard className={styles.card}>
                    <div className={styles.cardHeader}>
                      <div className={styles.cardIconWrap}>
                        <FileTextOutlined />
                      </div>
                      <Tag color={CATEGORY_COLORS[p.category]} style={{ fontSize: 12 }}>
                        {p.category}
                      </Tag>
                    </div>

                    <h4 className={styles.cardName}>{p.name}</h4>
                    {p.description && <p className={styles.cardDesc}>{p.description}</p>}

                    <div className={styles.cardMeta}>
                      <span
                        className={styles.metaItem}
                        style={{ color: accent, background: `${accent}1a` }}
                      >
                        v{p.version}
                      </span>
                      <span className={styles.metaItem}>
                        <ThunderboltOutlined />{' '}
                        {t('skill.prompt.usageCount', { count: p.useCount ?? 0 })}
                      </span>
                    </div>

                    <div className={styles.cardActions} onClick={(e) => e.stopPropagation()}>
                      <FavoriteButton
                        type="prompt"
                        targetId={p.templateId}
                        title={p.name}
                        description={p.description}
                        className={styles.btnGold}
                      />
                      <Tooltip title={t('skill.prompt.viewBtn')}>
                        <Button
                          size="small"
                          icon={<EyeOutlined />}
                          className={styles.btnTeal}
                          onClick={() => handleViewOpen(p)}
                        />
                      </Tooltip>
                      <Tooltip title={t('skill.prompt.copyBtn')}>
                        <Button
                          size="small"
                          icon={<CopyOutlined />}
                          className={styles.btnBlue}
                          onClick={() => copyContent(p.templateId)}
                        />
                      </Tooltip>
                      <Tooltip title={t('skill.prompt.editBtn')}>
                        <Button
                          size="small"
                          icon={<EditOutlined />}
                          className={styles.btnGray}
                          onClick={() => handleEditOpen(p)}
                        />
                      </Tooltip>
                      <Tooltip title={t('skill.prompt.historyBtn')}>
                        <Button
                          size="small"
                          icon={<HistoryOutlined />}
                          className={styles.btnPurple}
                          onClick={() => setHistoryPromptId(p.templateId)}
                        />
                      </Tooltip>
                      <PermissionButton permission="prompt:delete" mode="hide">
                        <Tooltip title={t('common.delete')}>
                          <Button
                            size="small"
                            icon={<DeleteOutlined />}
                            className={styles.btnRed}
                            onClick={() => handleDelete(p)}
                          />
                        </Tooltip>
                      </PermissionButton>
                    </div>
                  </GradientCard>
                </motion.div>
              )
            })}
          </div>
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
              <Select options={CATEGORY_SELECT_OPTIONS} />
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
              <Select options={CATEGORY_SELECT_OPTIONS} />
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

      {/* View Modal (read-only) */}
      <Modal
        title={t('skill.prompt.viewModalTitle', { name: viewPrompt?.name ?? '' })}
        open={viewLoading || !!viewPrompt}
        onCancel={() => {
          setViewPrompt(null)
          setViewLoading(false)
        }}
        footer={[
          <Button
            key="copy"
            icon={<CopyOutlined />}
            disabled={!viewPrompt}
            onClick={() =>
              viewPrompt &&
              navigator.clipboard
                .writeText(viewPrompt.content)
                .then(() => message.success(t('skill.prompt.copySuccess')))
            }
          >
            {t('skill.prompt.copyBtn')}
          </Button>,
          <Button key="close" onClick={() => setViewPrompt(null)}>
            {t('common.back')}
          </Button>,
        ]}
        destroyOnClose
        width={720}
      >
        {viewLoading || !viewPrompt ? (
          <div style={{ display: 'flex', justifyContent: 'center', padding: 40 }}>
            <Spin />
          </div>
        ) : (
          <div className={styles.viewBody}>
            <div className={styles.viewHeader}>
              {viewPrompt.category && (
                <Tag color={CATEGORY_COLORS[viewPrompt.category]}>{viewPrompt.category}</Tag>
              )}
              {viewPrompt.description && (
                <span className={styles.viewDesc}>{viewPrompt.description}</span>
              )}
            </div>
            <Descriptions size="small" column={2} className={styles.viewMeta}>
              <Descriptions.Item label={t('skill.prompt.viewFieldUsage')}>
                {viewPrompt.useCount ?? 0}
              </Descriptions.Item>
              {viewPrompt.variables && (
                <Descriptions.Item label={t('skill.prompt.viewFieldVars')}>
                  {viewPrompt.variables}
                </Descriptions.Item>
              )}
              {viewPrompt.tags && (
                <Descriptions.Item label={t('skill.prompt.viewFieldTags')}>
                  {viewPrompt.tags}
                </Descriptions.Item>
              )}
              <Descriptions.Item label={t('skill.prompt.viewFieldCreated')}>
                {new Date(viewPrompt.createdAt).toLocaleString()}
              </Descriptions.Item>
              <Descriptions.Item label={t('skill.prompt.viewFieldUpdated')}>
                {new Date(viewPrompt.updatedAt).toLocaleString()}
              </Descriptions.Item>
            </Descriptions>
            <Divider style={{ margin: '8px 0' }} />
            <div className={styles.viewContentLabel}>{t('skill.prompt.fieldContent')}</div>
            <pre className={styles.viewContent}>
              {viewPrompt.content || t('skill.prompt.viewEmptyContent')}
            </pre>
          </div>
        )}
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
                      {new Date(v.snapshotAt).toLocaleString()}
                    </span>
                  </div>
                  {v.changeDescription && (
                    <div className={styles.versionNote}>{v.changeDescription}</div>
                  )}
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
