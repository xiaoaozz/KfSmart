import { useState } from 'react'
import { Form, Input, Select, Slider, InputNumber, Button, App, Spin, Divider, Space } from 'antd'
import { SaveOutlined, SendOutlined, ArrowLeftOutlined, RobotOutlined } from '@ant-design/icons'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate, useParams } from 'react-router-dom'
import { motion } from 'framer-motion'
import { agentApi } from '@/api/agent'
import { kbApi } from '@/api/knowledge-base'
import type { AgentFormValues } from '@/types/agent'
import styles from './AgentEditorPage.module.css'

const DEFAULT_MODEL: AgentFormValues['model'] = {
  provider: 'deepseek',
  name: 'deepseek-chat',
  temperature: 0.7,
  maxTokens: 2048,
}

const MODEL_OPTIONS = [
  { label: 'DeepSeek Chat', value: 'deepseek-chat', provider: 'deepseek' },
  { label: 'DeepSeek Coder', value: 'deepseek-coder', provider: 'deepseek' },
  { label: 'GPT-4o', value: 'gpt-4o', provider: 'openai' },
  { label: 'GPT-3.5 Turbo', value: 'gpt-3.5-turbo', provider: 'openai' },
]

export default function AgentEditorPage() {
  const { id } = useParams<{ id?: string }>()
  const isEdit = !!id
  const navigate = useNavigate()
  const qc = useQueryClient()
  const { message } = App.useApp()
  const [form] = Form.useForm<AgentFormValues>()
  const [testInput, setTestInput] = useState('')
  const [testOutput, setTestOutput] = useState('')
  const [testing, setTesting] = useState(false)

  const { isLoading } = useQuery({
    queryKey: ['agents', id],
    queryFn: () => agentApi.get(Number(id)),
    enabled: isEdit,
    gcTime: 0,
    select: (agent) => {
      form.setFieldsValue({
        name: agent.name,
        description: agent.description,
        systemPrompt: agent.systemPrompt,
        model: agent.model,
        knowledgeBaseIds: agent.knowledgeBaseIds,
        skillIds: agent.skillIds,
      })
      return agent
    },
  })

  const { data: kbData } = useQuery({
    queryKey: ['knowledge-bases', 'all'],
    queryFn: () => kbApi.list({ size: 100 }),
  })

  const saveMutation = useMutation({
    mutationFn: (values: AgentFormValues) =>
      isEdit ? agentApi.update(Number(id), values) : agentApi.create(values),
    onSuccess: (agent) => {
      qc.invalidateQueries({ queryKey: ['agents'] })
      message.success(isEdit ? '保存成功' : '创建成功')
      if (!isEdit) navigate(`/agents/${agent.id}/edit`, { replace: true })
    },
  })

  const handleTest = async () => {
    if (!testInput.trim() || !isEdit) return
    setTesting(true)
    setTestOutput('')
    try {
      await agentApi.testRun(Number(id), testInput)
      setTestOutput('（测试运行已触发，结果通过 WebSocket 推送）')
    } catch {
      setTestOutput('测试运行失败')
    } finally {
      setTesting(false)
    }
  }

  if (isEdit && isLoading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: 80 }}>
        <Spin />
      </div>
    )
  }

  return (
    <div className={styles.root}>
      {/* Header bar */}
      <div className={styles.topBar}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/agents')}>
          返回列表
        </Button>
        <h2 className={styles.pageTitle}>{isEdit ? '编辑 Agent' : '新建 Agent'}</h2>
        <Space>
          <Button
            type="primary"
            icon={<SaveOutlined />}
            loading={saveMutation.isPending}
            onClick={() => form.submit()}
            style={{ background: 'var(--kf-accent-gradient-r)', border: 'none' }}
          >
            保存
          </Button>
        </Space>
      </div>

      <div className={styles.body}>
        {/* Left: configuration form */}
        <motion.div
          className={styles.formPanel}
          initial={{ opacity: 0, x: -16 }}
          animate={{ opacity: 1, x: 0 }}
          transition={{ duration: 0.3 }}
        >
          <Form
            form={form}
            layout="vertical"
            onFinish={(v) => saveMutation.mutate(v)}
            initialValues={{ model: DEFAULT_MODEL, knowledgeBaseIds: [], skillIds: [] }}
          >
            <Divider style={{ fontSize: 13, textAlign: 'left' }}>基本信息</Divider>

            <Form.Item
              name="name"
              label="名称"
              rules={[{ required: true, message: '请输入 Agent 名称' }]}
            >
              <Input placeholder="Agent 名称" />
            </Form.Item>

            <Form.Item name="description" label="描述">
              <Input.TextArea rows={2} placeholder="简要描述此 Agent 的用途" />
            </Form.Item>

            <Form.Item
              name="systemPrompt"
              label="系统 Prompt"
              rules={[{ required: true, message: '请输入系统 Prompt' }]}
            >
              <Input.TextArea
                rows={8}
                placeholder="你是一个专业的 AI 助手，..."
                style={{ fontFamily: 'var(--kf-font-mono)', fontSize: 13 }}
              />
            </Form.Item>

            <Divider style={{ fontSize: 13, textAlign: 'left' }}>知识库</Divider>

            <Form.Item name="knowledgeBaseIds" label="关联知识库">
              <Select
                mode="multiple"
                placeholder="选择知识库（可多选）"
                options={kbData?.records.map((kb) => ({ label: kb.name, value: kb.id }))}
                allowClear
              />
            </Form.Item>

            <Divider style={{ fontSize: 13, textAlign: 'left' }}>模型配置</Divider>

            <Form.Item name={['model', 'name']} label="模型">
              <Select options={MODEL_OPTIONS} />
            </Form.Item>

            <Form.Item name={['model', 'temperature']} label={`温度（创造性）`}>
              <Slider min={0} max={2} step={0.05} marks={{ 0: '严谨', 1: '均衡', 2: '创意' }} />
            </Form.Item>

            <Form.Item name={['model', 'maxTokens']} label="最大 Token 数">
              <InputNumber min={256} max={8192} step={256} style={{ width: '100%' }} />
            </Form.Item>
          </Form>
        </motion.div>

        {/* Right: preview chat */}
        <motion.div
          className={styles.previewPanel}
          initial={{ opacity: 0, x: 16 }}
          animate={{ opacity: 1, x: 0 }}
          transition={{ duration: 0.3 }}
        >
          <div className={styles.previewHeader}>
            <RobotOutlined /> 测试预览
          </div>
          <div className={styles.previewOutput}>
            {testOutput ? (
              <div className={styles.outputText}>{testOutput}</div>
            ) : (
              <div className={styles.outputEmpty}>输入测试内容后点击发送，将触发 Agent 运行</div>
            )}
          </div>
          <div className={styles.previewInput}>
            <Input.TextArea
              value={testInput}
              onChange={(e) => setTestInput(e.target.value)}
              placeholder="输入测试消息…"
              autoSize={{ minRows: 2, maxRows: 5 }}
              disabled={!isEdit || testing}
            />
            <Button
              type="primary"
              icon={<SendOutlined />}
              loading={testing}
              disabled={!isEdit || !testInput.trim()}
              onClick={handleTest}
              style={{ background: 'var(--kf-accent-gradient-r)', border: 'none' }}
            >
              发送
            </Button>
            {!isEdit && <div className={styles.saveHint}>请先保存 Agent 后再测试</div>}
          </div>
        </motion.div>
      </div>
    </div>
  )
}
