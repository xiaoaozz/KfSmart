import type { NodeProps } from '@xyflow/react'
import { useTranslation } from 'react-i18next'
import BaseNode from './BaseNode'

export default function EndNode({ id, selected }: NodeProps) {
  const { t } = useTranslation()
  return (
    <BaseNode id={id} type="end" selected={selected} hasSource={false}>
      <span>{t('workflow.nodeContent.end')}</span>
    </BaseNode>
  )
}
