import { useState } from 'react'
import { Button, Modal, Form, Input, Tree, Tag, App, Space, Tooltip, Popconfirm } from 'antd'
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import type { DataNode } from 'antd/es/tree'
import { adminRoleApi, type Role, type Permission } from '@/api/admin'
import styles from './AdminPage.module.css'

function buildPermTree(permissions: Permission[]): DataNode[] {
  const groups = [...new Set(permissions.map((p) => p.group))]
  return groups.map((group) => ({
    key: `group_${group}`,
    title: group,
    children: permissions
      .filter((p) => p.group === group)
      .map((p) => ({ key: p.key, title: p.label })),
  }))
}

export default function RoleManagePage() {
  const qc = useQueryClient()
  const { message, modal } = App.useApp()
  const { t } = useTranslation()
  const [formOpen, setFormOpen] = useState(false)
  const [editRole, setEditRole] = useState<Role | null>(null)
  const [checkedKeys, setCheckedKeys] = useState<string[]>([])
  const [form] = Form.useForm<{ name: string; description?: string }>()

  const { data: roles } = useQuery({
    queryKey: ['admin-roles'],
    queryFn: () => adminRoleApi.list(),
  })

  const { data: permissions } = useQuery({
    queryKey: ['admin-permissions'],
    queryFn: () => adminRoleApi.permissions(),
  })

  const permTree = buildPermTree(permissions ?? [])

  const createMutation = useMutation({
    mutationFn: (v: { name: string; description?: string }) =>
      adminRoleApi.create({
        ...v,
        permissions: checkedKeys.filter((k) => !k.startsWith('group_')),
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin-roles'] })
      setFormOpen(false)
      form.resetFields()
      setCheckedKeys([])
      message.success(t('admin.role.createSuccess'))
    },
  })

  const updateMutation = useMutation({
    mutationFn: (v: { name: string; description?: string }) =>
      adminRoleApi.update(editRole!.id, {
        ...v,
        permissions: checkedKeys.filter((k) => !k.startsWith('group_')),
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin-roles'] })
      setEditRole(null)
      form.resetFields()
      message.success(t('admin.role.updateSuccess'))
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => adminRoleApi.delete(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin-roles'] })
      message.success(t('admin.role.deleteSuccess'))
    },
  })

  const openCreate = () => {
    setEditRole(null)
    form.resetFields()
    setCheckedKeys([])
    setFormOpen(true)
  }

  const openEdit = (r: Role) => {
    setEditRole(r)
    form.setFieldsValue({ name: r.name, description: r.description })
    setCheckedKeys(r.permissions)
    setFormOpen(true)
  }

  const handleConfirmDelete = (r: Role) => {
    modal.confirm({
      title: t('admin.role.deleteConfirm', { name: r.name }),
      okType: 'danger',
      onOk: () => deleteMutation.mutateAsync(r.id),
    })
  }

  return (
    <div className={styles.root}>
      <div className={styles.topBar}>
        <h2 className={styles.pageTitle}>
          <SafetyCertificateOutlined /> {t('admin.role.title')}
        </h2>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          style={{ background: 'var(--kf-accent-gradient-r)', border: 'none' }}
          onClick={openCreate}
        >
          {t('admin.role.createBtn')}
        </Button>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
        {(roles ?? []).map((r: Role) => (
          <div
            key={r.id}
            style={{
              background: 'var(--kf-card)',
              border: '1px solid var(--kf-border)',
              borderRadius: 'var(--kf-radius-md)',
              padding: '14px 18px',
              display: 'flex',
              alignItems: 'center',
              gap: 14,
            }}
          >
            <div style={{ flex: 1 }}>
              <div style={{ fontWeight: 600, fontSize: 15, color: 'var(--kf-foreground)' }}>
                {r.name}
              </div>
              {r.description && (
                <div style={{ fontSize: 12, color: 'var(--kf-muted-foreground)', marginTop: 2 }}>
                  {r.description}
                </div>
              )}
              <div style={{ marginTop: 6, display: 'flex', gap: 4, flexWrap: 'wrap' }}>
                {r.permissions.slice(0, 8).map((p) => (
                  <Tag key={p} style={{ fontFamily: 'var(--kf-font-mono)', fontSize: 11 }}>
                    {p}
                  </Tag>
                ))}
                {r.permissions.length > 8 && <Tag>+{r.permissions.length - 8}</Tag>}
              </div>
            </div>
            <Tag color="blue">{t('common.userCount', { count: r.userCount })}</Tag>
            <Space>
              <Tooltip title={t('admin.role.tooltipEdit')}>
                <Button size="small" icon={<EditOutlined />} onClick={() => openEdit(r)} />
              </Tooltip>
              <Tooltip title={t('admin.role.tooltipDelete')}>
                <Popconfirm
                  title={t('admin.role.deleteConfirm', { name: r.name })}
                  okType="danger"
                  onConfirm={() => handleConfirmDelete(r)}
                >
                  <Button size="small" danger icon={<DeleteOutlined />} />
                </Popconfirm>
              </Tooltip>
            </Space>
          </div>
        ))}
      </div>

      <Modal
        title={
          editRole
            ? t('admin.role.editTitle', { name: editRole.name })
            : t('admin.role.createTitle')
        }
        open={formOpen}
        onCancel={() => {
          setFormOpen(false)
          form.resetFields()
        }}
        onOk={() => form.submit()}
        confirmLoading={createMutation.isPending || updateMutation.isPending}
        width={560}
        destroyOnClose
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={(v) => (editRole ? updateMutation.mutate(v) : createMutation.mutate(v))}
        >
          <Form.Item name="name" label={t('admin.role.fieldName')} rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="description" label={t('admin.role.fieldDescription')}>
            <Input />
          </Form.Item>
          <Form.Item label={t('admin.role.fieldPermissions')}>
            <Tree
              checkable
              treeData={permTree}
              checkedKeys={checkedKeys}
              onCheck={(keys) => {
                const arr = Array.isArray(keys) ? keys : keys.checked
                setCheckedKeys(arr as string[])
              }}
              style={{
                maxHeight: 300,
                overflow: 'auto',
                background: 'var(--kf-muted)',
                padding: 12,
                borderRadius: 8,
              }}
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
