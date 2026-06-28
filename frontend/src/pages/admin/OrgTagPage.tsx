import { useState } from 'react'
import { Tree, Button, Modal, Form, Input, App, Space, Tooltip, Empty } from 'antd'
import { PlusOutlined, EditOutlined, DeleteOutlined, ApartmentOutlined } from '@ant-design/icons'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import type { DataNode } from 'antd/es/tree'
import { adminOrgApi, type OrgTag } from '@/api/admin'
import styles from './AdminPage.module.css'

interface OrgTagFormValues {
  name: string
  code: string
  description?: string
}

function toTreeData(
  nodes: OrgTag[],
  t: (key: string, opts?: Record<string, unknown>) => string,
): DataNode[] {
  return nodes.map((n) => ({
    key: n.id,
    title: (
      <span>
        {n.name}
        <span style={{ fontSize: 12, color: 'var(--kf-muted-foreground)', marginLeft: 6 }}>
          ({n.code}) · {t('common.userCount', { count: n.userCount })}
        </span>
      </span>
    ),
    children: n.children ? toTreeData(n.children, t) : undefined,
  }))
}

export default function OrgTagPage() {
  const qc = useQueryClient()
  const { message } = App.useApp()
  const { t } = useTranslation()
  const [formOpen, setFormOpen] = useState(false)
  const [editTarget, setEditTarget] = useState<OrgTag | null>(null)
  const [parentId, setParentId] = useState<number | undefined>()
  const [form] = Form.useForm<OrgTagFormValues>()

  const { data: tree } = useQuery({
    queryKey: ['admin-org-tree'],
    queryFn: () => adminOrgApi.tree(),
  })

  const createMutation = useMutation({
    mutationFn: (v: OrgTagFormValues) =>
      adminOrgApi.create({ name: v.name, code: v.code, description: v.description, parentId }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin-org-tree'] })
      setFormOpen(false)
      form.resetFields()
      message.success(t('admin.orgTag.createSuccess'))
    },
  })

  const updateMutation = useMutation({
    mutationFn: (v: OrgTagFormValues) =>
      adminOrgApi.update(editTarget!.id, {
        name: v.name,
        code: v.code,
        description: v.description,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin-org-tree'] })
      setFormOpen(false)
      setEditTarget(null)
      message.success(t('admin.orgTag.updateSuccess'))
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => adminOrgApi.delete(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin-org-tree'] })
      message.success(t('admin.orgTag.deleteSuccess'))
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

  const treeData = toTreeData(tree ?? [], t)

  return (
    <div className={styles.root}>
      <div className={styles.topBar}>
        <h2 className={styles.pageTitle}>
          <ApartmentOutlined /> {t('admin.orgTag.title')}
        </h2>
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
                      openCreate(Number(node.key))
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
                      const org = findOrg(tree ?? [], Number(node.key))
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
          <Form.Item name="name" label={t('admin.orgTag.fieldName')} rules={[{ required: true }]}>
            <Input placeholder={t('admin.orgTag.namePlaceholder')} />
          </Form.Item>
          <Form.Item
            name="code"
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
              style={{ fontFamily: 'var(--kf-font-mono)' }}
            />
          </Form.Item>
          <Form.Item name="description" label={t('admin.orgTag.fieldDescription')}>
            <Input placeholder={t('admin.orgTag.descriptionPlaceholder')} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
