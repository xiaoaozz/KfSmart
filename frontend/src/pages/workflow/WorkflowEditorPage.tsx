import { useState, useCallback, useRef } from 'react'
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  addEdge,
  useNodesState,
  useEdgesState,
  BackgroundVariant,
  type Node,
  type Edge,
  type Connection,
  type NodeTypes,
  Panel,
} from '@xyflow/react'
import '@xyflow/react/dist/style.css'
import { Button, App, Tooltip, Divider, Space } from 'antd'
import {
  ArrowLeftOutlined,
  SaveOutlined,
  PlayCircleOutlined,
  DeleteOutlined,
  UndoOutlined,
  RedoOutlined,
} from '@ant-design/icons'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useParams, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { workflowApi } from '@/api/workflow'
import type { WorkflowNode, WorkflowEdge } from '@/types/workflow'
import StartNode from './nodes/StartNode'
import EndNode from './nodes/EndNode'
import LlmNode from './nodes/LlmNode'
import KbNode from './nodes/KbNode'
import CodeNode from './nodes/CodeNode'
import ConditionNode from './nodes/ConditionNode'
import NodeConfigDrawer from './NodeConfigDrawer'
import { NODE_COLORS } from './nodes/nodeTypes'
import styles from './WorkflowEditorPage.module.css'

const NODE_TYPES: NodeTypes = {
  start: StartNode,
  end: EndNode,
  llm: LlmNode,
  kb: KbNode,
  code: CodeNode,
  condition: ConditionNode,
}

const DEFAULT_NODES: Node[] = [
  { id: 'start-1', type: 'start', position: { x: 200, y: 80 }, data: { inputVariable: 'input' } },
  { id: 'end-1', type: 'end', position: { x: 200, y: 420 }, data: { outputVariable: 'output' } },
]

const DEFAULT_EDGES: Edge[] = [{ id: 'e-start-end', source: 'start-1', target: 'end-1' }]

const NODE_PALETTE_TYPES = ['llm', 'kb', 'code', 'condition']

let nodeIdCounter = 100

function toFlowNodes(wfNodes: WorkflowNode[]): Node[] {
  return wfNodes.map((n) => ({
    id: n.id,
    type: n.type,
    position: n.position,
    data: n.data,
  }))
}

function toFlowEdges(wfEdges: WorkflowEdge[]): Edge[] {
  return wfEdges.map((e) => ({
    id: e.id,
    source: e.source,
    target: e.target,
    sourceHandle: e.sourceHandle,
    targetHandle: e.targetHandle,
    label: e.label,
  }))
}

