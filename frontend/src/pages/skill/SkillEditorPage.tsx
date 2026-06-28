import { useState } from 'react'
import { Button, Form, Input, Select, Table, App, Spin, Space, Tag, Switch, Popconfirm } from 'antd'
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
import type { SkillParam } from '@/types/skill'
import styles from './SkillEditorPage.module.css'

const PARAM_TYPES = ['string', 'number', 'boolean', 'object', 'array'].map((v) => ({
  label: v,
  value: v,
}))

export default function SkillEditorPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const { message } = App.useApp()
  const { t } = useTranslation()

  const [form] = Form.useForm<{
    name: string
    description?: string
    category: string
    language: string
    code: string
    outputType: string
  }>()
  const [params, setParams] = useState<SkillParam[]>([])
  const [testArgs, setTestArgs] = useState('')
  const [testResult, setTestResult] = useState('')
  const [testing, setTesting] = useState(false)

  const { isLoading } = useQuery({
    queryKey: ['skills', id],
    queryFn: () => skillApi.get(Number(id)),
    enabled: !!id,
    gcTime: 0,
    select: (sk) => {
      form.setFieldsValue({
        name: sk.name,
        description: sk.description,
        category: sk.category,
        language: sk.language,
        code: sk.code,
        outputType: sk.outputType,
      })
      setParams(sk.params)
      return sk
    },
  })

  type FormValues = {
    name: string
    description?: string
    category: string
    language: string
    code: string
    outputType: string
  }

  const saveMutation = useMutation({
    mutationFn: (values: FormValues) => skillApi.update(Number(id), { ...values, params }),
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
    setTestResult('')
    try {
      let args: Record<string, unknown> = {}
      if (testArgs.trim()) args = JSON.parse(testArgs)
      const res = await skillApi.test(Number(id), args)
      setTestResult(t('skill.editor.testOutput', { output: res.output, ms: res.durationMs }))
    } catch (e) {
      setTestResult(e instanceof Error ? e.message : t('skill.editor.testFailed'))
    } finally {
      setTesting(false)
    }
  }

  const addParam = () => {
    setParams((prev) => [
      ...prev,
      { name: `param${prev.length + 1}`, type: 'string', required: false },
    ])
  }

  const updateParam = (idx: number, key: keyof SkillParam, value: unknown) => {
    setParams((prev) => prev.map((p, i) => (i === idx ? { ...p, [key]: value } : p)))
  }

  const removeParam = (idx: number) => {
    setParams((prev) => prev.filter((_, i) => i !== idx))
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
                initialValue="custom"
                style={{ width: 140 }}
              >
                <Select
                  options={[
                    { label: 'HTTP', value: 'http' },
                    { label: t('skill.editor.categoryDatabase'), value: 'database' },
                    { label: t('skill.editor.categoryFile'), value: 'file' },
                    { label: 'AI', value: 'ai' },
                    { label: t('skill.editor.categoryCustom'), value: 'custom' },
                  ]}
                />
              </Form.Item>
              <Form.Item
                name="language"
                label={t('skill.editor.fieldLanguage')}
                initialValue="javascript"
                style={{ width: 130 }}
              >
                <Select
                  options={[
                    { label: 'JavaScript', value: 'javascript' },
                    { label: 'Python', value: 'python' },
                  ]}
                />
              </Form.Item>
            </div>

            <Form.Item name="description" label={t('skill.editor.fieldDesc')}>
              <Input placeholder={t('skill.editor.descPlaceholder')} />
            </Form.Item>

            <Form.Item name="code" label={t('skill.editor.fieldCode')}>
              <Input.TextArea
                rows={16}
                className={styles.codeArea}
                placeholder="async function run(args) {&#10;  return args;&#10;}"
              />
            </Form.Item>

            <Form.Item
              name="outputType"
              label={t('skill.editor.fieldOutputType')}
              initialValue="string"
            >
              <Select
                options={[
                  { label: 'string', value: 'string' },
                  { label: 'number', value: 'number' },
                  { label: 'object', value: 'object' },
                  { label: 'array', value: 'array' },
                ]}
              />
            </Form.Item>
          </Form>

          <div className={styles.paramsSection}>
            <div className={styles.paramsHeader}>
              <span className={styles.paramsSectionTitle}>{t('skill.editor.paramsTitle')}</span>
              <Button size="small" icon={<PlusOutlined />} onClick={addParam}>
                {t('skill.editor.addParamBtn')}
              </Button>
            </div>
            <Table
              size="small"
              dataSource={params}
              rowKey={(_, i) => String(i)}
              pagination={false}
              columns={[
                {
                  title: t('skill.editor.colParamName'),
                  dataIndex: 'name',
                  render: (v: string, _: SkillParam, i: number) => (
                    <Input
                      size="small"
                      value={v}
                      onChange={(e) => updateParam(i, 'name', e.target.value)}
                      style={{ fontFamily: 'var(--kf-font-mono)' }}
                    />
                  ),
                },
                {
                  title: t('skill.editor.colParamType'),
                  dataIndex: 'type',
                  width: 110,
                  render: (v: string, _: SkillParam, i: number) => (
                    <Select
                      size="small"
                      value={v}
                      options={PARAM_TYPES}
                      onChange={(val) => updateParam(i, 'type', val)}
                      style={{ width: '100%' }}
                    />
                  ),
                },
                {
                  title: t('skill.editor.colParamRequired'),
                  dataIndex: 'required',
                  width: 60,
                  render: (v: boolean, _: SkillParam, i: number) => (
                    <Switch
                      size="small"
                      checked={v}
                      onChange={(checked) => updateParam(i, 'required', checked)}
                    />
                  ),
                },
                {
                  title: t('skill.editor.colParamDesc'),
                  dataIndex: 'description',
                  render: (v: string, _: SkillParam, i: number) => (
                    <Input
                      size="small"
                      value={v}
                      placeholder={t('skill.editor.paramDescPlaceholder')}
                      onChange={(e) => updateParam(i, 'description', e.target.value)}
                    />
                  ),
                },
                {
                  title: '',
                  width: 40,
                  render: (_: unknown, __: SkillParam, i: number) => (
                    <Popconfirm
                      title={t('skill.editor.deleteParamConfirm')}
                      onConfirm={() => removeParam(i)}
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
              <div>
                <div className={styles.testLabel}>{t('skill.editor.testOutputLabel')}</div>
                <pre className={styles.testOutput}>{testResult}</pre>
              </div>
            )}
            {params.length > 0 && (
              <div>
                <div className={styles.testLabel}>{t('skill.editor.testParamsLabel')}</div>
                <div className={styles.paramHints}>
                  {params.map((p) => (
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
