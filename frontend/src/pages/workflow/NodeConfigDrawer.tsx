import { useEffect, useState } from 'react'
import {
  Drawer,
  Form,
  Input,
  Select,
  Slider,
  InputNumber,
  Button,
  Divider,
  Tag,
  Typography,
} from 'antd'
import type { Node, Edge } from '@xyflow/react'
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import type { StartNodeVariable, InputMapping } from '@/types/workflow'
import InputMappingEditor from './InputMappingEditor'
import { getNodeOutputs } from './nodeSchema'

const { Text } = Typography

const LLM_OPTIONS = [
  { label: 'DeepSeek Chat', value: 'deepseek-chat' },
  { label: 'DeepSeek Coder', value: 'deepseek-coder' },
]

const HTTP_METHODS = [
  { label: 'GET', value: 'GET' },
  { label: 'POST', value: 'POST' },
  { label: 'PUT', value: 'PUT' },
  { label: 'DELETE', value: 'DELETE' },
]

const NODES_WITH_INPUT_MAPPING = new Set([
  'end',
  'llm',
  'kb',
  'code',
  'condition',
  'http',
  'loop',
  'variable',
  'agent_call',
])

interface Props {
  node: Node | null
  nodes: Node[]
  edges: Edge[]
  onClose: () => void
  onSave: (nodeId: string, data: Record<string, unknown>) => void
}

