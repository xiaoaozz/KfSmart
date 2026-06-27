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
import { workflowApi } from '@/api/workflow'
import type { WorkflowNode, WorkflowEdge } from '@/types/workflow'
import StartNode from './nodes/StartNode'
import EndNode from './nodes/EndNode'
import LlmNode from './nodes/LlmNode'
import KbNode from './nodes/KbNode'
import CodeNode from './nodes/CodeNode'
import ConditionNode from './nodes/ConditionNode'
import NodeConfigDrawer from './NodeConfigDrawer'
import { NODE_LABELS, NODE_COLORS } from './nodes/nodeTypes'
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

const NODE_PALETTE: Array<{ type: string }> = [
  { type: 'llm' },
  { type: 'kb' },
  { type: 'code' },
  { type: 'condition' },
]

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
      message.success('已保存')
    },
  })

  const runMutation = useMutation({
    mutationFn: () => workflowApi.run(Number(id), '（测试运行）'),
    onSuccess: () => message.success('运行已触发'),
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
    const id = selectedNode.id
    if (id === 'start-1' || id === 'end-1') {
      message.warning('不能删除开始/结束节点')
      return
    }
    setNodes((nds) => nds.filter((n) => n.id !== id))
    setEdges((eds) => eds.filter((e) => e.source !== id && e.target !== id))
    setSelectedNode(null)
  }, [selectedNode, setNodes, setEdges, message])

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
      {/* Top bar */}
      <div className={styles.topBar}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/workflows')}>
          返回
        </Button>
        <span className={styles.wfName}>{wf?.name ?? '工作流编辑器'}</span>
        <Space>
          <Tooltip title="保存 (Ctrl+S)">
            <Button
              icon={<SaveOutlined />}
              loading={saveMutation.isPending}
              onClick={() => saveMutation.mutate()}
              type="primary"
              style={{ background: 'var(--kf-accent-gradient-r)', border: 'none' }}
            >
              保存
            </Button>
          </Tooltip>
          <Tooltip title="运行测试">
            <Button
              icon={<PlayCircleOutlined />}
              loading={runMutation.isPending}
              onClick={() => runMutation.mutate()}
            >
              运行
            </Button>
          </Tooltip>
        </Space>
      </div>

      <div className={styles.canvas}>
        {/* Sidebar node palette */}
        <div className={styles.palette}>
          <div className={styles.paletteTitle}>节点</div>
          {NODE_PALETTE.map(({ type }) => (
            <button
              key={type}
              className={styles.paletteItem}
              style={{ '--node-color': NODE_COLORS[type] } as React.CSSProperties}
              onClick={() => addNode(type)}
            >
              <span className={styles.paletteDot} />
              {NODE_LABELS[type]}
            </button>
          ))}
          <Divider style={{ margin: '8px 0' }} />
          <Tooltip title="删除选中节点">
            <button
              className={`${styles.paletteItem} ${styles.paletteDelete}`}
              onClick={deleteSelectedNode}
              disabled={!selectedNode}
            >
              <DeleteOutlined /> 删除节点
            </button>
          </Tooltip>
        </div>

        {/* React Flow canvas */}
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
                <Tooltip title="撤销">
                  <Button size="small" icon={<UndoOutlined />} />
                </Tooltip>
                <Tooltip title="重做">
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
