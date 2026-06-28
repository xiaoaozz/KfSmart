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
import { useTranslation } from 'react-i18next'
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
  const { t } = useTranslation()
  return (
    <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }}>
      <GradientCard className={styles.card} onClick={() => navigate(`/knowledge-bases/${kb.kbId}`)}>
        <div className={styles.cardHeader}>
          <DatabaseOutlined className={styles.cardIcon} />
          <div className={styles.cardActions} onClick={(e) => e.stopPropagation()}>
            <PermissionButton permission="kb:write" mode="hide">
              <Tooltip title={t('common.edit')}>
                <EditOutlined className={styles.actionBtn} onClick={() => onEdit(kb)} />
              </Tooltip>
            </PermissionButton>
            <PermissionButton permission="kb:delete" mode="hide">
              <Popconfirm
                title={t('kb.deleteConfirm')}
                description={t('kb.deleteWarning')}
                onConfirm={() => onDelete(kb.kbId)}
                okText={t('common.delete')}
                okButtonProps={{ danger: true }}
                cancelText={t('common.cancel')}
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
            <FileTextOutlined /> {t('common.docCount', { count: kb.fileCount })}
          </span>
          {kb.orgTag && (
            <Tag color="blue" style={{ fontSize: 12 }}>
              {kb.orgTag}
            </Tag>
          )}
          <Tag color={kb.isPublic ? 'green' : 'default'} style={{ fontSize: 12 }}>
            {kb.isPublic ? t('common.public') : t('common.private')}
          </Tag>
        </div>

        <div className={styles.cardFooter}>
          <span className={styles.createdBy}>{t('common.createdBy', { name: kb.createdBy })}</span>
          <span className={styles.createdAt}>
            {kb.updatedAt ? new Date(kb.updatedAt).toLocaleDateString() : '—'}
          </span>
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
  const { t } = useTranslation()

  const { data, isLoading } = useQuery({
    queryKey: ['knowledge-bases', { keyword, isPublic }],
    queryFn: () => kbApi.list({ keyword: keyword || undefined, isPublic }),
  })

  const createMutation = useMutation({
    mutationFn: kbApi.create,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['knowledge-bases'] })
      message.success(t('kb.createSuccess'))
      setModalOpen(false)
      form.resetFields()
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: Partial<KbFormValues> }) =>
      kbApi.update(id, data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['knowledge-bases'] })
      message.success(t('kb.updateSuccess'))
      setModalOpen(false)
      setEditing(null)
    },
  })

  const deleteMutation = useMutation({
    mutationFn: kbApi.delete,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['knowledge-bases'] })
      message.success(t('kb.deleteSuccess'))
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
      orgTag: kb.orgTag,
      isPublic: kb.isPublic,
    })
    setModalOpen(true)
  }

  const handleSubmit = async (values: KbFormValues) => {
    if (editing) {
      await updateMutation.mutateAsync({ id: editing.kbId, data: values })
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
            placeholder={t('kb.searchPlaceholder')}
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            allowClear
            style={{ width: 240 }}
          />
          <Select
            placeholder={t('kb.accessFilter')}
            allowClear
            value={isPublic}
            onChange={(v) => setIsPublic(v)}
            style={{ width: 120 }}
            options={[
              { label: t('common.public'), value: true },
              { label: t('common.private'), value: false },
            ]}
          />
        </div>
        <PermissionButton permission="kb:write" mode="hide">
          <GradientButton icon={<PlusOutlined />} onClick={handleOpenCreate}>
            {t('kb.createNew')}
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
          title={t('kb.emptyTitle')}
          description={t('kb.emptyDesc')}
          action={
            <PermissionButton permission="kb:write" mode="hide">
              <GradientButton icon={<PlusOutlined />} onClick={handleOpenCreate}>
                {t('kb.createNew')}
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
        title={editing ? t('kb.editTitle') : t('kb.createTitle')}
        open={modalOpen}
        onCancel={() => {
          setModalOpen(false)
          setEditing(null)
        }}
        onOk={() => form.submit()}
        confirmLoading={createMutation.isPending || updateMutation.isPending}
        okText={editing ? t('kb.saveOk') : t('kb.createOk')}
        destroyOnClose
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={handleSubmit}
          initialValues={{ isPublic: false }}
        >
          <Form.Item
            name="name"
            label={t('kb.form.name')}
            rules={[{ required: true, message: t('kb.form.nameRequired') }]}
          >
            <Input placeholder={t('kb.form.namePlaceholder')} />
          </Form.Item>
          <Form.Item name="description" label={t('kb.form.description')}>
            <Input.TextArea rows={3} placeholder={t('kb.form.descriptionPlaceholder')} />
          </Form.Item>
          <Form.Item name="orgTag" label={t('kb.form.orgTag')}>
            <Input placeholder={t('kb.form.orgTagPlaceholder')} />
          </Form.Item>
          <Form.Item name="isPublic" label={t('kb.form.isPublic')} valuePropName="checked">
            <Switch checkedChildren={t('common.public')} unCheckedChildren={t('common.private')} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