function NodeConfigForm({ node, nodes, edges, onClose, onSave }: Props) {
  const [form] = Form.useForm()
  const { t } = useTranslation()
  const [inputMappings, setInputMappings] = useState<InputMapping[]>(
    (node?.data?.inputMappings as InputMapping[]) ?? [],
  )

  useEffect(() => {
    form.setFieldsValue(node?.data)
  }, [node, form])

  const handleSave = () => {
    form.validateFields().then((values) => {
      if (node) onSave(node.id, { ...values, inputMappings })
      onClose()
    })
  }

  const type = node?.type ?? ''
  const nodeLabel = t('workflow.nodeLabel.' + type, { defaultValue: type })
  const currentNodeId = node?.id ?? ''
  const outputs = node ? getNodeOutputs(node) : []

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
        {/* Input mapping section — for all nodes that accept inputs */}
        {NODES_WITH_INPUT_MAPPING.has(type) && (
          <Form.Item>
            <InputMappingEditor
              nodes={nodes}
              edges={edges}
              currentNodeId={currentNodeId}
              value={inputMappings}
              onChange={setInputMappings}
            />
          </Form.Item>
        )}

        {type === 'start' && (
          <Form.Item label={t('workflow.nodeConfig.inputVariables')}>
            <StartVariablesEditor />
          </Form.Item>
        )}

        {type === 'end' && (
          <>
            <Form.Item
              name="outputVariable"
              label={t('workflow.nodeConfig.outputVariable')}
              initialValue="output"
            >
              <Input placeholder="output" />
            </Form.Item>
            <Form.Item
              name="outputMode"
              label={t('workflow.nodeConfig.outputMode')}
              initialValue="模板渲染"
            >
              <Select
                options={[
                  { label: t('workflow.nodeConfig.outputModeTemplate'), value: '模板渲染' },
                  { label: t('workflow.nodeConfig.outputModeVariable'), value: '变量映射' },
                ]}
              />
            </Form.Item>
            <Form.Item name="outputTemplate" label={t('workflow.nodeConfig.outputTemplate')}>
              <Input.TextArea
                rows={4}
                placeholder="{{llm.output}}"
                style={{ fontFamily: 'var(--kf-font-mono)', fontSize: 12 }}
              />
            </Form.Item>
          </>
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
                rows={4}
                placeholder={t('workflow.nodeConfig.promptPlaceholder')}
                style={{ fontFamily: 'var(--kf-font-mono)', fontSize: 12 }}
              />
            </Form.Item>
            <Form.Item name="prompt" label={t('workflow.nodeConfig.prompt')}>
              <Input.TextArea
                rows={3}
                placeholder="{{start.query}}"
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
            <Form.Item name="query" label={t('workflow.nodeConfig.query')}>
              <Input.TextArea
                rows={2}
                placeholder="{{start.query}}"
                style={{ fontFamily: 'var(--kf-font-mono)', fontSize: 12 }}
              />
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
                placeholder="// input.xxx 可引用映射的变量&#10;// return result"
                style={{ fontFamily: 'var(--kf-font-mono)', fontSize: 12 }}
              />
            </Form.Item>
          </>
        )}

        {type === 'condition' && (
          <Form.Item
            name="conditionExpr"
            label={t('workflow.nodeConfig.conditionExpr')}
            initialValue='{{query}} != ""'
          >
            <Input.TextArea
              rows={2}
              placeholder='{{llm.output}} contains "yes"'
              style={{ fontFamily: 'var(--kf-font-mono)', fontSize: 12 }}
            />
          </Form.Item>
        )}

        {type === 'http' && (
          <>
            <Form.Item name="method" label={t('workflow.nodeConfig.method')} initialValue="GET">
              <Select options={HTTP_METHODS} />
            </Form.Item>
            <Form.Item name="url" label={t('workflow.nodeConfig.url')} rules={[{ required: true }]}>
              <Input.TextArea
                rows={2}
                placeholder="https://api.example.com/{{start.path}}"
                style={{ fontFamily: 'var(--kf-font-mono)', fontSize: 12 }}
              />
            </Form.Item>
            <Form.Item label={t('workflow.nodeConfig.headers')}>
              <HttpHeadersEditor />
            </Form.Item>
            <Form.Item name="body" label={t('workflow.nodeConfig.body')}>
              <Input.TextArea
                rows={4}
                placeholder={'{"query": "{{llm.output}}"}'}
                style={{ fontFamily: 'var(--kf-font-mono)', fontSize: 12 }}
              />
            </Form.Item>
            <Form.Item name="timeout" label={t('workflow.nodeConfig.timeout')} initialValue={30}>
              <InputNumber min={1} max={300} style={{ width: '100%' }} />
            </Form.Item>
          </>
        )}

        {type === 'loop' && (
          <>
            <Form.Item name="mode" label={t('workflow.nodeConfig.loopMode')} initialValue="count">
              <Select
                options={[
                  { label: t('workflow.nodeConfig.loopCount'), value: 'count' },
                  { label: t('workflow.nodeConfig.loopArray'), value: 'array' },
                ]}
              />
            </Form.Item>
            <Form.Item noStyle shouldUpdate={(_f, v) => v.mode !== 'array'}>
              {({ getFieldValue }) =>
                getFieldValue('mode') !== 'array' && (
                  <Form.Item
                    name="count"
                    label={t('workflow.nodeConfig.loopCount')}
                    initialValue={3}
                  >
                    <InputNumber min={1} max={1000} style={{ width: '100%' }} />
                  </Form.Item>
                )
              }
            </Form.Item>
            <Form.Item noStyle shouldUpdate={(_f, v) => v.mode === 'array'}>
              {({ getFieldValue }) =>
                getFieldValue('mode') === 'array' && (
                  <Form.Item name="arrayVariable" label={t('workflow.nodeConfig.loopArray')}>
                    <Input
                      placeholder="{{kb.documents}}"
                      style={{ fontFamily: 'var(--kf-font-mono)', fontSize: 12 }}
                    />
                  </Form.Item>
                )
              }
            </Form.Item>
            <Form.Item
              name="maxIterations"
              label={t('workflow.nodeConfig.maxIterations')}
              initialValue={100}
            >
              <InputNumber min={1} max={10000} style={{ width: '100%' }} />
            </Form.Item>
          </>
        )}

        {type === 'variable' && (
          <>
            <Form.Item
              name="key"
              label={t('workflow.nodeConfig.varKey')}
              rules={[{ required: true }]}
            >
              <Input placeholder={t('workflow.nodeConfig.varKeyPlaceholder')} />
            </Form.Item>
            <Form.Item name="value" label={t('workflow.nodeConfig.varValue')}>
              <Input.TextArea
                rows={4}
                placeholder="{{llm.output}}"
                style={{ fontFamily: 'var(--kf-font-mono)', fontSize: 12 }}
              />
            </Form.Item>
          </>
        )}

        {type === 'agent_call' && (
          <>
            <Form.Item
              name="agentId"
              label={t('workflow.nodeConfig.agentId')}
              rules={[{ required: true }]}
            >
              <InputNumber min={1} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="query" label={t('workflow.nodeConfig.query')}>
              <Input.TextArea
                rows={2}
                placeholder="{{start.query}}"
                style={{ fontFamily: 'var(--kf-font-mono)', fontSize: 12 }}
              />
            </Form.Item>
          </>
        )}

        {type === 'delay' && (
          <Form.Item
            name="seconds"
            label={t('workflow.nodeConfig.seconds')}
            initialValue={1}
            rules={[{ required: true }]}
          >
            <InputNumber min={1} max={300} style={{ width: '100%' }} />
          </Form.Item>
        )}

        {/* Output variables display */}
        {outputs.length > 0 && (
          <>
            <Divider style={{ margin: '12px 0' }} />
            <div>
              <Text type="secondary" style={{ fontSize: 12, fontWeight: 600 }}>
                {t('workflow.nodeConfig.outputs')}
              </Text>
              <div style={{ marginTop: 6, display: 'flex', flexWrap: 'wrap', gap: 4 }}>
                {outputs.map((out) => (
                  <Tag key={out.key} style={{ fontSize: 11 }}>
                    <Text code style={{ fontSize: 11 }}>
                      {out.key}
                    </Text>
                    <Text type="secondary" style={{ fontSize: 11, marginLeft: 4 }}>
                      {out.label}
                    </Text>
                  </Tag>
                ))}
              </div>
            </div>
          </>
        )}
      </Form>
    </Drawer>
  )
}

