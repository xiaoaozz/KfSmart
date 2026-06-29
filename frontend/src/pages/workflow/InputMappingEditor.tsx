import { useMemo } from 'react'
import { Divider, Switch, Input, Typography, Empty } from 'antd'
import type { Node, Edge } from '@xyflow/react'
import { useTranslation } from 'react-i18next'
import { getNodeOutputs, type NodeOutputDef } from './nodeSchema'
import type { InputMapping } from '@/types/workflow'

const { Text } = Typography

interface Props {
  nodes: Node[]
  edges: Edge[]
  currentNodeId: string
  value?: InputMapping[]
  onChange: (mappings: InputMapping[]) => void
}

interface UpstreamVar {
  nodeId: string
  nodeLabel: string
  output: NodeOutputDef
}

export default function InputMappingEditor({
  nodes,
  edges,
  currentNodeId,
  value = [],
  onChange,
}: Props) {
  const { t } = useTranslation()

  const upstreamVars = useMemo<UpstreamVar[]>(() => {
    const upstreamNodeIds = edges
      .filter((e) => e.target === currentNodeId)
      .map((e) => e.source)
      .filter((id, idx, arr) => arr.indexOf(id) === idx)

    const result: UpstreamVar[] = []
    for (const nodeId of upstreamNodeIds) {
      const node = nodes.find((n) => n.id === nodeId)
      if (!node) continue
      const outputs = getNodeOutputs(node)
      const nodeLabel = (node.data?.label as string) || node.id
      for (const output of outputs) {
        result.push({ nodeId, nodeLabel, output })
      }
    }
    return result
  }, [nodes, edges, currentNodeId])

  if (upstreamVars.length === 0) {
    return (
      <>
        <Divider style={{ margin: '8px 0' }} />
        <Text type="secondary" style={{ fontSize: 12, fontWeight: 600 }}>
          {t('workflow.nodeConfig.inputMapping')}
        </Text>
        <div style={{ marginTop: 8 }}>
          <Empty
            description={t('workflow.nodeConfig.noUpstream')}
            image={Empty.PRESENTED_IMAGE_SIMPLE}
          />
        </div>
      </>
    )
  }

  const getMapping = (source: string): InputMapping | undefined =>
    value.find((m) => m.source === source)

  const updateMapping = (source: string, updates: Partial<InputMapping>) => {
    const existing = getMapping(source)
    if (existing) {
      onChange(value.map((m) => (m.source === source ? { ...m, ...updates } : m)))
    } else {
      onChange([...value, { source, param: '', enabled: false, ...updates }])
    }
  }

  let lastNodeLabel = ''

  return (
    <>
      <Divider style={{ margin: '8px 0' }} />
      <Text type="secondary" style={{ fontSize: 12, fontWeight: 600 }}>
        {t('workflow.nodeConfig.inputMapping')}
      </Text>
      <div style={{ marginTop: 8, display: 'flex', flexDirection: 'column', gap: 6 }}>
        {upstreamVars.map(({ nodeId, nodeLabel, output }) => {
          const source = `${nodeId}.${output.key}`
          const mapping = getMapping(source)
          const enabled = mapping?.enabled ?? false
          const param = mapping?.param ?? output.key
          const showHeader = nodeLabel !== lastNodeLabel
          lastNodeLabel = nodeLabel

          return (
            <div key={source}>
              {showHeader && (
                <Text type="secondary" style={{ fontSize: 11, display: 'block', marginBottom: 2 }}>
                  {t('workflow.nodeConfig.upstreamNode')}: {nodeLabel}
                </Text>
              )}
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 8,
                  padding: '4px 0',
                  opacity: enabled ? 1 : 0.5,
                }}
              >
                <Text code style={{ fontSize: 11, minWidth: 60, flex: '0 0 auto' }}>
                  {output.key}
                </Text>
                <Input
                  size="small"
                  placeholder={t('workflow.nodeConfig.mapTo')}
                  value={param}
                  disabled={!enabled}
                  onChange={(e) => updateMapping(source, { param: e.target.value })}
                  style={{ flex: 1 }}
                />
                <Switch
                  size="small"
                  checked={enabled}
                  onChange={(checked) =>
                    updateMapping(source, { enabled: checked, param: param || output.key })
                  }
                />
              </div>
            </div>
          )
        })}
      </div>
    </>
  )
}
