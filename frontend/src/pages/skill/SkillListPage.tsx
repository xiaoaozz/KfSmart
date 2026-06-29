import { useState } from 'react'
import { Button, Input, Tag, Select, Modal, Form, App, Tooltip, Descriptions } from 'antd'
import {
  PlusOutlined,
  SearchOutlined,
  EditOutlined,
  DeleteOutlined,
  PlayCircleOutlined,
  CheckCircleOutlined,
  StopOutlined,
  CodeOutlined,
} from '@ant-design/icons'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { motion } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import { skillApi } from '@/api/skill'
import type { SkillSummary, SkillTestResult } from '@/types/skill'
import { GradientCard, GradientButton } from '@/components/base'
import { PermissionButton, PageBar, EmptyState, FavoriteButton } from '@/components/business'
import styles from './SkillListPage.module.css'

const CATEGORY_COLORS = ['#1677ff', '#52c41a', '#fa8c16', '#722ed1', '#13c2c2', '#eb2f96']

function categoryColor(category: string): string {
  let hash = 0
  for (let i = 0; i < category.length; i++) {
    hash = (hash * 31 + category.charCodeAt(i)) | 0
  }
  return CATEGORY_COLORS[Math.abs(hash) % CATEGORY_COLORS.length]
}

export default function SkillListPage() {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const { message, modal } = App.useApp()
  const { t } = useTranslation()

  const STATUS_CFG: Record<string, { color: string; label: string; icon: React.ReactNode }> = {
    草稿: { color: 'default', label: t('skill.statusDraft'), icon: <EditOutlined /> },
    已发布: { color: 'success', label: t('skill.statusPublished'), icon: <CheckCircleOutlined /> },
    已停用: { color: 'error', label: t('skill.statusDisabled'), icon: <StopOutlined /> },
  }

  const [keyword, setKeyword] = useState('')
  const [category, setCategory] = useState('')
  const [current, setCurrent] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [createOpen, setCreateOpen] = useState(false)
  const [testSkill, setTestSkill] = useState<SkillSummary | null>(null)
  const [testArgs, setTestArgs] = useState('')
  const [testResult, setTestResult] = useState<SkillTestResult | null>(null)
  const [testing, setTesting] = useState(false)
  const [createForm] = Form.useForm<{
    name: string
    description?: string
    category: string
  }>()

  const { data, isLoading } = useQuery({
    queryKey: ['skills', category, keyword, current, pageSize],
    queryFn: () =>
      skillApi.list({
        page: current,
        size: pageSize,
        keyword: keyword || undefined,
        category: category || undefined,
      }),
  })

  const categoryOptions = (() => {
    const cats = (data?.records ?? [])
      .map((s) => s.category)
      .filter((c, i, arr) => c && arr.indexOf(c) === i)
      .sort()
    return [
      { label: t('skill.categoryAll'), value: '' },
      ...cats.map((c) => ({ label: c, value: c })),
    ]
  })()

  const createMutation = useMutation({
    mutationFn: (v: { name: string; description?: string; category: string }) =>
      skillApi.create({
        ...v,
        instruction: `// ${v.name}\n// 在此编写技能逻辑`,
      }),
    onSuccess: (sk) => {
      qc.invalidateQueries({ queryKey: ['skills'] })
      setCreateOpen(false)
      createForm.resetFields()
      navigate(`/skills/${sk.skillId}/edit`)
    },
  })

  const publishMutation = useMutation({
    mutationFn: (skillId: string) => skillApi.publish(skillId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['skills'] })
      message.success(t('skill.publishSuccess'))
    },
  })

  const toggleMutation = useMutation({
    mutationFn: (skillId: string) => skillApi.toggleStatus(skillId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['skills'] })
      message.success(t('skill.disableSuccess'))
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (skillId: string) => skillApi.delete(skillId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['skills'] })
      message.success(t('skill.deleteSuccess'))
    },
  })

  const handleDelete = (sk: SkillSummary) => {
    modal.confirm({
      title: t('skill.deleteConfirm', { name: sk.name }),
      okType: 'danger',
      onOk: () => deleteMutation.mutateAsync(sk.skillId),
    })
  }

  const handleTest = async () => {
    if (!testSkill) return
    setTesting(true)
    setTestResult(null)
    try {
      let args: Record<string, unknown> = {}
      if (testArgs.trim()) args = JSON.parse(testArgs)
      const res = await skillApi.test(testSkill.skillId, args)
      setTestResult(res)
    } catch (e) {
      message.error(e instanceof Error ? e.message : t('skill.testFailed'))
    } finally {
      setTesting(false)
    }
  }

  const records = data?.records ?? []

  return (
    <div className={styles.root}>
      <div className={styles.pageHeader}>
        <div className={styles.topBar}>
          <div className={styles.filters}>
            <Input
              prefix={<SearchOutlined />}
              placeholder={t('skill.searchPlaceholder')}
              value={keyword}
              onChange={(e) => {
                setKeyword(e.target.value)
                setCurrent(1)
              }}
              style={{ width: 220 }}
              allowClear
            />
            <Select
              placeholder={t('skill.fieldCategory')}
              allowClear
              value={category || undefined}
              onChange={(v) => {
                setCategory(v ?? '')
                setCurrent(1)
              }}
              style={{ width: 160 }}
              options={categoryOptions}
            />
          </div>
          <PermissionButton permission="skill:create">
            <GradientButton icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
              {t('skill.createBtn')}
            </GradientButton>
          </PermissionButton>
        </div>
      </div>

      <div className={styles.body}>
        {isLoading ? (
          <div className={styles.skeletonGrid}>
            {Array.from({ length: 6 }).map((_, i) => (
              <div key={i} className={styles.skeleton} />
            ))}
          </div>
        ) : !records.length ? (
          <EmptyState
            title={t('skill.empty')}
            description={t('skill.searchPlaceholder')}
            action={
              <PermissionButton permission="skill:create">
                <GradientButton icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
                  {t('skill.createBtn')}
                </GradientButton>
              </PermissionButton>
            }
          />
        ) : (
          <div className={styles.grid}>
            {records.map((sk: SkillSummary, i: number) => {
              const statusCfg = STATUS_CFG[sk.status] ?? {
                color: 'default',
                label: sk.status,
                icon: <CodeOutlined />,
              }
              const tags = (sk.tags ?? '').split(',').filter(Boolean)
              const catColor = sk.category ? categoryColor(sk.category) : ''
              return (
                <motion.div
                  key={sk.id}
                  initial={{ opacity: 0, y: 14 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: i * 0.04 }}
                >
                  <GradientCard featured={sk.status === '已发布'} className={styles.card}>
                    <div className={styles.cardHeader}>
                      <div className={styles.cardIconWrap}>
                        <CodeOutlined />
                      </div>
                      <Tag color={statusCfg.color} icon={statusCfg.icon} style={{ fontSize: 12 }}>
                        {statusCfg.label}
                      </Tag>
                    </div>

                    <h4 className={styles.cardName}>{sk.name}</h4>
                    {sk.description && <p className={styles.cardDesc}>{sk.description}</p>}

                    <div className={styles.cardMeta}>
                      {sk.category && (
                        <span
                          className={styles.metaItem}
                          style={{ color: catColor, background: `${catColor}1a` }}
                        >
                          {sk.category}
                        </span>
                      )}
                      <span className={styles.metaItem}>v{sk.version}</span>
                      {tags.map((tag) => (
                        <span key={tag} className={styles.metaItem}>
                          #{tag}
                        </span>
                      ))}
                      <span className={styles.metaItem}>
                        <PlayCircleOutlined /> {sk.callCount}
                      </span>
                    </div>

                    <div className={styles.cardActions} onClick={(e) => e.stopPropagation()}>
                      <Tooltip title={t('common.test')}>
                        <Button
                          size="small"
                          icon={<PlayCircleOutlined />}
                          className={styles.btnBlue}
                          onClick={() => {
                            setTestSkill(sk)
                            setTestArgs('')
                            setTestResult(null)
                          }}
                        />
                      </Tooltip>
                      <Tooltip title={t('common.edit')}>
                        <Button
                          size="small"
                          icon={<EditOutlined />}
                          className={styles.btnGray}
                          onClick={() => navigate(`/skills/${sk.skillId}/edit`)}
                        />
                      </Tooltip>
                      {sk.status !== '已发布' ? (
                        <PermissionButton permission="skill:publish" mode="hide">
                          <Tooltip title={t('common.publish')}>
                            <Button
                              size="small"
                              icon={<CheckCircleOutlined />}
                              className={styles.btnGreen}
                              onClick={() => publishMutation.mutate(sk.skillId)}
                            />
                          </Tooltip>
                        </PermissionButton>
                      ) : (
                        <PermissionButton permission="skill:publish" mode="hide">
                          <Tooltip title={t('common.disable')}>
                            <Button
                              size="small"
                              icon={<StopOutlined />}
                              className={styles.btnOrange}
                              onClick={() => toggleMutation.mutate(sk.skillId)}
                            />
                          </Tooltip>
                        </PermissionButton>
                      )}
                      <PermissionButton permission="skill:delete" mode="hide">
                        <Tooltip title={t('common.delete')}>
                          <Button
                            size="small"
                            icon={<DeleteOutlined />}
                            className={styles.btnRed}
                            onClick={() => handleDelete(sk)}
                          />
                        </Tooltip>
                      </PermissionButton>
                      <FavoriteButton
                        type="skill"
                        targetId={sk.skillId}
                        title={sk.name}
                        description={sk.description}
                        className={styles.btnGold}
                      />
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
            initialValue="通用技能"
            rules={[{ required: true }]}
          >
            <Input placeholder={t('skill.fieldCategory')} />
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
        width={640}
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
                  {testResult.message}
                </pre>
              </Descriptions.Item>
              {testResult.executionPlan && (
                <Descriptions.Item label="执行计划">
                  <pre style={{ margin: 0, fontSize: 12, whiteSpace: 'pre-wrap' }}>
                    {testResult.executionPlan.join('\n')}
                  </pre>
                </Descriptions.Item>
              )}
              {testResult.warnings && testResult.warnings.length > 0 && (
                <Descriptions.Item label="警告">
                  {testResult.warnings.map((w, idx) => (
                    <div key={idx} style={{ fontSize: 12, color: 'var(--kf-warning)' }}>
                      {w}
                    </div>
                  ))}
                </Descriptions.Item>
              )}
            </Descriptions>
          )}
        </div>
      </Modal>
    </div>
  )
}
