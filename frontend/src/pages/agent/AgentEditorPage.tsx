import { useState } from 'react'
import { Form, Input, Select, Slider, InputNumber, Button, App, Spin, Divider, Space } from 'antd'
import { SaveOutlined, SendOutlined, ArrowLeftOutlined, RobotOutlined } from '@ant-design/icons'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate, useParams } from 'react-router-dom'
import { motion } from 'framer-motion'
import { useTranslation } from 'react-i18next'
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
  const { t } = useTranslation()
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
      message.success(isEdit ? t('agent.editor.saveSuccess') : t('agent.editor.createSuccess'))
      if (!isEdit) navigate(`/agents/${agent.id}/edit`, { replace: true })
    },
  })

  const handleTest = async () => {
    if (!testInput.trim() || !isEdit) return
    setTesting(true)
    setTestOutput('')
    try {
      await agentApi.testRun(Number(id), testInput)
      setTestOutput(t('agent.editor.testTriggered'))
    } catch {
      setTestOutput(t('agent.editor.testFailed'))
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
      <div className={styles.topBar}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/agents')}>
          {t('agent.editor.backBtn')}
        </Button>
        <h2 className={styles.pageTitle}>
          {isEdit ? t('agent.editor.editTitle') : t('agent.editor.createTitle')}
        </h2>
        <Space>
          <Button
            type="primary"
            icon={<SaveOutlined />}
            loading={saveMutation.isPending}
            onClick={() => form.submit()}
            style={{ background: 'var(--kf-accent-gradient-r)', border: 'none' }}
          >
            {t('agent.editor.saveBtn')}
          </Button>
        </Space>
      </div>

      <div className={styles.body}>
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
            <Divider style={{ fontSize: 13, textAlign: 'left' }}>
              {t('agent.editor.sectionBasic')}
            </Divider>

            <Form.Item
              name="name"
              label={t('agent.editor.fieldName')}
              rules={[{ required: true, message: t('agent.editor.nameRequired') }]}
            >
              <Input placeholder={t('agent.editor.namePlaceholder')} />
            </Form.Item>

            <Form.Item name="description" label={t('agent.editor.fieldDesc')}>
              <Input.TextArea rows={2} placeholder={t('agent.editor.descPlaceholder')} />
            </Form.Item>

            <Form.Item
              name="systemPrompt"
              label={t('agent.editor.fieldPrompt')}
              rules={[{ required: true, message: t('agent.editor.promptRequired') }]}
            >
              <Input.TextArea
                rows={8}
                placeholder={t('agent.editor.promptPlaceholder')}
                style={{ fontFamily: 'var(--kf-font-mono)', fontSize: 13 }}
              />
            </Form.Item>

            <Divider style={{ fontSize: 13, textAlign: 'left' }}>
              {t('agent.editor.sectionKb')}
            </Divider>

            <Form.Item name="knowledgeBaseIds" label={t('agent.editor.fieldKb')}>
              <Select
                mode="multiple"
                placeholder={t('agent.editor.kbPlaceholder')}
                options={kbData?.records.map((kb) => ({ label: kb.name, value: kb.id }))}
                allowClear
              />
            </Form.Item>

            <Divider style={{ fontSize: 13, textAlign: 'left' }}>
              {t('agent.editor.sectionModel')}
            </Divider>

            <Form.Item name={['model', 'name']} label={t('agent.editor.fieldModel')}>
              <Select options={MODEL_OPTIONS} />
            </Form.Item>

            <Form.Item name={['model', 'temperature']} label={t('agent.editor.fieldTemp')}>
              <Slider
                min={0}
                max={2}
                step={0.05}
                marks={{
                  0: t('agent.editor.tempStrict'),
                  1: t('agent.editor.tempBalanced'),
                  2: t('agent.editor.tempCreative'),
                }}
              />
            </Form.Item>

            <Form.Item name={['model', 'maxTokens']} label={t('agent.editor.fieldMaxTokens')}>
              <InputNumber min={256} max={8192} step={256} style={{ width: '100%' }} />
            </Form.Item>
          </Form>
        </motion.div>

        <motion.div
          className={styles.previewPanel}
          initial={{ opacity: 0, x: 16 }}
          animate={{ opacity: 1, x: 0 }}
          transition={{ duration: 0.3 }}
        >
          <div className={styles.previewHeader}>
            <RobotOutlined /> {t('agent.editor.previewHeader')}
          </div>
          <div className={styles.previewOutput}>
            {testOutput ? (
              <div className={styles.outputText}>{testOutput}</div>
            ) : (
              <div className={styles.outputEmpty}>{t('agent.editor.previewEmpty')}</div>
            )}
          </div>
          <div className={styles.previewInput}>
            <Input.TextArea
              value={testInput}
              onChange={(e) => setTestInput(e.target.value)}
              placeholder={t('agent.editor.testPlaceholder')}
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
              {t('common.send')}
            </Button>
            {!isEdit && <div className={styles.saveHint}>{t('agent.editor.saveHint')}</div>}
          </div>
        </motion.div>
      </div>
    </div>
  )
}
