import { useState } from 'react'
import { Tree, Button, Modal, Form, Input, App, Space, Tooltip, Empty } from 'antd'
import { PlusOutlined, EditOutlined, DeleteOutlined, ApartmentOutlined } from '@ant-design/icons'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import type { DataNode } from 'antd/es/tree'
import { adminOrgApi, type OrgTag } from '@/api/admin'
import styles from './AdminPage.module.css'

function toTreeData(nodes: OrgTag[]): DataNode[] {
  return nodes.map((n) => ({
    key: n.id,
    title: (
      <span>
        {n.name}
        <span style={{ fontSize: 12, color: 'var(--kf-muted-foreground)', marginLeft: 6 }}>
          ({n.code}) · {n.userCount} 用户
        </span>
      </span>
    ),
    children: n.children ? toTreeData(n.children) : undefined,
  }))
}

export default function OrgTagPage() {
  const qc = useQueryClient()
  const { message } = App.useApp()
  const [formOpen, setFormOpen] = useState(false)
  const [editTarget, setEditTarget] = useState<OrgTag | null>(null)
  const [parentId, setParentId] = useState<number | undefined>()
  const [form] = Form.useForm<{ name: string; code: string; description?: string }>()

  const { data: tree } = useQuery({
    queryKey: ['admin-org-tree'],
    queryFn: () => adminOrgApi.tree(),
  })

  const createMutation = useMutation({
    mutationFn: (v: { name: string; code: string; description?: string }) =>
      adminOrgApi.create({ ...v, parentId }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin-org-tree'] })
      setFormOpen(false)
      form.resetFields()
      message.success('已创建')
    },
  })

  const updateMutation = useMutation({
    mutationFn: (v: { name: string; code: string; description?: string }) =>
      adminOrgApi.update(editTarget!.id, v),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin-org-tree'] })
      setFormOpen(false)
      setEditTarget(null)
      message.success('已更新')
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => adminOrgApi.delete(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin-org-tree'] })
      message.success('已删除')
    },
  })

  const openCreate = (pid?: number) => {
    setEditTarget(null)
    setParentId(pid)
    form.resetFields()
    setFormOpen(true)
  }

  const openEdit = (org: OrgTag) => {
    setEditTarget(org)
    form.setFieldsValue({ name: org.name, code: org.code, description: org.description })
    setFormOpen(true)
  }

  // Find a flat org by id for delete confirmation
  const findOrg = (nodes: OrgTag[], id: number): OrgTag | undefined => {
    for (const n of nodes) {
      if (n.id === id) return n
      if (n.children) {
        const found = findOrg(n.children, id)
        if (found) return found
      }
    }
  }

  const handleDelete = (key: React.Key) => {
    const org = findOrg(tree ?? [], Number(key))
    if (!org) return
    deleteMutation.mutate(org.id)
  }

  const treeData = toTreeData(tree ?? [])

  return (
    <div className={styles.root}>
      <div className={styles.topBar}>
        <h2 className={styles.pageTitle}>
          <ApartmentOutlined /> 组织标签
        </h2>
        <Space>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            style={{ background: 'var(--kf-accent-gradient-r)', border: 'none' }}
            onClick={() => openCreate()}
          >
            新建根节点
          </Button>
        </Space>
      </div>

      <div className={styles.orgTree}>
        {!treeData.length ? (
          <Empty description="暂无组织结构" />
        ) : (
          <Tree
            treeData={treeData}
            defaultExpandAll
            titleRender={(node) => (
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '2px 0' }}>
                <span style={{ flex: 1 }}>{node.title as React.ReactNode}</span>
                <Tooltip title="添加子节点">
                  <Button
                    size="small"
                    type="text"
                    icon={<PlusOutlined />}
                    onClick={(e) => {
                      e.stopPropagation()
                      openCreate(Number(node.key))
                    }}
                  />
                </Tooltip>
                <Tooltip title="编辑">
                  <Button
                    size="small"
                    type="text"
                    icon={<EditOutlined />}
                    onClick={(e) => {
                      e.stopPropagation()
                      const org = findOrg(tree ?? [], Number(node.key))
                      if (org) openEdit(org)
                    }}
                  />
                </Tooltip>
                <Tooltip title="删除">
                  <Button
                    size="small"
                    type="text"
                    danger
                    icon={<DeleteOutlined />}
                    onClick={(e) => {
                      e.stopPropagation()
                      handleDelete(node.key)
                    }}
                  />
                </Tooltip>
              </div>
            )}
          />
        )}
      </div>

      <Modal
        title={editTarget ? `编辑 — ${editTarget.name}` : '新建组织节点'}
        open={formOpen}
        onCancel={() => {
          setFormOpen(false)
          form.resetFields()
          setEditTarget(null)
        }}
        onOk={() => form.submit()}
        confirmLoading={createMutation.isPending || updateMutation.isPending}
        destroyOnClose
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={(v) => (editTarget ? updateMutation.mutate(v) : createMutation.mutate(v))}
        >
          <Form.Item name="name" label="名称" rules={[{ required: true }]}>
            <Input placeholder="例：研发部" />
          </Form.Item>
          <Form.Item
            name="code"
            label="编码"
            rules={[
              {
                required: true,
                pattern: /^[a-z0-9_-]+$/,
                message: '只允许小写字母、数字、下划线和横线',
              },
            ]}
          >
            <Input placeholder="例：rd-dept" style={{ fontFamily: 'var(--kf-font-mono)' }} />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input placeholder="可选" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
