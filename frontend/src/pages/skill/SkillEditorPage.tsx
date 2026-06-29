import { useState } from 'react'
import {
  Button,
  Form,
  Input,
  Select,
  App,
  Spin,
  Space,
  Tag,
  Switch,
  Popconfirm,
  Table,
  Descriptions,
  Alert,
} from 'antd'
import {
  ArrowLeftOutlined,
  SaveOutlined,
  PlusOutlined,
  DeleteOutlined,
  PlayCircleOutlined,
} from '@ant-design/icons'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useParams, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { skillApi } from '@/api/skill'
import type { SkillTestResult } from '@/types/skill'
import styles from './SkillEditorPage.module.css'

const SCHEMA_TYPES = ['string', 'number', 'boolean', 'object', 'array'].map((v) => ({
  label: v,
  value: v,
}))

interface SchemaProperty {
  name: string
  type: string
  description?: string
  required: boolean
  defaultValue?: string
}

function parseInputSchema(json?: string): SchemaProperty[] {
  if (!json) return []
  try {
    const schema = JSON.parse(json)
    const props = schema.properties ?? {}
    const required: string[] = schema.required ?? []
    return Object.entries(props).map(([name, def]: [string, unknown]) => ({
      name,
      type: String((def as Record<string, unknown>).type ?? 'string'),
      description: String((def as Record<string, unknown>).description ?? ''),
      required: required.includes(name),
      defaultValue:
        (def as Record<string, unknown>).default != null
          ? String((def as Record<string, unknown>).default)
          : undefined,
    }))
  } catch {
    return []
  }
}

function buildInputSchema(properties: SchemaProperty[]): string {
  const obj: Record<string, unknown> = { type: 'object', properties: {}, required: [] }
  const required: string[] = []
  for (const prop of properties) {
    ;(obj.properties as Record<string, unknown>)[prop.name] = {
      type: prop.type,
      ...(prop.description ? { description: prop.description } : {}),
      ...(prop.defaultValue != null ? { default: prop.defaultValue } : {}),
    }
    if (prop.required) required.push(prop.name)
  }
  obj.required = required
  return JSON.stringify(obj, null, 2)
}

