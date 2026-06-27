import { useEffect } from 'react'
import { Drawer, Form, Input, Select, Slider, InputNumber, Button } from 'antd'
import type { Node } from '@xyflow/react'
import { NODE_LABELS } from './nodes/nodeTypes'

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

  return (
    <Drawer
      title={`配置 — ${NODE_LABELS[type] ?? type}`}
      open={!!node}
      onClose={onClose}
      width={360}
      footer={
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
          <Button onClick={onClose}>取消</Button>
          <Button
            type="primary"
            onClick={handleSave}
            style={{ background: 'var(--kf-accent-gradient-r)', border: 'none' }}
          >
            保存
          </Button>
        </div>
      }
    >
      <Form form={form} layout="vertical">
        {type === 'start' && (
          <Form.Item name="inputVariable" label="输入变量名" initialValue="input">
            <Input placeholder="input" />
          </Form.Item>
        )}

        {type === 'end' && (
          <Form.Item name="outputVariable" label="输出变量名" initialValue="output">
            <Input placeholder="output" />
          </Form.Item>
        )}

        {type === 'llm' && (
          <>
            <Form.Item name="model" label="模型" initialValue="deepseek-chat">
              <Select options={LLM_OPTIONS} />
            </Form.Item>
            <Form.Item name="systemPrompt" label="系统 Prompt">
              <Input.TextArea
                rows={6}
                placeholder="你是一个专业的 AI 助手…"
                style={{ fontFamily: 'var(--kf-font-mono)', fontSize: 12 }}
              />
            </Form.Item>
            <Form.Item name="temperature" label="温度" initialValue={0.7}>
              <Slider min={0} max={2} step={0.05} marks={{ 0: '严谨', 1: '均衡', 2: '创意' }} />
            </Form.Item>
            <Form.Item name="maxTokens" label="最大 Token" initialValue={2048}>
              <InputNumber min={256} max={8192} step={256} style={{ width: '100%' }} />
            </Form.Item>
          </>
        )}

        {type === 'kb' && (
          <>
            <Form.Item name="knowledgeBaseId" label="知识库 ID" rules={[{ required: true }]}>
              <InputNumber min={1} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="topK" label="返回条数" initialValue={5}>
              <InputNumber min={1} max={20} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="threshold" label="相似度阈值" initialValue={0.7}>
              <Slider min={0} max={1} step={0.05} />
            </Form.Item>
          </>
        )}

        {type === 'code' && (
          <>
            <Form.Item name="language" label="语言" initialValue="javascript">
              <Select
                options={[
                  { label: 'JavaScript', value: 'javascript' },
                  { label: 'Python', value: 'python' },
                ]}
              />
            </Form.Item>
            <Form.Item name="code" label="代码">
              <Input.TextArea
                rows={12}
                placeholder="// input: string\n// return string"
                style={{ fontFamily: 'var(--kf-font-mono)', fontSize: 12 }}
              />
            </Form.Item>
          </>
        )}

        {type === 'condition' && (
          <>
            <Form.Item name="variable" label="变量" initialValue="input">
              <Input placeholder="input" />
            </Form.Item>
            <Form.Item name="operator" label="操作符" initialValue="eq">
              <Select
                options={[
                  { label: '等于', value: 'eq' },
                  { label: '不等于', value: 'neq' },
                  { label: '包含', value: 'contains' },
                  { label: '不包含', value: 'not_contains' },
                  { label: '大于', value: 'gt' },
                  { label: '小于', value: 'lt' },
                ]}
              />
            </Form.Item>
            <Form.Item name="value" label="值">
              <Input placeholder="比较值" />
            </Form.Item>
          </>
        )}
      </Form>
    </Drawer>
  )
}
