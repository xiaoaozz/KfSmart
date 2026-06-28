import type { NodeProps } from '@xyflow/react'
import { useTranslation } from 'react-i18next'
import BaseNode from './BaseNode'

export default function StartNode({ id, selected }: NodeProps) {
  const { t } = useTranslation()
  return (
    <BaseNode id={id} type="start" selected={selected} hasTarget={false}>
      <span>{t('workflow.nodeContent.start')}</span>
    </BaseNode>
  )
}