export default function SkillEditorPage() {
  const { skillId } = useParams<{ skillId: string }>()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const { message } = App.useApp()
  const { t } = useTranslation()

  const [form] = Form.useForm<{
    name: string
    description?: string
    category: string
    instruction: string
    systemPrompt?: string
    outputSchema?: string
    tags?: string
  }>()
  const [schemaProperties, setSchemaProperties] = useState<SchemaProperty[]>([])
  const [testArgs, setTestArgs] = useState('')
  const [testResult, setTestResult] = useState<SkillTestResult | null>(null)
  const [testing, setTesting] = useState(false)

  const { isLoading } = useQuery({
    queryKey: ['skills', skillId],
    queryFn: () => skillApi.get(skillId!),
    enabled: !!skillId,
    gcTime: 0,
    select: (sk) => {
      form.setFieldsValue({
        name: sk.name,
        description: sk.description,
        category: sk.category,
        instruction: sk.instruction ?? '',
        systemPrompt: sk.systemPrompt ?? '',
        outputSchema: sk.outputSchema,
        tags: sk.tags,
      })
      setSchemaProperties(parseInputSchema(sk.inputSchema))
      return sk
    },
  })

  type FormValues = {
    name: string
    description?: string
    category: string
    instruction: string
    systemPrompt?: string
    outputSchema?: string
    tags?: string
  }

  const saveMutation = useMutation({
    mutationFn: (values: FormValues) =>
      skillApi.update(skillId!, {
        ...values,
        inputSchema: buildInputSchema(schemaProperties),
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['skills'] })
      message.success(t('skill.editor.saveSuccess'))
    },
  })

  const handleSave = () => {
    form.validateFields().then((values) => {
      saveMutation.mutate(values as FormValues)
    })
  }

  const handleTest = async () => {
    setTesting(true)
    setTestResult(null)
    try {
      let args: Record<string, unknown> = {}
      if (testArgs.trim()) args = JSON.parse(testArgs)
      const res = await skillApi.test(skillId!, args)
      setTestResult(res)
    } catch (e) {
      message.error(e instanceof Error ? e.message : t('skill.editor.testFailed'))
    } finally {
      setTesting(false)
    }
  }

  const addProperty = () => {
    setSchemaProperties((prev) => [
      ...prev,
      { name: `param${prev.length + 1}`, type: 'string', required: false },
    ])
  }

  const updateProperty = (idx: number, key: keyof SchemaProperty, value: unknown) => {
    setSchemaProperties((prev) => prev.map((p, i) => (i === idx ? { ...p, [key]: value } : p)))
  }

  const removeProperty = (idx: number) => {
    setSchemaProperties((prev) => prev.filter((_, i) => i !== idx))
  }

  if (isLoading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: 80 }}>
        <Spin />
      </div>
    )
  }

  return (
    <div className={styles.root}>
      <div className={styles.topBar}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/skills')}>
          {t('skill.editor.backBtn')}
        </Button>
        <h2 className={styles.pageTitle}>{t('skill.editor.title')}</h2>
        <Space>
          <Button
            type="primary"
            icon={<SaveOutlined />}
            loading={saveMutation.isPending}
            onClick={handleSave}
            style={{ background: 'var(--kf-accent-gradient-r)', border: 'none' }}
          >
            {t('skill.editor.saveBtn')}
          </Button>
        </Space>
      </div>

      <div className={styles.body}>
        <div className={styles.leftPanel}>
          <Form form={form} layout="vertical">
            <div className={styles.row2}>
              <Form.Item
                name="name"
                label={t('skill.editor.fieldName')}
                rules={[{ required: true }]}
                style={{ flex: 1 }}
              >
                <Input />
              </Form.Item>
              <Form.Item
                name="category"
                label={t('skill.editor.fieldCategory')}
                initialValue="通用技能"
                style={{ width: 200 }}
              >
                <Input placeholder={t('skill.editor.fieldCategory')} />
              </Form.Item>
            </div>

            <Form.Item name="description" label={t('skill.editor.fieldDesc')}>
              <Input placeholder={t('skill.editor.descPlaceholder')} />
            </Form.Item>

            <Form.Item name="tags" label="标签（逗号分隔）">
              <Input placeholder="标签1, 标签2" />
            </Form.Item>

            <Form.Item name="instruction" label="技能指令 / 代码">
              <Input.TextArea
                rows={12}
                className={styles.codeArea}
                placeholder="// 在此编写技能逻辑指令"
              />
            </Form.Item>

            <Form.Item name="systemPrompt" label="System Prompt">
              <Input.TextArea
                rows={4}
                className={styles.codeArea}
                placeholder="系统提示词（可选）"
              />
            </Form.Item>

            <Form.Item name="outputSchema" label="输出 Schema (JSON)">
              <Input.TextArea
                rows={4}
                className={styles.codeArea}
                placeholder='{"type":"object","properties":{"answer":{"type":"string"}}}'
              />
            </Form.Item>
          </Form>

          <div className={styles.paramsSection}>
            <div className={styles.paramsHeader}>
              <span className={styles.paramsSectionTitle}>输入参数 Schema</span>
              <Button size="small" icon={<PlusOutlined />} onClick={addProperty}>
                {t('skill.editor.addParamBtn')}
              </Button>
            </div>
            <Table
              size="small"
              dataSource={schemaProperties}
              rowKey={(_, i) => String(i)}
              pagination={false}
              columns={[
                {
                  title: t('skill.editor.colParamName'),
                  dataIndex: 'name',
                  render: (v: string, _: SchemaProperty, i: number) => (
                    <Input
                      size="small"
                      value={v}
                      onChange={(e) => updateProperty(i, 'name', e.target.value)}
                      style={{ fontFamily: 'var(--kf-font-mono)' }}
                    />
                  ),
                },
                {
                  title: t('skill.editor.colParamType'),
                  dataIndex: 'type',
                  width: 110,
                  render: (v: string, _: SchemaProperty, i: number) => (
                    <Select
                      size="small"
                      value={v}
                      options={SCHEMA_TYPES}
                      onChange={(val) => updateProperty(i, 'type', val)}
                      style={{ width: '100%' }}
                    />
                  ),
                },
                {
                  title: t('skill.editor.colParamRequired'),
                  dataIndex: 'required',
                  width: 60,
                  render: (v: boolean, _: SchemaProperty, i: number) => (
                    <Switch
                      size="small"
                      checked={v}
                      onChange={(checked) => updateProperty(i, 'required', checked)}
                    />
                  ),
                },
                {
                  title: t('skill.editor.colParamDesc'),
                  dataIndex: 'description',
                  render: (v: string, _: SchemaProperty, i: number) => (
                    <Input
                      size="small"
                      value={v}
                      placeholder={t('skill.editor.paramDescPlaceholder')}
                      onChange={(e) => updateProperty(i, 'description', e.target.value)}
                    />
                  ),
                },
                {
                  title: '',
                  width: 40,
                  render: (_: unknown, __: SchemaProperty, i: number) => (
                    <Popconfirm
                      title={t('skill.editor.deleteParamConfirm')}
                      onConfirm={() => removeProperty(i)}
                    >
                      <Button size="small" type="text" danger icon={<DeleteOutlined />} />
                    </Popconfirm>
                  ),
                },
              ]}
            />
          </div>
        </div>

        <div className={styles.testPanel}>
          <div className={styles.testHeader}>
            <PlayCircleOutlined /> {t('skill.editor.testPanelTitle')}
          </div>
          <div className={styles.testBody}>
            <div>
              <div className={styles.testLabel}>{t('skill.editor.testArgsLabel')}</div>
              <Input.TextArea
                value={testArgs}
                onChange={(e) => setTestArgs(e.target.value)}
                rows={6}
                placeholder='{"key": "value"}'
                className={styles.codeArea}
              />
            </div>
            <Button
              type="primary"
              icon={<PlayCircleOutlined />}
              loading={testing}
              onClick={handleTest}
              style={{ background: 'var(--kf-accent-gradient-r)', border: 'none' }}
            >
              {t('skill.editor.testRunBtn')}
            </Button>
            {testResult && (
              <>
                <Alert
                  type={testResult.success ? 'success' : 'error'}
                  message={testResult.message}
                  style={{ padding: '6px 12px' }}
                />
                {testResult.validation && (
                  <Descriptions size="small" column={1} bordered>
                    <Descriptions.Item label="校验结果">
                      {testResult.validation.valid ? '通过' : '未通过'}
                    </Descriptions.Item>
                    {testResult.validation.missingFields.length > 0 && (
                      <Descriptions.Item label="缺失字段">
                        {testResult.validation.missingFields.join(', ')}
                      </Descriptions.Item>
                    )}
                    {testResult.validation.typeErrors.length > 0 && (
                      <Descriptions.Item label="类型错误">
                        {testResult.validation.typeErrors.join(', ')}
                      </Descriptions.Item>
                    )}
                  </Descriptions>
                )}
                {testResult.executionPlan && testResult.executionPlan.length > 0 && (
                  <div>
                    <div className={styles.testLabel}>执行计划</div>
                    <pre className={styles.testOutput}>{testResult.executionPlan.join('\n')}</pre>
                  </div>
                )}
                {testResult.warnings && testResult.warnings.length > 0 && (
                  <div>
                    <div className={styles.testLabel}>警告</div>
                    {testResult.warnings.map((w, idx) => (
                      <Tag key={idx} color="orange" style={{ marginBottom: 4 }}>
                        {w}
                      </Tag>
                    ))}
                  </div>
                )}
                {testResult.mockOutput && (
                  <div>
                    <div className={styles.testLabel}>模拟输出</div>
                    <pre className={styles.testOutput}>
                      {JSON.stringify(testResult.mockOutput, null, 2)}
                    </pre>
                  </div>
                )}
              </>
            )}
            {schemaProperties.length > 0 && (
              <div>
                <div className={styles.testLabel}>{t('skill.editor.testParamsLabel')}</div>
                <div className={styles.paramHints}>
                  {schemaProperties.map((p) => (
                    <div key={p.name} className={styles.paramHint}>
                      <Tag color="blue" style={{ fontFamily: 'var(--kf-font-mono)' }}>
                        {p.name}
                      </Tag>
                      <Tag>{p.type}</Tag>
                      {p.required && <Tag color="orange">{t('skill.editor.requiredTag')}</Tag>}
                      {p.description && (
                        <span className={styles.paramHintDesc}>{p.description}</span>
                      )}
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