export default function NodeConfigDrawer({ node, ...props }: Props) {
  if (!node) {
    return (
      <Drawer open={false} onClose={props.onClose} width={360}>
        <Form layout="vertical" />
      </Drawer>
    )
  }
  return <NodeConfigForm key={node.id} node={node} {...props} />
}

// Variable list editor for Start node
function StartVariablesEditor() {
  const { t } = useTranslation()
  const [form] = Form.useForm()
  const existing = (form.getFieldValue('variables') as StartNodeVariable[] | undefined) ?? [
    { name: 'query', value: '' },
  ]
  const [variables, setVariables] = useState<StartNodeVariable[]>(existing)

  const add = () => {
    const next = [...variables, { name: '', value: '' }]
    setVariables(next)
    form.setFieldValue('variables', next)
  }

  const remove = (i: number) => {
    const next = variables.filter((_, idx) => idx !== i)
    setVariables(next)
    form.setFieldValue('variables', next)
  }

  const update = (i: number, field: 'name' | 'value', val: string) => {
    const next = variables.map((v, idx) => (idx === i ? { ...v, [field]: val } : v))
    setVariables(next)
    form.setFieldValue('variables', next)
  }

  return (
    <div>
      {variables.map((v, i) => (
        <div key={i} style={{ display: 'flex', gap: 6, marginBottom: 6 }}>
          <Input
            size="small"
            placeholder={t('workflow.nodeConfig.varName')}
            value={v.name}
            onChange={(e) => update(i, 'name', e.target.value)}
            style={{ flex: 1 }}
          />
          <Input
            size="small"
            placeholder={t('workflow.nodeConfig.varDefaultValue')}
            value={v.value}
            onChange={(e) => update(i, 'value', e.target.value)}
            style={{ flex: 1 }}
          />
          <Button size="small" danger icon={<DeleteOutlined />} onClick={() => remove(i)} />
        </div>
      ))}
      <Button size="small" icon={<PlusOutlined />} onClick={add}>
        {t('workflow.nodeConfig.addVariable')}
      </Button>
    </div>
  )
}

// Dynamic key-value list for HTTP headers
function HttpHeadersEditor() {
  const { t } = useTranslation()
  const [form] = Form.useForm()
  const existingHeaders =
    (form.getFieldValue('headers') as Array<{ key: string; value: string }> | undefined) ?? []
  const [headers, setHeaders] = useState<Array<{ key: string; value: string }>>(existingHeaders)

  const add = () => {
    const next = [...headers, { key: '', value: '' }]
    setHeaders(next)
    form.setFieldValue('headers', next)
  }

  const remove = (i: number) => {
    const next = headers.filter((_, idx) => idx !== i)
    setHeaders(next)
    form.setFieldValue('headers', next)
  }

  const update = (i: number, field: 'key' | 'value', val: string) => {
    const next = headers.map((h, idx) => (idx === i ? { ...h, [field]: val } : h))
    setHeaders(next)
    form.setFieldValue('headers', next)
  }

  return (
    <div>
      {headers.map((h, i) => (
        <div key={i} style={{ display: 'flex', gap: 6, marginBottom: 6 }}>
          <Input
            size="small"
            placeholder={t('workflow.nodeConfig.headerKey')}
            value={h.key}
            onChange={(e) => update(i, 'key', e.target.value)}
            style={{ flex: 1 }}
          />
          <Input
            size="small"
            placeholder={t('workflow.nodeConfig.headerValue')}
            value={h.value}
            onChange={(e) => update(i, 'value', e.target.value)}
            style={{ flex: 1 }}
          />
          <Button size="small" danger icon={<DeleteOutlined />} onClick={() => remove(i)} />
        </div>
      ))}
      <Button size="small" icon={<PlusOutlined />} onClick={add}>
        {t('workflow.nodeConfig.addHeader')}
      </Button>
    </div>
  )
}
