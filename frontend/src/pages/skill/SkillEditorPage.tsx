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
      message.success('保存成功')
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
      setTestResult(`输出：\n${res.output}\n\n耗时：${res.durationMs}ms`)
    } catch (e) {
      setTestResult(e instanceof Error ? e.message : '测试失败')
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
          返回
        </Button>
        <h2 className={styles.pageTitle}>技能编辑器</h2>
        <Space>
          <Button
            type="primary"
            icon={<SaveOutlined />}
            loading={saveMutation.isPending}
            onClick={handleSave}
            style={{ background: 'var(--kf-accent-gradient-r)', border: 'none' }}
          >
            保存
          </Button>
        </Space>
      </div>

      <div className={styles.body}>
        {/* Left: config + code */}
        <div className={styles.leftPanel}>
          <Form form={form} layout="vertical">
            <div className={styles.row2}>
              <Form.Item name="name" label="名称" rules={[{ required: true }]} style={{ flex: 1 }}>
                <Input />
              </Form.Item>
              <Form.Item name="category" label="分类" initialValue="custom" style={{ width: 140 }}>
                <Select
                  options={[
                    { label: 'HTTP', value: 'http' },
                    { label: '数据库', value: 'database' },
                    { label: '文件', value: 'file' },
                    { label: 'AI', value: 'ai' },
                    { label: '自定义', value: 'custom' },
                  ]}
                />
              </Form.Item>
              <Form.Item
                name="language"
                label="语言"
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

            <Form.Item name="description" label="描述">
              <Input placeholder="简要描述此技能功能" />
            </Form.Item>

            <Form.Item name="code" label="代码">
              <Input.TextArea
                rows={16}
                className={styles.codeArea}
                placeholder="async function run(args) {&#10;  return args;&#10;}"
              />
            </Form.Item>

            <Form.Item name="outputType" label="输出类型" initialValue="string">
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

          {/* Params table */}
          <div className={styles.paramsSection}>
            <div className={styles.paramsHeader}>
              <span className={styles.paramsSectionTitle}>参数定义</span>
              <Button size="small" icon={<PlusOutlined />} onClick={addParam}>
                添加参数
              </Button>
            </div>
            <Table
              size="small"
              dataSource={params}
              rowKey={(_, i) => String(i)}
              pagination={false}
              columns={[
                {
                  title: '参数名',
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
                  title: '类型',
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
                  title: '必填',
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
                  title: '描述',
                  dataIndex: 'description',
                  render: (v: string, _: SkillParam, i: number) => (
                    <Input
                      size="small"
                      value={v}
                      placeholder="可选"
                      onChange={(e) => updateParam(i, 'description', e.target.value)}
                    />
                  ),
                },
                {
                  title: '',
                  width: 40,
                  render: (_: unknown, __: SkillParam, i: number) => (
                    <Popconfirm title="删除此参数？" onConfirm={() => removeParam(i)}>
                      <Button size="small" type="text" danger icon={<DeleteOutlined />} />
                    </Popconfirm>
                  ),
                },
              ]}
            />
          </div>
        </div>

        {/* Right: test panel */}
        <div className={styles.testPanel}>
          <div className={styles.testHeader}>
            <PlayCircleOutlined /> 测试运行
          </div>
          <div className={styles.testBody}>
            <div>
              <div className={styles.testLabel}>参数 JSON</div>
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
              运行测试
            </Button>
            {testResult && (
              <div>
                <div className={styles.testLabel}>输出</div>
                <pre className={styles.testOutput}>{testResult}</pre>
              </div>
            )}
            {params.length > 0 && (
              <div>
                <div className={styles.testLabel}>参数说明</div>
                <div className={styles.paramHints}>
                  {params.map((p) => (
                    <div key={p.name} className={styles.paramHint}>
                      <Tag color="blue" style={{ fontFamily: 'var(--kf-font-mono)' }}>
                        {p.name}
                      </Tag>
                      <Tag>{p.type}</Tag>
                      {p.required && <Tag color="orange">必填</Tag>}
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
