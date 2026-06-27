import { useState } from 'react'
import {
  Input,
  Select,
  Switch,
  Modal,
  Form,
  Tag,
  Tooltip,
  Popconfirm,
  App,
  Row,
  Col,
  Skeleton,
} from 'antd'
import {
  PlusOutlined,
  SearchOutlined,
  EditOutlined,
  DeleteOutlined,
  DatabaseOutlined,
  FileTextOutlined,
} from '@ant-design/icons'
import { motion } from 'framer-motion'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { kbApi } from '@/api/knowledge-base'
import type { KnowledgeBase, KbFormValues } from '@/types/knowledge-base'
import { GradientButton, GradientCard } from '@/components/base'
import { EmptyState, PermissionButton } from '@/components/business'
import styles from './KbListPage.module.css'

function KbCard({
  kb,
  onEdit,
  onDelete,
}: {
  kb: KnowledgeBase
  onEdit: (kb: KnowledgeBase) => void
  onDelete: (id: string) => void
}) {
  const navigate = useNavigate()
  return (
    <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }}>
      <GradientCard className={styles.card} onClick={() => navigate(`/knowledge-bases/${kb.id}`)}>
        <div className={styles.cardHeader}>
          <DatabaseOutlined className={styles.cardIcon} />
          <div className={styles.cardActions} onClick={(e) => e.stopPropagation()}>
            <PermissionButton permission="kb:write" mode="hide">
              <Tooltip title="编辑">
                <EditOutlined className={styles.actionBtn} onClick={() => onEdit(kb)} />
              </Tooltip>
            </PermissionButton>
            <PermissionButton permission="kb:delete" mode="hide">
              <Popconfirm
                title="确认删除此知识库？"
                description="删除后数据不可恢复"
                onConfirm={() => onDelete(kb.id)}
                okText="删除"
                okButtonProps={{ danger: true }}
                cancelText="取消"
              >
                <DeleteOutlined className={`${styles.actionBtn} ${styles.danger}`} />
              </Popconfirm>
            </PermissionButton>
          </div>
        </div>

        <h3 className={styles.cardName}>{kb.name}</h3>
        {kb.description && <p className={styles.cardDesc}>{kb.description}</p>}

        <div className={styles.cardMeta}>
          <span className={styles.metaItem}>
            <FileTextOutlined /> {kb.docCount} 文档
          </span>
          {kb.organizationTag && (
            <Tag color="blue" style={{ fontSize: 12 }}>
              {kb.organizationTag}
            </Tag>
          )}
          <Tag color={kb.isPublic ? 'green' : 'default'} style={{ fontSize: 12 }}>
            {kb.isPublic ? '公开' : '私有'}
          </Tag>
        </div>

        <div className={styles.cardFooter}>
          <span className={styles.createdBy}>by {kb.createdBy}</span>
          <span className={styles.createdAt}>{new Date(kb.updateTime).toLocaleDateString()}</span>
        </div>
      </GradientCard>
    </motion.div>
  )
}

export default function KbListPage() {
  const [keyword, setKeyword] = useState('')
  const [isPublic, setIsPublic] = useState<boolean | undefined>(undefined)
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<KnowledgeBase | null>(null)
  const [form] = Form.useForm<KbFormValues>()
  const qc = useQueryClient()
  const { message } = App.useApp()

  const { data, isLoading } = useQuery({
    queryKey: ['knowledge-bases', { keyword, isPublic }],
    queryFn: () => kbApi.list({ keyword: keyword || undefined, isPublic }),
  })

  const createMutation = useMutation({
    mutationFn: kbApi.create,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['knowledge-bases'] })
      message.success('创建成功')
      setModalOpen(false)
      form.resetFields()
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: Partial<KbFormValues> }) =>
      kbApi.update(id, data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['knowledge-bases'] })
      message.success('更新成功')
      setModalOpen(false)
      setEditing(null)
    },
  })

  const deleteMutation = useMutation({
    mutationFn: kbApi.delete,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['knowledge-bases'] })
      message.success('删除成功')
    },
  })

  const handleOpenCreate = () => {
    setEditing(null)
    form.resetFields()
    setModalOpen(true)
  }

  const handleOpenEdit = (kb: KnowledgeBase) => {
    setEditing(kb)
    form.setFieldsValue({
      name: kb.name,
      description: kb.description,
      organizationTag: kb.organizationTag,
      isPublic: kb.isPublic,
    })
    setModalOpen(true)
  }

  const handleSubmit = async (values: KbFormValues) => {
    if (editing) {
      await updateMutation.mutateAsync({ id: editing.id, data: values })
    } else {
      await createMutation.mutateAsync(values)
    }
  }

  const kbs = data?.records ?? []

  return (
    <div className={styles.root}>
      {/* Toolbar */}
      <div className={styles.toolbar}>
        <div className={styles.filters}>
          <Input
            prefix={<SearchOutlined />}
            placeholder="搜索知识库"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            allowClear
            style={{ width: 240 }}
          />
          <Select
            placeholder="访问权限"
            allowClear
            value={isPublic}
            onChange={(v) => setIsPublic(v)}
            style={{ width: 120 }}
            options={[
              { label: '公开', value: true },
              { label: '私有', value: false },
            ]}
          />
        </div>
        <PermissionButton permission="kb:write" mode="hide">
          <GradientButton icon={<PlusOutlined />} onClick={handleOpenCreate}>
            新建知识库
          </GradientButton>
        </PermissionButton>
      </div>

      {/* Grid */}
      {isLoading ? (
        <Row gutter={[16, 16]}>
          {[1, 2, 3, 4, 5, 6].map((i) => (
            <Col key={i} xs={24} sm={12} lg={8}>
              <Skeleton active />
            </Col>
          ))}
        </Row>
      ) : kbs.length === 0 ? (
        <EmptyState
          title="暂无知识库"
          description="创建一个知识库，开始组织您的文档"
          action={
            <PermissionButton permission="kb:write" mode="hide">
              <GradientButton icon={<PlusOutlined />} onClick={handleOpenCreate}>
                新建知识库
              </GradientButton>
            </PermissionButton>
          }
        />
      ) : (
        <Row gutter={[16, 16]}>
          {kbs.map((kb) => (
            <Col key={kb.id} xs={24} sm={12} lg={8}>
              <KbCard
                kb={kb}
                onEdit={handleOpenEdit}
                onDelete={(id) => deleteMutation.mutate(id)}
              />
            </Col>
          ))}
        </Row>
      )}

      {/* Create / Edit Modal */}
      <Modal
        title={editing ? '编辑知识库' : '新建知识库'}
        open={modalOpen}
        onCancel={() => {
          setModalOpen(false)
          setEditing(null)
        }}
        onOk={() => form.submit()}
        confirmLoading={createMutation.isPending || updateMutation.isPending}
        okText={editing ? '保存' : '创建'}
        destroyOnClose
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={handleSubmit}
          initialValues={{ isPublic: false }}
        >
          <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入名称' }]}>
            <Input placeholder="知识库名称" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={3} placeholder="可选描述" />
          </Form.Item>
          <Form.Item name="organizationTag" label="组织标签">
            <Input placeholder="所属组织（可选）" />
          </Form.Item>
          <Form.Item name="isPublic" label="是否公开" valuePropName="checked">
            <Switch checkedChildren="公开" unCheckedChildren="私有" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
