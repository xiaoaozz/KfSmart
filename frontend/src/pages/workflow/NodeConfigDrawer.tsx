import { useEffect } from 'react'
import { Drawer, Form, Input, Select, Slider, InputNumber, Button } from 'antd'
import type { Node } from '@xyflow/react'
import { useTranslation } from 'react-i18next'

const LLM_OPTIONS = [
  { label: 'DeepSeek Chat', value: 'deepseek-chat' },
  { label: 'DeepSeek Coder', value: 'deepseek-coder' },
]

interface Props {
  node: Node | null
  onClose: () => void
  onSave: (nodeId: string, data: Record<string, unknown>) => void
}

export default function NodeConfigDrawer({ node, onClose, onSave }: Props) {
  const [form] = Form.useForm()
  const { t } = useTranslation()

  useEffect(() => {
    if (node) {
      form.setFieldsValue(node.data)
    }
  }, [node, form])

  const handleSave = () => {
    form.validateFields().then((values) => {
      if (node) onSave(node.id, values)
      onClose()
    })
  }

  const type = node?.type ?? ''
  const nodeLabel = t('workflow.nodeLabel.' + type, { defaultValue: type })

  return (
    <Drawer
      title={t('workflow.nodeConfig.title', { label: nodeLabel })}
      open={!!node}
      onClose={onClose}
      width={360}
      footer={
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
          <Button onClick={onClose}>{t('common.cancel')}</Button>
          <Button
            type="primary"
            onClick={handleSave}
            style={{ background: 'var(--kf-accent-gradient-r)', border: 'none' }}
          >
            {t('common.save')}
          </Button>
        </div>
      }
    >
      <Form form={form} layout="vertical">
        {type === 'start' && (
          <Form.Item
            name="inputVariable"
            label={t('workflow.nodeConfig.inputVariable')}
            initialValue="input"
          >
            <Input placeholder="input" />
          </Form.Item>
        )}

        {type === 'end' && (
          <Form.Item
            name="outputVariable"
            label={t('workflow.nodeConfig.outputVariable')}
            initialValue="output"
          >
            <Input placeholder="output" />
          </Form.Item>
        )}

        {type === 'llm' && (
          <>
            <Form.Item
              name="model"
              label={t('workflow.nodeConfig.model')}
              initialValue="deepseek-chat"
            >
              <Select options={LLM_OPTIONS} />
            </Form.Item>
            <Form.Item name="systemPrompt" label={t('workflow.nodeConfig.systemPrompt')}>
              <Input.TextArea
                rows={6}
                placeholder={t('workflow.nodeConfig.promptPlaceholder')}
                style={{ fontFamily: 'var(--kf-font-mono)', fontSize: 12 }}
              />
            </Form.Item>
            <Form.Item
              name="temperature"
              label={t('workflow.nodeConfig.temperature')}
              initialValue={0.7}
            >
              <Slider
                min={0}
                max={2}
                step={0.05}
                marks={{
                  0: t('workflow.nodeConfig.tempStrict'),
                  1: t('workflow.nodeConfig.tempBalanced'),
                  2: t('workflow.nodeConfig.tempCreative'),
                }}
              />
            </Form.Item>
            <Form.Item
              name="maxTokens"
              label={t('workflow.nodeConfig.maxTokens')}
              initialValue={2048}
            >
              <InputNumber min={256} max={8192} step={256} style={{ width: '100%' }} />
            </Form.Item>
          </>
        )}

        {type === 'kb' && (
          <>
            <Form.Item
              name="knowledgeBaseId"
              label={t('workflow.nodeConfig.kbId')}
              rules={[{ required: true }]}
            >
              <InputNumber min={1} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="topK" label={t('workflow.nodeConfig.topK')} initialValue={5}>
              <InputNumber min={1} max={20} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item
              name="threshold"
              label={t('workflow.nodeConfig.threshold')}
              initialValue={0.7}
            >
              <Slider min={0} max={1} step={0.05} />
            </Form.Item>
          </>
        )}

        {type === 'code' && (
          <>
            <Form.Item
              name="language"
              label={t('workflow.nodeConfig.language')}
              initialValue="javascript"
            >
              <Select
                options={[
                  { label: 'JavaScript', value: 'javascript' },
                  { label: 'Python', value: 'python' },
                ]}
              />
            </Form.Item>
            <Form.Item name="code" label={t('workflow.nodeConfig.code')}>
              <Input.TextArea
                rows={12}
                placeholder="// input: string&#10;// return string"
                style={{ fontFamily: 'var(--kf-font-mono)', fontSize: 12 }}
              />
            </Form.Item>
          </>
        )}

        {type === 'condition' && (
          <>
            <Form.Item
              name="variable"
              label={t('workflow.nodeConfig.variable')}
              initialValue="input"
            >
              <Input placeholder="input" />
            </Form.Item>
            <Form.Item name="operator" label={t('workflow.nodeConfig.operator')} initialValue="eq">
              <Select
                options={[
                  { label: t('workflow.nodeConfig.opEq'), value: 'eq' },
                  { label: t('workflow.nodeConfig.opNeq'), value: 'neq' },
                  { label: t('workflow.nodeConfig.opContains'), value: 'contains' },
                  { label: t('workflow.nodeConfig.opNotContains'), value: 'not_contains' },
                  { label: t('workflow.nodeConfig.opGt'), value: 'gt' },
                  { label: t('workflow.nodeConfig.opLt'), value: 'lt' },
                ]}
              />
            </Form.Item>
            <Form.Item name="value" label={t('workflow.nodeConfig.value')}>
              <Input placeholder={t('workflow.nodeConfig.valuePlaceholder')} />
            </Form.Item>
          </>
        )}
      </Form>
    </Drawer>
  )
}
