import { useState, useRef, useEffect, useMemo } from 'react'
import {
  Form,
  Input,
  Select,
  Slider,
  InputNumber,
  Button,
  App,
  Spin,
  Space,
  Tabs,
  Tag,
  Modal,
  Empty,
} from 'antd'
import {
  SaveOutlined,
  SendOutlined,
  ArrowLeftOutlined,
  RobotOutlined,
  RocketOutlined,
  SettingOutlined,
  ToolOutlined,
  AppstoreOutlined,
  CheckOutlined,
} from '@ant-design/icons'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate, useParams } from 'react-router-dom'
import { motion } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import { agentApi } from '@/api/agent'
import { kbApi } from '@/api/knowledge-base'
import { skillApi, promptApi, modelApi } from '@/api/skill'
import type { AgentFormValues } from '@/types/agent'
import type { Prompt } from '@/types/skill'
import styles from './AgentEditorPage.module.css'

interface ChatMessage {
  role: 'user' | 'agent'
  content: string
}

const DEFAULT_TEMPERATURE = 0.7
const DEFAULT_MAX_TOKENS = 2048

/** Parse a comma-separated ID string (backend format) into a number array (UI format). */
const parseIds = (str?: string | null): number[] => {
  if (!str?.trim()) return []
  return str
    .split(',')
    .map((s) => parseInt(s.trim(), 10))
    .filter((n) => !isNaN(n))
}

