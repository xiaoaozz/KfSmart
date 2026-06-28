import { useState } from 'react'
import {
  Button,
  Input,
  Tag,
  Tabs,
  Modal,
  Form,
  Select,
  App,
  Empty,
  Tooltip,
  Descriptions,
} from 'antd'
import {
  PlusOutlined,
  SearchOutlined,
  EditOutlined,
  DeleteOutlined,
  PlayCircleOutlined,
  ThunderboltOutlined,
  CheckCircleOutlined,
  StopOutlined,
  CodeOutlined,
} from '@ant-design/icons'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { motion } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import { skillApi } from '@/api/skill'
import type { SkillSummary } from '@/types/skill'
import { GradientCard } from '@/components/base'
import { PermissionButton } from '@/components/business'
import styles from './SkillListPage.module.css'

const CATEGORY_COLORS: Record<string, string> = {
  http: 'blue',
  database: 'green',
  file: 'orange',
  ai: 'purple',
  custom: 'default',
}

export default function SkillListPage() {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const { message, modal } = App.useApp()
  const { t } = useTranslation()

  const CATEGORY_OPTIONS = [
    { label: t('skill.categoryAll'), value: '' },
    { label: t('skill.categoryHttp'), value: 'http' },
    { label: t('skill.categoryDatabase'), value: 'database' },
    { label: t('skill.categoryFile'), value: 'file' },
    { label: t('skill.categoryAi'), value: 'ai' },
    { label: t('skill.categoryCustom'), value: 'custom' },
  ]

  const STATUS_CFG = {
    draft: { color: 'default', label: t('skill.statusDraft'), icon: <EditOutlined /> },
    published: {
      color: 'success',
      label: t('skill.statusPublished'),
      icon: <CheckCircleOutlined />,
    },
    disabled: { color: 'error', label: t('skill.statusDisabled'), icon: <StopOutlined /> },
  }

  const [keyword, setKeyword] = useState('')
  const [category, setCategory] = useState('')
  const [createOpen, setCreateOpen] = useState(false)
  const [testSkill, setTestSkill] = useState<SkillSummary | null>(null)
  const [testArgs, setTestArgs] = useState('')
  const [testResult, setTestResult] = useState('')
  const [testing, setTesting] = useState(false)
  const [createForm] = Form.useForm<{
    name: string
    description?: string
    category: string
    language: string
  }>()

  const { data, isLoading } = useQuery({
    queryKey: ['skills', category, keyword],
    queryFn: () =>
      skillApi.list({ size: 50, keyword: keyword || undefined, category: category || undefined }),
  })

  const createMutation = useMutation({
    mutationFn: (v: { name: string; description?: string; category: string; language: string }) =>
      skillApi.create({
        ...v,
        code: `// ${v.name}\nasync function run(args) {\n  return args;\n}`,
        params: [],
        outputType: 'string',
      }),
    onSuccess: (sk) => {
      qc.invalidateQueries({ queryKey: ['skills'] })
      setCreateOpen(false)
      createForm.resetFields()
      navigate(`/skills/${sk.id}/edit`)
    },
  })

  const publishMutation = useMutation({
    mutationFn: (id: number) => skillApi.publish(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['skills'] })
      message.success(t('skill.publishSuccess'))
    },
  })

  const disableMutation = useMutation({
    mutationFn: (id: number) => skillApi.disable(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['skills'] })
      message.success(t('skill.disableSuccess'))
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => skillApi.delete(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['skills'] })
      message.success(t('skill.deleteSuccess'))
    },
  })

  const handleDelete = (sk: SkillSummary) => {
    modal.confirm({
      title: t('skill.deleteConfirm', { name: sk.name }),
      okType: 'danger',
      onOk: () => deleteMutation.mutateAsync(sk.id),
    })
  }

  const handleTest = async () => {
    if (!testSkill) return
    setTesting(true)
    setTestResult('')
    try {
      let args: Record<string, unknown> = {}
      if (testArgs.trim()) args = JSON.parse(testArgs)
      const res = await skillApi.test(testSkill.id, args)
      setTestResult(t('skill.testOutput', { output: res.output, ms: res.durationMs }))
    } catch (e) {
      setTestResult(e instanceof Error ? e.message : t('skill.testFailed'))
    } finally {
      setTesting(false)
    }
  }

  const tabItems = CATEGORY_OPTIONS.map((opt) => ({ key: opt.value, label: opt.label }))
  const records = data?.records ?? []

  return (
    <div className={styles.root}>
      <div className={styles.topBar}>
        <h2 className={styles.pageTitle}>
          <ThunderboltOutlined /> {t('skill.title')}
        </h2>
        <div className={styles.actions}>
          <Input
            prefix={<SearchOutlined />}
            placeholder={t('skill.searchPlaceholder')}
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            style={{ width: 200 }}
            allowClear
          />
          <PermissionButton permission="skill:create">
            <Button
              type="primary"
              icon={<PlusOutlined />}
              style={{ background: 'var(--kf-accent-gradient-r)', border: 'none' }}
              onClick={() => setCreateOpen(true)}
            >
              {t('skill.createBtn')}
            </Button>
          </PermissionButton>
        </div>
      </div>

      <Tabs
        activeKey={category}
        onChange={setCategory}
        items={tabItems}
        style={{ marginBottom: 16 }}
      />

      {isLoading ? (
        <div className={styles.skeletonGrid}>
          {Array.from({ length: 6 }).map((_, i) => (
            <div key={i} className={styles.skeleton} />
          ))}
        </div>
      ) : !records.length ? (
        <Empty description={t('skill.empty')} />
      ) : (
        <div className={styles.grid}>
          {records.map((sk: SkillSummary, i: number) => (
            <motion.div
              key={sk.id}
              initial={{ opacity: 0, y: 14 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: i * 0.04 }}
            >
              <GradientCard featured={sk.status === 'published'} className={styles.card}>
                <div className={styles.cardHeader}>
                  <CodeOutlined style={{ color: 'var(--kf-primary)' }} />
                  <span className={styles.cardName}>{sk.name}</span>
                  <Tag color={STATUS_CFG[sk.status].color} icon={STATUS_CFG[sk.status].icon}>
                    {STATUS_CFG[sk.status].label}
                  </Tag>
                </div>
                <div className={styles.cardMeta}>
                  <Tag color={CATEGORY_COLORS[sk.category]}>{sk.category}</Tag>
                  <Tag color="default">{sk.language}</Tag>
                  <span className={styles.runCount}>
                    <PlayCircleOutlined /> {sk.runCount}
                  </span>
                </div>
                {sk.description && <p className={styles.cardDesc}>{sk.description}</p>}
                <div className={styles.cardActions}>
                  <Button
                    size="small"
                    icon={<EditOutlined />}
                    onClick={() => navigate(`/skills/${sk.id}/edit`)}
                  >
                    {t('skill.editor.saveBtn') /* reuse common edit label */}
                  </Button>
                  <Button
                    size="small"
                    icon={<PlayCircleOutlined />}
                    onClick={() => {
                      setTestSkill(sk)
                      setTestArgs('')
                      setTestResult('')
                    }}
                  >
                    {t('common.test')}
                  </Button>
                  {sk.status !== 'published' ? (
                    <PermissionButton permission="skill:publish">
                      <Button
                        size="small"
                        type="primary"
                        style={{ background: 'var(--kf-accent-gradient-r)', border: 'none' }}
                        onClick={() => publishMutation.mutate(sk.id)}
                      >
                        {t('common.publish')}
                      </Button>
                    </PermissionButton>
                  ) : (
                    <PermissionButton permission="skill:publish">
                      <Button size="small" danger onClick={() => disableMutation.mutate(sk.id)}>
                        {t('common.disable')}
                      </Button>
                    </PermissionButton>
                  )}
                  <Tooltip title={t('common.delete')}>
                    <PermissionButton permission="skill:delete">
                      <Button
                        size="small"
                        danger
                        icon={<DeleteOutlined />}
                        onClick={() => handleDelete(sk)}
                      />
                    </PermissionButton>
                  </Tooltip>
                </div>
              </GradientCard>
            </motion.div>
          ))}
        </div>
      )}

      {/* Create Modal */}
      <Modal
        title={t('skill.createModalTitle')}
        open={createOpen}
        onCancel={() => {
          setCreateOpen(false)
          createForm.resetFields()
        }}
        onOk={() => createForm.submit()}
        confirmLoading={createMutation.isPending}
        destroyOnClose
      >
        <Form form={createForm} layout="vertical" onFinish={(v) => createMutation.mutate(v)}>
          <Form.Item name="name" label={t('skill.fieldName')} rules={[{ required: true }]}>
            <Input placeholder={t('skill.namePlaceholder')} />
          </Form.Item>
          <Form.Item name="description" label={t('skill.fieldDesc')}>
            <Input.TextArea rows={2} placeholder={t('skill.descPlaceholder')} />
          </Form.Item>
          <Form.Item
            name="category"
            label={t('skill.fieldCategory')}
            initialValue="custom"
            rules={[{ required: true }]}
          >
            <Select options={CATEGORY_OPTIONS.slice(1)} />
          </Form.Item>
          <Form.Item
            name="language"
            label={t('skill.fieldLanguage')}
            initialValue="javascript"
            rules={[{ required: true }]}
          >
            <Select
              options={[
                { label: 'JavaScript', value: 'javascript' },
                { label: 'Python', value: 'python' },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* Test Modal */}
      <Modal
        title={t('skill.testModalTitle', { name: testSkill?.name })}
        open={!!testSkill}
        onCancel={() => setTestSkill(null)}
        onOk={handleTest}
        confirmLoading={testing}
        okText={t('common.run')}
        destroyOnClose
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <div>
            <div style={{ fontSize: 12, color: 'var(--kf-muted-foreground)', marginBottom: 4 }}>
              {t('skill.testArgsLabel')}
            </div>
            <Input.TextArea
              value={testArgs}
              onChange={(e) => setTestArgs(e.target.value)}
              rows={4}
              placeholder='{"key": "value"}'
              style={{ fontFamily: 'var(--kf-font-mono)', fontSize: 12 }}
            />
          </div>
          {testResult && (
            <Descriptions size="small" column={1} bordered>
              <Descriptions.Item label={t('skill.testResultLabel')}>
                <pre
                  style={{
                    margin: 0,
                    fontFamily: 'var(--kf-font-mono)',
                    fontSize: 12,
                    whiteSpace: 'pre-wrap',
                  }}
                >
                  {testResult}
                </pre>
              </Descriptions.Item>
            </Descriptions>
          )}
        </div>
      </Modal>
    </div>
  )
}