export default function WorkflowEditorPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const { message } = App.useApp()
  const { t } = useTranslation()
  const reactFlowWrapper = useRef<HTMLDivElement>(null)

  const [nodes, setNodes, onNodesChange] = useNodesState<Node>(DEFAULT_NODES)
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>(DEFAULT_EDGES)
  const [selectedNode, setSelectedNode] = useState<Node | null>(null)
  const [initialized, setInitialized] = useState(false)

  useQuery({
    queryKey: ['workflows', id],
    queryFn: () => workflowApi.get(Number(id)),
    enabled: !!id && !initialized,
    select: (wf) => {
      if (wf.nodes.length > 0) {
        setNodes(toFlowNodes(wf.nodes))
        setEdges(toFlowEdges(wf.edges))
      }
      setInitialized(true)
      return wf
    },
  })

  const { data: wf } = useQuery({
    queryKey: ['workflows', id],
    queryFn: () => workflowApi.get(Number(id)),
    enabled: !!id,
  })

  const saveMutation = useMutation({
    mutationFn: () =>
      workflowApi.saveGraph(Number(id), {
        nodes: nodes.map((n) => ({
          id: n.id,
          type: n.type as WorkflowNode['type'],
          position: n.position,
          data: n.data as Record<string, unknown>,
        })),
        edges: edges.map((e) => ({
          id: e.id,
          source: e.source,
          target: e.target,
          sourceHandle: e.sourceHandle ?? undefined,
          targetHandle: e.targetHandle ?? undefined,
          label: typeof e.label === 'string' ? e.label : undefined,
        })),
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['workflows'] })
      message.success(t('workflow.editor.saveSuccess'))
    },
  })

  const runMutation = useMutation({
    mutationFn: () => workflowApi.run(Number(id), ''),
    onSuccess: () => message.success(t('workflow.editor.runTriggered')),
  })

  const onConnect = useCallback(
    (connection: Connection) => setEdges((eds) => addEdge(connection, eds)),
    [setEdges],
  )

  const onNodeClick = useCallback((_: React.MouseEvent, node: Node) => {
    setSelectedNode(node)
  }, [])

  const onPaneClick = useCallback(() => {
    setSelectedNode(null)
  }, [])

  const onSaveNodeConfig = useCallback(
    (nodeId: string, data: Record<string, unknown>) => {
      setNodes((nds) => nds.map((n) => (n.id === nodeId ? { ...n, data } : n)))
    },
    [setNodes],
  )

  const deleteSelectedNode = useCallback(() => {
    if (!selectedNode) return
    const nodeId = selectedNode.id
    if (nodeId === 'start-1' || nodeId === 'end-1') {
      message.warning(t('workflow.editor.cannotDeleteFixed'))
      return
    }
    setNodes((nds) => nds.filter((n) => n.id !== nodeId))
    setEdges((eds) => eds.filter((e) => e.source !== nodeId && e.target !== nodeId))
    setSelectedNode(null)
  }, [selectedNode, setNodes, setEdges, message, t])

  const addNode = useCallback(
    (type: string) => {
      const newId = `${type}-${++nodeIdCounter}`
      const newNode: Node = {
        id: newId,
        type,
        position: { x: 200 + Math.random() * 100, y: 200 + Math.random() * 100 },
        data: {},
      }
      setNodes((nds) => [...nds, newNode])
      setSelectedNode(newNode)
    },
    [setNodes],
  )

  return (
    <div className={styles.root}>
      <div className={styles.topBar}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/workflows')}>
          {t('workflow.editor.backBtn')}
        </Button>
        <span className={styles.wfName}>{wf?.name ?? t('workflow.editor.defaultTitle')}</span>
        <Space>
          <Tooltip title={t('workflow.editor.saveTooltip')}>
            <Button
              icon={<SaveOutlined />}
              loading={saveMutation.isPending}
              onClick={() => saveMutation.mutate()}
              type="primary"
              style={{ background: 'var(--kf-accent-gradient-r)', border: 'none' }}
            >
              {t('workflow.editor.saveBtn')}
            </Button>
          </Tooltip>
          <Tooltip title={t('workflow.editor.runTooltip')}>
            <Button
              icon={<PlayCircleOutlined />}
              loading={runMutation.isPending}
              onClick={() => runMutation.mutate()}
            >
              {t('workflow.editor.runBtn')}
            </Button>
          </Tooltip>
        </Space>
      </div>

      <div className={styles.canvas}>
        <div className={styles.palette}>
          <div className={styles.paletteTitle}>{t('workflow.editor.paletteTitle')}</div>
          {NODE_PALETTE_TYPES.map((type) => (
            <button
              key={type}
              className={styles.paletteItem}
              style={{ '--node-color': NODE_COLORS[type] } as React.CSSProperties}
              onClick={() => addNode(type)}
            >
              <span className={styles.paletteDot} />
              {t('workflow.nodeLabel.' + type, { defaultValue: type })}
            </button>
          ))}
          <Divider style={{ margin: '8px 0' }} />
          <Tooltip title={t('workflow.editor.deleteNodeTooltip')}>
            <button
              className={`${styles.paletteItem} ${styles.paletteDelete}`}
              onClick={deleteSelectedNode}
              disabled={!selectedNode}
            >
              <DeleteOutlined /> {t('workflow.editor.deleteNodeBtn')}
            </button>
          </Tooltip>
        </div>

        <div className={styles.flow} ref={reactFlowWrapper}>
          <ReactFlow
            nodes={nodes}
            edges={edges}
            onNodesChange={onNodesChange}
            onEdgesChange={onEdgesChange}
            onConnect={onConnect}
            onNodeClick={onNodeClick}
            onPaneClick={onPaneClick}
            nodeTypes={NODE_TYPES}
            fitView
            defaultEdgeOptions={{
              animated: true,
              style: { stroke: 'var(--kf-primary)', strokeWidth: 2 },
            }}
            proOptions={{ hideAttribution: true }}
          >
            <Background variant={BackgroundVariant.Dots} gap={16} color="var(--kf-border)" />
            <Controls />
            <MiniMap
              nodeStrokeColor={(n) => NODE_COLORS[n.type ?? ''] ?? '#aaa'}
              nodeColor={(n) => NODE_COLORS[n.type ?? ''] ?? '#aaa'}
              style={{ background: 'var(--kf-card)', border: '1px solid var(--kf-border)' }}
            />
            <Panel position="top-right">
              <Space>
                <Tooltip title={t('workflow.editor.undoTooltip')}>
                  <Button size="small" icon={<UndoOutlined />} />
                </Tooltip>
                <Tooltip title={t('workflow.editor.redoTooltip')}>
                  <Button size="small" icon={<RedoOutlined />} />
                </Tooltip>
              </Space>
            </Panel>
          </ReactFlow>
        </div>
      </div>

      <NodeConfigDrawer
        node={selectedNode}
        onClose={() => setSelectedNode(null)}
        onSave={onSaveNodeConfig}
      />
    </div>
  )
}