export default function AgentEditorPage() {
  const { id } = useParams<{ id?: string }>()
  const isEdit = !!id
  const navigate = useNavigate()
  const qc = useQueryClient()
  const { message, modal } = App.useApp()
  const { t } = useTranslation()
  const [form] = Form.useForm<AgentFormValues>()
  const [testInput, setTestInput] = useState('')
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [testing, setTesting] = useState(false)
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const hasAppliedDefaultModel = useRef(false)

  // template picker state
  const [templateOpen, setTemplateOpen] = useState(false)
  const [templateSearch, setTemplateSearch] = useState('')
  const [templateCategory, setTemplateCategory] = useState('')

  // variable fill state
  const [varFillOpen, setVarFillOpen] = useState(false)
  const [varFillTemplate, setVarFillTemplate] = useState<Prompt | null>(null)
  const [varValues, setVarValues] = useState<Record<string, string>>({})

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  const { isLoading, data: currentAgent } = useQuery({
    queryKey: ['agents', id],
    queryFn: () => agentApi.get(Number(id)),
    enabled: isEdit,
    gcTime: 0,
    select: (agent) => {
      form.setFieldsValue({
        name: agent.name,
        description: agent.description,
        systemPrompt: agent.systemPrompt,
        models: agent.models ?? '',
        temperature: agent.temperature ?? DEFAULT_TEMPERATURE,
        maxTokens: agent.maxTokens ?? DEFAULT_MAX_TOKENS,
        knowledgeBaseIds: parseIds(agent.knowledgeBases),
        skillIds: parseIds(agent.skillRefs),
      })
      return agent
    },
  })

  const { data: kbData } = useQuery({
    queryKey: ['knowledge-bases', 'all'],
    queryFn: () => kbApi.list({ size: 100 }),
  })

  const { data: skillData } = useQuery({
    queryKey: ['skills', 'all'],
    queryFn: () => skillApi.list({ size: 100 }),
  })

  const { data: modelConfigs } = useQuery({
    queryKey: ['models'],
    queryFn: () => modelApi.list(),
    staleTime: 60_000,
  })

  const activeModelOptions = useMemo(() => {
    const configs = modelConfigs ?? []
    const active = configs.filter((m) => m.active)
    return (active.length ? active : configs).map((m) => ({
      label: m.name || m.modelName,
      value: m.modelName,
    }))
  }, [modelConfigs])

  useEffect(() => {
    if (!isEdit && !hasAppliedDefaultModel.current && modelConfigs?.length) {
      hasAppliedDefaultModel.current = true
      const first = modelConfigs.find((m) => m.active) ?? modelConfigs[0]
      if (first) {
        form.setFieldsValue({
          models: first.modelName,
          temperature: first.temperature ?? DEFAULT_TEMPERATURE,
          maxTokens: first.maxTokens ?? DEFAULT_MAX_TOKENS,
        })
      }
    }
  }, [modelConfigs, isEdit, form])

  const temperatureValue = Form.useWatch('temperature', form)
  const maxTokensValue = Form.useWatch('maxTokens', form)

  const { data: promptData, isLoading: promptLoading } = useQuery({
    queryKey: ['prompts-for-template'],
    queryFn: () => promptApi.list({ size: 200 }),
    enabled: templateOpen,
    staleTime: 60_000,
  })

  const TEMPLATE_CATEGORIES = [
    { value: '', label: t('skill.prompt.categoryAll') },
    { value: 'chat', label: t('skill.prompt.categoryChat') },
    { value: 'summary', label: t('skill.prompt.categorySummary') },
    { value: 'translation', label: t('skill.prompt.categoryTranslation') },
    { value: 'code', label: t('skill.prompt.categoryCode') },
    { value: 'analysis', label: t('skill.prompt.categoryAnalysis') },
    { value: 'custom', label: t('skill.prompt.categoryCustom') },
  ]

  const CATEGORY_COLORS: Record<string, string> = {
    chat: 'blue',
    summary: 'cyan',
    translation: 'green',
    code: 'purple',
    analysis: 'orange',
    custom: 'default',
  }

  const filteredTemplates = useMemo(() => {
    const list = (promptData?.records ?? []) as Prompt[]
    return list.filter((p) => {
      const matchSearch =
        !templateSearch || p.name.toLowerCase().includes(templateSearch.toLowerCase())
      const matchCat = !templateCategory || p.category === templateCategory
      return matchSearch && matchCat
    })
  }, [promptData, templateSearch, templateCategory])

  const saveMutation = useMutation({
    mutationFn: (values: AgentFormValues) =>
      isEdit ? agentApi.update(Number(id), values) : agentApi.create(values),
    onSuccess: (agent) => {
      qc.invalidateQueries({ queryKey: ['agents'] })
      message.success(isEdit ? t('agent.editor.saveSuccess') : t('agent.editor.createSuccess'))
      if (!isEdit) navigate(`/agents/${agent.id}/edit`, { replace: true })
    },
  })

  const publishMutation = useMutation({
    mutationFn: () => agentApi.publish(Number(id)),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['agents', id] })
      qc.invalidateQueries({ queryKey: ['agents'] })
      message.success(t('agent.publishSuccess'))
    },
  })

  const handleTest = async () => {
    if (!testInput.trim() || !isEdit) return
    const userMsg = testInput.trim()
    setTestInput('')
    setMessages((prev) => [...prev, { role: 'user', content: userMsg }])
    setTesting(true)
    try {
      const result = await agentApi.testRun(Number(id), userMsg)
      setMessages((prev) => [
        ...prev,
        { role: 'agent', content: result.answer ?? t('agent.editor.testFailed') },
      ])
    } catch {
      setMessages((prev) => [...prev, { role: 'agent', content: t('agent.editor.testFailed') }])
    } finally {
      setTesting(false)
    }
  }

  const extractVariables = (content: string): string[] => {
    const vars = [...content.matchAll(/\{\{([^}]+)\}\}/g)].map((m) => m[1].trim())
    return [...new Set(vars)]
  }

  const substituteVariables = (content: string, values: Record<string, string>): string =>
    content.replace(/\{\{([^}]+)\}\}/g, (_, name) => values[name.trim()] ?? `{{${name.trim()}}}`)

  const previewContent = useMemo(
    () => (varFillTemplate ? substituteVariables(varFillTemplate.content ?? '', varValues) : ''),
    [varFillTemplate, varValues],
  )

  const closeTemplateModal = () => {
    setTemplateOpen(false)
    setTemplateSearch('')
    setTemplateCategory('')
  }

  const applyTemplate = (p: Prompt) => {
    const vars = extractVariables(p.content ?? '')
    if (vars.length > 0) {
      setVarFillTemplate(p)
      setVarValues(Object.fromEntries(vars.map((v) => [v, ''])))
      setVarFillOpen(true)
      closeTemplateModal()
      return
    }
    const current = form.getFieldValue('systemPrompt') as string | undefined
    const doApply = () => {
      form.setFieldValue('systemPrompt', p.content ?? '')
      closeTemplateModal()
      message.success(t('agent.editor.templateApplied'))
    }
    if (current?.trim()) {
      modal.confirm({
        title: t('agent.editor.templateApplyConfirm'),
        okText: t('agent.editor.templateApply'),
        onOk: doApply,
      })
    } else {
      doApply()
    }
  }

  const confirmVarFill = () => {
    if (!varFillTemplate) return
    const current = form.getFieldValue('systemPrompt') as string | undefined
    const doApply = () => {
      form.setFieldValue('systemPrompt', previewContent)
      setVarFillOpen(false)
      setVarFillTemplate(null)
      message.success(t('agent.editor.templateApplied'))
    }
    if (current?.trim()) {
      modal.confirm({
        title: t('agent.editor.templateApplyConfirm'),
        okText: t('agent.editor.templateApply'),
        onOk: doApply,
      })
    } else {
      doApply()
    }
  }

  const statusColorMap: Record<string, string> = {
    published: 'green',
    disabled: 'red',
    draft: 'default',
  }

  const statusLabelKey: Record<string, string> = {
    published: 'agent.statusPublished',
    disabled: 'agent.statusDisabled',
    draft: 'agent.statusDraft',
  }

  if (isEdit && isLoading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: 80 }}>
        <Spin />
      </div>
    )
  }

  const configTabs = [
    {
      key: 'prompt',
      label: (
        <span>
          <RobotOutlined /> {t('agent.editor.tabPrompt')}
        </span>
      ),
      children: (
        <div className={styles.promptTabContent}>
          <Form.Item
            name="systemPrompt"
            className={styles.promptFormItem}
            label={
              <div className={styles.promptLabelRow}>
                <span>{t('agent.editor.fieldPrompt')}</span>
                <Button
                  size="small"
                  icon={<AppstoreOutlined />}
                  onClick={(e) => {
                    e.preventDefault()
                    setTemplateOpen(true)
                  }}
                >
                  {t('agent.editor.templateBtn')}
                </Button>
              </div>
            }
            rules={[{ required: true, message: t('agent.editor.promptRequired') }]}
          >
            <Input.TextArea
              placeholder={t('agent.editor.promptPlaceholder')}
              style={{ fontFamily: 'var(--kf-font-mono)', fontSize: 13, resize: 'none' }}
            />
          </Form.Item>
        </div>
      ),
    },
    {
      key: 'model',
      label: (
        <span>
          <SettingOutlined /> {t('agent.editor.tabModel')}
        </span>
      ),
      children: (
        <div className={styles.tabContent}>
          <Form.Item name="models" label={t('agent.editor.fieldModel')}>
            <Select options={activeModelOptions} placeholder={t('agent.editor.modelPlaceholder')} />
          </Form.Item>
          <Form.Item
            name="temperature"
            label={
              <span>
                {t('agent.editor.fieldTemp')}
                <span
                  style={{
                    marginLeft: 8,
                    fontWeight: 400,
                    color: 'var(--kf-muted-foreground)',
                    fontFamily: 'var(--kf-font-mono)',
                    fontSize: 12,
                  }}
                >
                  {(temperatureValue ?? DEFAULT_TEMPERATURE).toFixed(2)}
                </span>
              </span>
            }
          >
            <Slider
              min={0}
              max={2}
              step={0.05}
              tooltip={{ formatter: (v) => v?.toFixed(2) }}
              marks={{
                0: t('agent.editor.tempStrict'),
                1: t('agent.editor.tempBalanced'),
                2: t('agent.editor.tempCreative'),
              }}
            />
          </Form.Item>
          <Form.Item
            name="maxTokens"
            label={
              <span>
                {t('agent.editor.fieldMaxTokens')}
                <span
                  style={{
                    marginLeft: 8,
                    fontWeight: 400,
                    color: 'var(--kf-muted-foreground)',
                    fontFamily: 'var(--kf-font-mono)',
                    fontSize: 12,
                  }}
                >
                  {(maxTokensValue ?? DEFAULT_MAX_TOKENS).toLocaleString()}
                </span>
              </span>
            }
          >
            <InputNumber min={256} max={8192} step={256} style={{ width: '100%' }} />
          </Form.Item>
        </div>
      ),
    },
    {
      key: 'tools',
      label: (
        <span>
          <ToolOutlined /> {t('agent.editor.tabTools')}
        </span>
      ),
      children: (
        <div className={styles.tabContent}>
          <Form.Item name="knowledgeBaseIds" label={t('agent.editor.fieldKb')}>
            <Select
              mode="multiple"
              placeholder={t('agent.editor.kbPlaceholder')}
              options={kbData?.records.map((kb) => ({ label: kb.name, value: kb.id }))}
              allowClear
            />
          </Form.Item>
          <Form.Item name="skillIds" label={t('agent.editor.fieldSkills')}>
            <Select
              mode="multiple"
              placeholder={t('agent.editor.skillsPlaceholder')}
              options={skillData?.records.map((s) => ({ label: s.name, value: s.id }))}
              allowClear
            />
          </Form.Item>
        </div>
      ),
    },
  ]

  return (
    <div className={styles.root}>
      <div className={styles.topBar}>
        <Button icon={<ArrowLeftOutlined />} type="text" onClick={() => navigate('/agents')}>
          {t('agent.editor.backBtn')}
        </Button>
        <h2 className={styles.pageTitle}>
          {isEdit ? t('agent.editor.editTitle') : t('agent.editor.createTitle')}
        </h2>
        <Space>
          <Button
            icon={<SaveOutlined />}
            loading={saveMutation.isPending}
            onClick={() => form.submit()}
          >
            {t('agent.editor.saveBtn')}
          </Button>
          {isEdit && currentAgent?.status !== 'published' ? (
            <Button
              type="primary"
              icon={<RocketOutlined />}
              loading={publishMutation.isPending}
              onClick={() => publishMutation.mutate()}
              style={{ background: 'var(--kf-accent-gradient-r)', border: 'none' }}
            >
              {t('agent.editor.publishBtn')}
            </Button>
          ) : !isEdit ? (
            <Button
              type="primary"
              icon={<SaveOutlined />}
              loading={saveMutation.isPending}
              onClick={() => form.submit()}
              style={{ background: 'var(--kf-accent-gradient-r)', border: 'none' }}
            >
              {t('agent.editor.createBtn')}
            </Button>
          ) : null}
        </Space>
      </div>

      <Form
        form={form}
        layout="vertical"
        onFinish={(v) => saveMutation.mutate(v)}
        initialValues={{
          models: '',
          temperature: DEFAULT_TEMPERATURE,
          maxTokens: DEFAULT_MAX_TOKENS,
          knowledgeBaseIds: [],
          skillIds: [],
        }}
        className={styles.formWrapper}
        onValuesChange={(changed) => {
          if (changed.models !== undefined) {
            const config = (modelConfigs ?? []).find((m) => m.modelName === changed.models)
            if (config) {
              form.setFieldValue('temperature', config.temperature)
              form.setFieldValue('maxTokens', config.maxTokens)
            }
          }
        }}
      >
        <div className={styles.body}>
          {/* Left: Basic Info */}
          <motion.div
            className={styles.leftPanel}
            initial={{ opacity: 0, x: -16 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ duration: 0.3 }}
          >
            <div className={styles.panelHeader}>{t('agent.editor.sectionBasic')}</div>
            <div className={styles.panelBody}>
              <Form.Item
                name="name"
                label={t('agent.editor.fieldName')}
                rules={[{ required: true, message: t('agent.editor.nameRequired') }]}
              >
                <Input placeholder={t('agent.editor.namePlaceholder')} />
              </Form.Item>
              <Form.Item name="description" label={t('agent.editor.fieldDesc')}>
                <Input.TextArea rows={4} placeholder={t('agent.editor.descPlaceholder')} />
              </Form.Item>
              {isEdit && currentAgent && (
                <div className={styles.statusRow}>
                  <span className={styles.statusLabel}>{t('agent.editor.statusLabel')}</span>
                  <Tag color={statusColorMap[currentAgent.status] ?? 'default'}>
                    {t(statusLabelKey[currentAgent.status] ?? 'agent.statusDraft')}
                  </Tag>
                </div>
              )}
            </div>
          </motion.div>

          {/* Middle: Config */}
          <motion.div
            className={styles.configPanel}
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.3, delay: 0.05 }}
          >
            <div className={styles.panelHeader}>{t('agent.editor.sectionConfig')}</div>
            <Tabs
              className={styles.configTabs}
              items={configTabs}
              size="small"
              tabBarStyle={{ padding: '0 16px', margin: 0 }}
            />
          </motion.div>

          {/* Right: Debug */}
          <motion.div
            className={styles.debugPanel}
            initial={{ opacity: 0, x: 16 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ duration: 0.3, delay: 0.1 }}
          >
            <div className={styles.panelHeader}>
              <RobotOutlined />
              {t('agent.editor.debugHeader')}
            </div>
            <div className={styles.debugMessages}>
              {messages.length === 0 && (
                <div className={styles.debugEmpty}>{t('agent.editor.debugEmpty')}</div>
              )}
              {messages.map((msg, i) => (
                <div
                  key={i}
                  className={msg.role === 'user' ? styles.userBubble : styles.agentBubble}
                >
                  <div className={styles.bubbleRole}>
                    {msg.role === 'user'
                      ? t('agent.editor.debugUserLabel')
                      : (currentAgent?.name ?? 'Agent')}
                  </div>
                  <div className={styles.bubbleContent}>{msg.content}</div>
                </div>
              ))}
              {testing && (
                <div className={styles.agentBubble}>
                  <div className={styles.bubbleRole}>{currentAgent?.name ?? 'Agent'}</div>
                  <div className={styles.bubbleContent}>
                    <Spin size="small" />
                  </div>
                </div>
              )}
              <div ref={messagesEndRef} />
            </div>
            <div className={styles.debugInput}>
              {!isEdit && <div className={styles.saveHint}>{t('agent.editor.saveHint')}</div>}
              <Input.TextArea
                value={testInput}
                onChange={(e) => setTestInput(e.target.value)}
                placeholder={
                  isEdit ? t('agent.editor.testPlaceholder') : t('agent.editor.saveHint')
                }
                autoSize={{ minRows: 2, maxRows: 4 }}
                disabled={!isEdit || testing}
                onPressEnter={(e) => {
                  if (!e.shiftKey) {
                    e.preventDefault()
                    handleTest()
                  }
                }}
              />
              <Button
                type="primary"
                icon={<SendOutlined />}
                loading={testing}
                disabled={!isEdit || !testInput.trim()}
                onClick={handleTest}
                style={{ background: 'var(--kf-accent-gradient-r)', border: 'none' }}
                block
              >
                {t('common.send')}
              </Button>
            </div>
          </motion.div>
        </div>
      </Form>

      {/* Template Picker Modal */}
      <Modal
        title={t('agent.editor.templateModalTitle')}
        open={templateOpen}
        onCancel={closeTemplateModal}
        footer={null}
        width={680}
        destroyOnClose
      >
        <div className={styles.templateModalHeader}>
          <Input
            prefix={<AppstoreOutlined style={{ color: 'var(--kf-muted-foreground)' }} />}
            placeholder={t('agent.editor.templateSearchPlaceholder')}
            value={templateSearch}
            onChange={(e) => setTemplateSearch(e.target.value)}
            allowClear
            style={{ flex: 1 }}
          />
        </div>
        <div className={styles.templateCategoryBar}>
          {TEMPLATE_CATEGORIES.map((cat) => (
            <button
              key={cat.value}
              className={`${styles.catBtn} ${templateCategory === cat.value ? styles.catBtnActive : ''}`}
              onClick={() => setTemplateCategory(cat.value)}
            >
              {cat.label}
            </button>
          ))}
        </div>
        <div className={styles.templateList}>
          {promptLoading ? (
            <div className={styles.templateLoading}>
              <Spin />
            </div>
          ) : filteredTemplates.length === 0 ? (
            <Empty description={t('agent.editor.templateEmpty')} style={{ padding: '32px 0' }} />
          ) : (
            filteredTemplates.map((p) => (
              <div key={p.templateId} className={styles.templateCard}>
                <div className={styles.templateCardTop}>
                  <span className={styles.templateName}>{p.name}</span>
                  <Tag color={CATEGORY_COLORS[p.category] ?? 'default'} style={{ flexShrink: 0 }}>
                    {p.category}
                  </Tag>
                </div>
                {p.description && <div className={styles.templateDesc}>{p.description}</div>}
                <pre className={styles.templatePreview}>
                  {(p.content ?? '').slice(0, 160)}
                  {(p.content ?? '').length > 160 ? '…' : ''}
                </pre>
                <div className={styles.templateCardActions}>
                  <Button
                    size="small"
                    type="primary"
                    icon={<CheckOutlined />}
                    onClick={() => applyTemplate(p)}
                    style={{ background: 'var(--kf-accent-gradient-r)', border: 'none' }}
                  >
                    {t('agent.editor.templateApply')}
                  </Button>
                </div>
              </div>
            ))
          )}
        </div>
      </Modal>

      {/* Variable Fill Modal */}
      <Modal
        title={t('agent.editor.varFillTitle')}
        open={varFillOpen}
        onCancel={() => {
          setVarFillOpen(false)
          setVarFillTemplate(null)
        }}
        onOk={confirmVarFill}
        okText={t('agent.editor.varFillApply')}
        width={600}
        destroyOnClose
      >
        {varFillTemplate && (
          <div className={styles.varModal}>
            <div className={styles.varSubtitle}>
              {t('agent.editor.varFillSubtitle', {
                count: extractVariables(varFillTemplate.content ?? '').length,
                name: varFillTemplate.name,
              })}
            </div>
            <div className={styles.varFields}>
              {extractVariables(varFillTemplate.content ?? '').map((varName) => (
                <div key={varName} className={styles.varField}>
                  <label className={styles.varLabel}>
                    <Tag color="blue" style={{ fontFamily: 'var(--kf-font-mono)', fontSize: 12 }}>
                      {`{{${varName}}}`}
                    </Tag>
                  </label>
                  <Input
                    placeholder={t('agent.editor.varFillPlaceholder', { name: varName })}
                    value={varValues[varName] ?? ''}
                    onChange={(e) =>
                      setVarValues((prev) => ({ ...prev, [varName]: e.target.value }))
                    }
                  />
                </div>
              ))}
            </div>
            <div className={styles.varPreviewHeader}>{t('agent.editor.varFillPreviewLabel')}</div>
            <pre className={styles.varPreviewBox}>{previewContent}</pre>
          </div>
        )}
      </Modal>
    </div>
  )
}
