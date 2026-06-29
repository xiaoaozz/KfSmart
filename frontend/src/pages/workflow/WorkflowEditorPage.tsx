import { useState, useCallback, useRef, useEffect } from 'react'
import {
  ReactFlow,
  Background,
  Controls,
  ControlButton,
  MiniMap,
  addEdge,
  useNodesState,
  useEdgesState,
  useViewport,
  useReactFlow,
  useStore,
  BackgroundVariant,
  MarkerType,
  type Node,
  type Edge,
  type Connection,
  type NodeTypes,
  type OnNodesChange,
  type OnEdgesChange,
  Panel,
  ConnectionMode,
} from '@xyflow/react'
import { Button, App, Tooltip, Divider, Space, Modal, Form, Input } from 'antd'
import {
  ArrowLeftOutlined,
  SaveOutlined,
  PlayCircleOutlined,
  DeleteOutlined,
  UndoOutlined,
  RedoOutlined,
  HistoryOutlined,
  BugOutlined,
  ZoomInOutlined,
  ZoomOutOutlined,
  FullscreenOutlined,
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
import HttpNode from './nodes/HttpNode'
import LoopNode from './nodes/LoopNode'
import VariableNode from './nodes/VariableNode'
import AgentCallNode from './nodes/AgentCallNode'
import DelayNode from './nodes/DelayNode'
import NodeConfigDrawer from './NodeConfigDrawer'
import WorkflowDebugPanel from './WorkflowDebugPanel'
import { NODE_COLORS } from './nodes/nodeTypes'
import { NodeStatusContext } from './nodes/NodeStatusContext'
import styles from './WorkflowEditorPage.module.css'

const NODE_TYPES: NodeTypes = {
  start: StartNode,
  end: EndNode,
  llm: LlmNode,
  kb: KbNode,
  code: CodeNode,
  condition: ConditionNode,
  http: HttpNode,
  loop: LoopNode,
  variable: VariableNode,
  agent_call: AgentCallNode,
  delay: DelayNode,
}

const DEFAULT_NODES: Node[] = [
  { id: 'start-1', type: 'start', position: { x: 200, y: 80 }, data: { inputVariable: 'input' } },
  { id: 'end-1', type: 'end', position: { x: 200, y: 300 }, data: { outputVariable: 'output' } },
]

const DEFAULT_EDGES: Edge[] = [{ id: 'e-start-end', source: 'start-1', target: 'end-1' }]

const NODE_PALETTE_TYPES = [
  'llm',
  'kb',
  'code',
  'condition',
  'http',
  'loop',
  'variable',
  'agent_call',
  'delay',
]

const DEFAULT_EDGE_OPTIONS = {
  animated: true,
  style: { stroke: 'var(--kf-accent)', strokeWidth: 2 },
  markerEnd: { type: MarkerType.ArrowClosed, color: 'var(--kf-accent)' },
}

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

// Zoom display embedded inside the built-in Controls panel to avoid overlap
function ZoomDisplay() {
  const { zoom } = useViewport()
  const { zoomIn, zoomOut, fitView } = useReactFlow()
  const minZoom = useStore((s) => s.minZoom)
  const maxZoom = useStore((s) => s.maxZoom)
  const minZoomReached = zoom <= minZoom
  const maxZoomReached = zoom >= maxZoom
  return (
    <>
      <ControlButton
        title="Zoom in"
        onClick={() => zoomIn({ duration: 200 })}
        disabled={maxZoomReached}
      >
        <ZoomInOutlined style={{ pointerEvents: 'none' }} />
      </ControlButton>
      <div className={styles.zoomPct}>{Math.round(zoom * 100)}%</div>
      <ControlButton
        title="Zoom out"
        onClick={() => zoomOut({ duration: 200 })}
        disabled={minZoomReached}
      >
        <ZoomOutOutlined style={{ pointerEvents: 'none' }} />
      </ControlButton>
      <ControlButton
        title="Fit view"
        onClick={() => fitView({ duration: 300, padding: 0.15, minZoom: 1, maxZoom: 1 })}
      >
        <FullscreenOutlined style={{ pointerEvents: 'none' }} />
      </ControlButton>
    </>
  )
}

export default function WorkflowEditorPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const { message, modal } = App.useApp()
  const { t } = useTranslation()
  const reactFlowWrapper = useRef<HTMLDivElement>(null)

  const [nodes, setNodes, onNodesChange] = useNodesState<Node>(DEFAULT_NODES)
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>(DEFAULT_EDGES)
  const [selectedNode, setSelectedNode] = useState<Node | null>(null)
  const initializedRef = useRef(false)

  // Undo / Redo history
  type Snapshot = { nodes: Node[]; edges: Edge[] }
  const historyRef = useRef<Snapshot[]>([])
  const futureRef = useRef<Snapshot[]>([])
  const [canUndo, setCanUndo] = useState(false)
  const [canRedo, setCanRedo] = useState(false)

  const pushHistory = useCallback((prevNodes: Node[], prevEdges: Edge[]) => {
    historyRef.current.push({ nodes: prevNodes, edges: prevEdges })
    if (historyRef.current.length > 100) historyRef.current.shift()
    futureRef.current = []
    setCanUndo(true)
    setCanRedo(false)
  }, [])

  const undo = useCallback(() => {
    const past = historyRef.current.pop()
    if (!past) return
    futureRef.current.push({ nodes: [...nodes], edges: [...edges] })
    setNodes(past.nodes)
    setEdges(past.edges)
    setCanUndo(historyRef.current.length > 0)
    setCanRedo(true)
  }, [nodes, edges, setNodes, setEdges])

  const redo = useCallback(() => {
    const next = futureRef.current.pop()
    if (!next) return
    historyRef.current.push({ nodes: [...nodes], edges: [...edges] })
    setNodes(next.nodes)
    setEdges(next.edges)
    setCanUndo(true)
    setCanRedo(futureRef.current.length > 0)
  }, [nodes, edges, setNodes, setEdges])

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (!(e.metaKey || e.ctrlKey)) return
      if (e.key === 'z') {
        e.preventDefault()
        if (e.shiftKey) redo()
        else undo()
      } else if (e.key === 'y') {
        e.preventDefault()
        redo()
      }
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [undo, redo])

  const [runDialogOpen, setRunDialogOpen] = useState(false)
  const [runForm] = Form.useForm<{ input: string }>()
  const [debugMode, setDebugMode] = useState(false)
  const [nodeStatusMap, setNodeStatusMap] = useState<Record<string, string>>({})

  const { data: wf } = useQuery({
    queryKey: ['workflows', id],
    queryFn: () => workflowApi.get(Number(id)),
    enabled: !!id,
  })

  useEffect(() => {
    if (!wf || initializedRef.current) return
    if (wf.nodes?.length > 0) {
      setNodes(toFlowNodes(wf.nodes))
      setEdges(toFlowEdges(wf.edges ?? []))
    }
    initializedRef.current = true
  }, [wf, setNodes, setEdges])

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
    mutationFn: (input: string) => workflowApi.run(Number(id), input),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['workflow-executions', id] })
      setRunDialogOpen(false)
      runForm.resetFields()
      modal.success({
        title: t('workflow.executions.runSuccess'),
        content: t('workflow.executions.runSuccess'),
        okText: t('workflow.executions.historyBtn'),
        cancelText: t('common.cancel'),
        onOk: () => navigate(`/workflows/${id}/executions`),
      })
    },
  })

  const onConnect = useCallback(
    (connection: Connection) => {
      pushHistory([...nodes], [...edges])
      setEdges((eds) => addEdge(connection, eds))
    },
    [nodes, edges, setEdges, pushHistory],
  )

  const onNodesChangeFn = useCallback<OnNodesChange>(
    (changes) => {
      onNodesChange(changes)
    },
    [onNodesChange],
  )

  const onEdgesChangeFn = useCallback<OnEdgesChange>(
    (changes) => {
      onEdgesChange(changes)
    },
    [onEdgesChange],
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
    pushHistory([...nodes], [...edges])
    setNodes((nds) => nds.filter((n) => n.id !== nodeId))
    setEdges((eds) => eds.filter((e) => e.source !== nodeId && e.target !== nodeId))
    setSelectedNode(null)
  }, [selectedNode, nodes, edges, setNodes, setEdges, pushHistory, message, t])

  const addNode = useCallback(
    (type: string) => {
      const newId = `${type}-${++nodeIdCounter}`
      const newNode: Node = {
        id: newId,
        type,
        position: { x: 200 + Math.random() * 100, y: 200 + Math.random() * 100 },
        data: {},
      }
      pushHistory([...nodes], [...edges])
      setNodes((nds) => [...nds, newNode])
      setSelectedNode(newNode)
    },
    [nodes, edges, setNodes, pushHistory],
  )

  return (
    <div className={styles.root}>
      <div className={styles.topBar}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/workflows')}>
          {t('workflow.editor.backBtn')}
        </Button>
        <span className={styles.wfName}>{wf?.name ?? t('workflow.editor.defaultTitle')}</span>
        <Space>
          <Tooltip title={t('workflow.debug.title')}>
            <Button
              icon={<BugOutlined />}
              type={debugMode ? 'primary' : 'default'}
              onClick={() => {
                setDebugMode((v) => !v)
                if (debugMode) {
                  setSelectedNode(null)
                  setNodeStatusMap({})
                }
              }}
              style={debugMode ? { background: 'var(--kf-accent-gradient-r)', border: 'none' } : {}}
            >
              {t('workflow.debug.title')}
            </Button>
          </Tooltip>
          <Tooltip title={t('workflow.executions.historyBtn')}>
            <Button
              icon={<HistoryOutlined />}
              onClick={() => navigate(`/workflows/${id}/executions`)}
            >
              {t('workflow.executions.historyBtn')}
            </Button>
          </Tooltip>
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
            <Button icon={<PlayCircleOutlined />} onClick={() => setRunDialogOpen(true)}>
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
          <NodeStatusContext.Provider value={nodeStatusMap}>
            <ReactFlow
              nodes={nodes}
              edges={edges}
              onNodesChange={onNodesChangeFn}
              onEdgesChange={onEdgesChangeFn}
              onConnect={onConnect}
              onNodeClick={onNodeClick}
              onPaneClick={onPaneClick}
              nodeTypes={NODE_TYPES}
              fitView
              fitViewOptions={{ maxZoom: 1, padding: 0.15 }}
              minZoom={0.2}
              maxZoom={2}
              defaultEdgeOptions={DEFAULT_EDGE_OPTIONS}
              connectionLineStyle={{ stroke: 'var(--kf-accent)', strokeWidth: 2 }}
              connectionMode={ConnectionMode.Loose}
              proOptions={{ hideAttribution: true }}
            >
              <Background variant={BackgroundVariant.Dots} gap={16} color="var(--kf-border)" />
              <Controls showZoom={false} showFitView={false} showInteractive={false}>
                <ZoomDisplay />
              </Controls>
              <MiniMap
                nodeStrokeColor={(n) => NODE_COLORS[n.type ?? ''] ?? '#aaa'}
                nodeColor={(n) => NODE_COLORS[n.type ?? ''] ?? '#aaa'}
                style={{ background: 'var(--kf-card)', border: '1px solid var(--kf-border)' }}
              />
              <Panel position="top-right">
                <Space>
                  <Tooltip title={t('workflow.editor.undoTooltip')}>
                    <Button
                      size="small"
                      icon={<UndoOutlined />}
                      disabled={!canUndo}
                      onClick={undo}
                    />
                  </Tooltip>
                  <Tooltip title={t('workflow.editor.redoTooltip')}>
                    <Button
                      size="small"
                      icon={<RedoOutlined />}
                      disabled={!canRedo}
                      onClick={redo}
                    />
                  </Tooltip>
                </Space>
              </Panel>
            </ReactFlow>
          </NodeStatusContext.Provider>
        </div>

        {/* Debug panel as right-side overlay within the canvas */}
        {debugMode && (
          <WorkflowDebugPanel
            nodes={nodes}
            nodeStatusMap={nodeStatusMap}
            onStatusChange={setNodeStatusMap}
            onClose={() => {
              setDebugMode(false)
              setNodeStatusMap({})
            }}
            workflowId={Number(id)}
          />
        )}
      </div>

      <NodeConfigDrawer
        node={selectedNode}
        nodes={nodes}
        edges={edges}
        onClose={() => setSelectedNode(null)}
        onSave={onSaveNodeConfig}
      />

      <Modal
        title={t('workflow.executions.runModalTitle')}
        open={runDialogOpen}
        onCancel={() => {
          setRunDialogOpen(false)
          runForm.resetFields()
        }}
        onOk={() => runForm.submit()}
        okText={t('workflow.executions.runModalOk')}
        confirmLoading={runMutation.isPending}
        destroyOnClose
      >
        <Form form={runForm} layout="vertical" onFinish={(v) => runMutation.mutate(v.input ?? '')}>
          <Form.Item
            name="input"
            label={t('workflow.executions.runInputLabel')}
            rules={[{ required: true, message: t('workflow.executions.runInputPlaceholder') }]}
          >
            <Input.TextArea
              rows={5}
              placeholder={t('workflow.executions.runInputPlaceholder')}
              style={{ fontFamily: 'var(--kf-font-mono)', fontSize: 13 }}
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
