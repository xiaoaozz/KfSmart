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
import { skillApi } from '@/api/skill'
import type { SkillSummary } from '@/types/skill'
import { GradientCard } from '@/components/base'
import { PermissionButton } from '@/components/business'
import styles from './SkillListPage.module.css'

const CATEGORY_OPTIONS = [
  { label: '全部', value: '' },
  { label: 'HTTP 请求', value: 'http' },
  { label: '数据库', value: 'database' },
  { label: '文件处理', value: 'file' },
  { label: 'AI 工具', value: 'ai' },
  { label: '自定义', value: 'custom' },
]

const CATEGORY_COLORS: Record<string, string> = {
  http: 'blue',
  database: 'green',
  file: 'orange',
  ai: 'purple',
  custom: 'default',
}

const STATUS_CFG = {
  draft: { color: 'default', label: '草稿', icon: <EditOutlined /> },
  published: { color: 'success', label: '已发布', icon: <CheckCircleOutlined /> },
  disabled: { color: 'error', label: '已停用', icon: <StopOutlined /> },
}

export default function SkillListPage() {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const { message, modal } = App.useApp()

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
      message.success('已发布')
    },
  })

  const disableMutation = useMutation({
    mutationFn: (id: number) => skillApi.disable(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['skills'] })
      message.success('已停用')
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => skillApi.delete(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['skills'] })
      message.success('已删除')
    },
  })

  const handleDelete = (sk: SkillSummary) => {
    modal.confirm({
      title: `删除技能「${sk.name}」？`,
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
      if (testArgs.trim()) {
        args = JSON.parse(testArgs)
      }
      const res = await skillApi.test(testSkill.id, args)
      setTestResult(`输出：${res.output}\n耗时：${res.durationMs}ms`)
    } catch (e) {
      setTestResult(e instanceof Error ? e.message : '测试失败')
    } finally {
      setTesting(false)
    }
  }

  const tabItems = CATEGORY_OPTIONS.map((opt) => ({
    key: opt.value,
    label: opt.label,
  }))

  const records = data?.records ?? []

  return (
    <div className={styles.root}>
      <div className={styles.topBar}>
        <h2 className={styles.pageTitle}>
          <ThunderboltOutlined /> 技能库
        </h2>
        <div className={styles.actions}>
          <Input
            prefix={<SearchOutlined />}
            placeholder="搜索技能…"
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
              新建技能
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
        <Empty description="暂无技能" />
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
                    编辑
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
                    测试
                  </Button>
                  {sk.status !== 'published' ? (
                    <PermissionButton permission="skill:publish">
                      <Button
                        size="small"
                        type="primary"
                        style={{ background: 'var(--kf-accent-gradient-r)', border: 'none' }}
                        onClick={() => publishMutation.mutate(sk.id)}
                      >
                        发布
                      </Button>
                    </PermissionButton>
                  ) : (
                    <PermissionButton permission="skill:publish">
                      <Button size="small" danger onClick={() => disableMutation.mutate(sk.id)}>
                        停用
                      </Button>
                    </PermissionButton>
                  )}
                  <Tooltip title="删除">
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
        title="新建技能"
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
          <Form.Item name="name" label="技能名称" rules={[{ required: true }]}>
            <Input placeholder="例：HTTP 请求工具" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} placeholder="描述此技能的用途" />
          </Form.Item>
          <Form.Item
            name="category"
            label="分类"
            initialValue="custom"
            rules={[{ required: true }]}
          >
            <Select options={CATEGORY_OPTIONS.slice(1)} />
          </Form.Item>
          <Form.Item
            name="language"
            label="语言"
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
        title={`测试技能 — ${testSkill?.name}`}
        open={!!testSkill}
        onCancel={() => setTestSkill(null)}
        onOk={handleTest}
        confirmLoading={testing}
        okText="运行"
        destroyOnClose
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <div>
            <div style={{ fontSize: 12, color: 'var(--kf-muted-foreground)', marginBottom: 4 }}>
              参数 JSON（可选）
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
              <Descriptions.Item label="结果">
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
