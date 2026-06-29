import { useState } from 'react'
import { Button, Modal, Form, Input, Tree, Tag, App, Space, Tooltip, Popconfirm } from 'antd'
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import type { DataNode } from 'antd/es/tree'
import { adminRoleApi, type Role, type Permission } from '@/api/admin'
import { PageBar } from '@/components/business'
import styles from './AdminPage.module.css'

export function buildPermTree(permissions: Permission[]): DataNode[] {
  const groups = [...new Set(permissions.map((p) => p.resourceType))]
  return groups.map((group) => ({
    key: `group_${group}`,
    title: group,
    children: permissions
      .filter((p) => p.resourceType === group)
      .map((p) => ({ key: p.permCode, title: `${p.permCode} — ${p.permName}` })),
  }))
}

function autoRoleCode(name: string): string {
  return (
    'ROLE_' +
    name
      .trim()
      .toUpperCase()
      .replace(/[^A-Z0-9]+/g, '_')
  )
}

export default function RoleManagePage() {
  const qc = useQueryClient()
  const { message, modal } = App.useApp()
  const { t } = useTranslation()
  const [current, setCurrent] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [formOpen, setFormOpen] = useState(false)
  const [editRole, setEditRole] = useState<Role | null>(null)
  const [checkedKeys, setCheckedKeys] = useState<string[]>([])
  const [form] = Form.useForm<{ roleCode: string; roleName: string; description?: string }>()

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
    mutationFn: async (v: { roleCode: string; roleName: string; description?: string }) => {
      await adminRoleApi.create({
        roleCode: v.roleCode,
        roleName: v.roleName,
        description: v.description,
      })
      const permCodes = checkedKeys.filter((k) => !k.startsWith('group_'))
      if (permCodes.length > 0) {
        const allRoles = await adminRoleApi.list()
        const newRole = allRoles.find((r) => r.roleCode === v.roleCode)
        if (newRole) {
          await adminRoleApi.update(newRole.id, { permCodes })
        }
      }
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin-roles'] })
      setFormOpen(false)
      form.resetFields()
      setCheckedKeys([])
      message.success(t('admin.role.createSuccess'))
    },
  })

  const updateMutation = useMutation({
    mutationFn: (v: { roleName: string; description?: string }) =>
      adminRoleApi.update(editRole!.id, {
        roleName: v.roleName,
        description: v.description,
        permCodes: checkedKeys.filter((k) => !k.startsWith('group_')),
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin-roles'] })
      setEditRole(null)
      setFormOpen(false)
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
    form.setFieldsValue({ roleCode: r.roleCode, roleName: r.roleName, description: r.description })
    setCheckedKeys(r.permissions?.map((p) => p.permCode) ?? [])
    setFormOpen(true)
  }

  const handleConfirmDelete = (r: Role) => {
    modal.confirm({
      title: t('admin.role.deleteConfirm', { name: r.roleName }),
      okType: 'danger',
      onOk: () => deleteMutation.mutateAsync(r.id),
    })
  }

  return (
    <div className={styles.root}>
      <div className={styles.topBar}>
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
        {(roles ?? []).slice((current - 1) * pageSize, current * pageSize).map((r: Role) => (
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
                {r.roleName}
                {r.isSystem && (
                  <Tag color="orange" style={{ marginLeft: 8, fontSize: 11 }}>
                    {t('admin.role.system')}
                  </Tag>
                )}
              </div>
              <div
                style={{
                  fontSize: 11,
                  color: 'var(--kf-muted-foreground)',
                  marginTop: 1,
                  fontFamily: 'var(--kf-font-mono)',
                }}
              >
                {r.roleCode}
              </div>
              {r.description && (
                <div style={{ fontSize: 12, color: 'var(--kf-muted-foreground)', marginTop: 2 }}>
                  {r.description}
                </div>
              )}
              <div style={{ marginTop: 6, display: 'flex', gap: 4, flexWrap: 'wrap' }}>
                {(r.permissions ?? []).slice(0, 8).map((p, i) => (
                  <Tag key={i} style={{ fontFamily: 'var(--kf-font-mono)', fontSize: 11 }}>
                    {p.permCode}
                  </Tag>
                ))}
                {(r.permissions ?? []).length > 8 && <Tag>+{r.permissions.length - 8}</Tag>}
              </div>
            </div>
            <Space>
              <Tooltip title={t('admin.role.tooltipEdit')}>
                <Button size="small" icon={<EditOutlined />} onClick={() => openEdit(r)} />
              </Tooltip>
              <Tooltip title={t('admin.role.tooltipDelete')}>
                <Popconfirm
                  title={t('admin.role.deleteConfirm', { name: r.roleName })}
                  okType="danger"
                  onConfirm={() => handleConfirmDelete(r)}
                  disabled={r.isSystem}
                >
                  <Button size="small" danger icon={<DeleteOutlined />} disabled={r.isSystem} />
                </Popconfirm>
              </Tooltip>
            </Space>
          </div>
        ))}
      </div>
      {(roles ?? []).length > 0 && (
        <div
          style={{
            display: 'flex',
            justifyContent: 'flex-end',
            alignItems: 'center',
            flexShrink: 0,
            padding: '12px 20px',
            borderTop: '1px solid var(--kf-border)',
          }}
        >
          <PageBar
            current={current}
            pageSize={pageSize}
            total={(roles ?? []).length}
            onChange={(page, size) => {
              setCurrent(page)
              setPageSize(size)
            }}
          />
        </div>
      )}

      <Modal
        title={
          editRole
            ? t('admin.role.editTitle', { name: editRole.roleName })
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
          <Form.Item
            name="roleName"
            label={t('admin.role.fieldName')}
            rules={[{ required: true, message: t('common.required') }]}
          >
            <Input
              onChange={(e) => {
                if (!editRole) {
                  form.setFieldValue('roleCode', autoRoleCode(e.target.value))
                }
              }}
            />
          </Form.Item>
          {!editRole && (
            <Form.Item
              name="roleCode"
              label={t('admin.role.fieldCode')}
              rules={[
                { required: true, message: t('common.required') },
                { pattern: /^ROLE_[A-Z0-9_]+$/, message: t('admin.role.codePattern') },
              ]}
            >
              <Input placeholder="ROLE_CUSTOM" style={{ fontFamily: 'var(--kf-font-mono)' }} />
            </Form.Item>
          )}
          <Form.Item name="description" label={t('admin.role.fieldDescription')}>
            <Input />
          </Form.Item>
          <Form.Item label={t('admin.role.fieldPermissions')}>
            <Tree
              checkable
              defaultExpandAll
              treeData={permTree}
              checkedKeys={checkedKeys}
              onCheck={(keys) => {
                const arr = Array.isArray(keys) ? keys : keys.checked
                setCheckedKeys((arr as string[]).filter((k) => !k.startsWith('group_')))
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
