import { useState } from 'react'
import { Tree, Button, Modal, Form, Input, App, Space, Tooltip, Empty } from 'antd'
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import type { DataNode } from 'antd/es/tree'
import { adminOrgApi, type OrgTag } from '@/api/admin'
import styles from './AdminPage.module.css'

interface OrgTagFormValues {
  tagId: string
  name: string
  description?: string
}

function toTreeData(
  nodes: OrgTag[],
  t: (key: string, opts?: Record<string, unknown>) => string,
): DataNode[] {
  return nodes.map((n) => ({
    key: n.tagId,
    title: (
      <span>
        {n.name}
        <span style={{ fontSize: 12, color: 'var(--kf-muted-foreground)', marginLeft: 6 }}>
          ({n.tagId})
        </span>
      </span>
    ),
    children: n.children ? toTreeData(n.children, t) : undefined,
  }))
}

function findOrg(nodes: OrgTag[], tagId: string): OrgTag | undefined {
  for (const n of nodes) {
    if (n.tagId === tagId) return n
    if (n.children) {
      const found = findOrg(n.children, tagId)
      if (found) return found
    }
  }
}

export default function OrgTagPage() {
  const qc = useQueryClient()
  const { message } = App.useApp()
  const { t } = useTranslation()
  const [formOpen, setFormOpen] = useState(false)
  const [editTarget, setEditTarget] = useState<OrgTag | null>(null)
  const [parentTagId, setParentTagId] = useState<string | undefined>()
  const [form] = Form.useForm<OrgTagFormValues>()

  const { data: tree } = useQuery({
    queryKey: ['admin-org-tree'],
    queryFn: () => adminOrgApi.tree(),
  })

  const createMutation = useMutation({
    mutationFn: (v: OrgTagFormValues) =>
      adminOrgApi.create({
        tagId: v.tagId.trim(),
        name: v.name,
        description: v.description,
        parentTag: parentTagId,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin-org-tree'] })
      setFormOpen(false)
      form.resetFields()
      message.success(t('admin.orgTag.createSuccess'))
    },
  })

  const updateMutation = useMutation({
    mutationFn: (v: OrgTagFormValues) =>
      adminOrgApi.update(editTarget!.tagId, {
        name: v.name,
        description: v.description,
        parentTag: editTarget!.parentTag ?? null,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin-org-tree'] })
      setFormOpen(false)
      setEditTarget(null)
      message.success(t('admin.orgTag.updateSuccess'))
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (tagId: string) => adminOrgApi.delete(tagId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin-org-tree'] })
      message.success(t('admin.orgTag.deleteSuccess'))
    },
  })

  const openCreate = (pid?: string) => {
    setEditTarget(null)
    setParentTagId(pid)
    form.resetFields()
    setFormOpen(true)
  }

  const openEdit = (org: OrgTag) => {
    setEditTarget(org)
    form.setFieldsValue({ tagId: org.tagId, name: org.name, description: org.description })
    setFormOpen(true)
  }

  const handleDelete = (key: React.Key) => {
    const tagId = String(key)
    deleteMutation.mutate(tagId)
  }

  const treeData = toTreeData(tree ?? [], t)

  return (
    <div className={styles.root}>
      <div className={styles.topBar}>
        <Space>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            style={{ background: 'var(--kf-accent-gradient-r)', border: 'none' }}
            onClick={() => openCreate()}
          >
            {t('admin.orgTag.createRoot')}
          </Button>
        </Space>
      </div>

      <div className={styles.orgTree}>
        {!treeData.length ? (
          <Empty description={t('admin.orgTag.emptyDesc')} />
        ) : (
          <Tree
            treeData={treeData}
            defaultExpandAll
            titleRender={(node) => (
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '2px 0' }}>
                <span style={{ flex: 1 }}>{node.title as React.ReactNode}</span>
                <Tooltip title={t('admin.orgTag.tooltipAddChild')}>
                  <Button
                    size="small"
                    type="text"
                    icon={<PlusOutlined />}
                    onClick={(e) => {
                      e.stopPropagation()
                      openCreate(String(node.key))
                    }}
                  />
                </Tooltip>
                <Tooltip title={t('admin.orgTag.tooltipEdit')}>
                  <Button
                    size="small"
                    type="text"
                    icon={<EditOutlined />}
                    onClick={(e) => {
                      e.stopPropagation()
                      const org = findOrg(tree ?? [], String(node.key))
                      if (org) openEdit(org)
                    }}
                  />
                </Tooltip>
                <Tooltip title={t('admin.orgTag.tooltipDelete')}>
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
        title={
          editTarget
            ? t('admin.orgTag.editTitle', { name: editTarget.name })
            : t('admin.orgTag.createTitle')
        }
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
          <Form.Item
            name="tagId"
            label={t('admin.orgTag.fieldCode')}
            rules={[
              {
                required: true,
                pattern: /^[a-z0-9_-]+$/,
                message: t('admin.orgTag.codePattern'),
              },
            ]}
          >
            <Input
              placeholder={t('admin.orgTag.codePlaceholder')}
              disabled={!!editTarget}
              style={{ fontFamily: 'var(--kf-font-mono)' }}
            />
          </Form.Item>
          <Form.Item name="name" label={t('admin.orgTag.fieldName')} rules={[{ required: true }]}>
            <Input placeholder={t('admin.orgTag.namePlaceholder')} />
          </Form.Item>
          <Form.Item name="description" label={t('admin.orgTag.fieldDescription')}>
            <Input placeholder={t('admin.orgTag.descriptionPlaceholder')} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
