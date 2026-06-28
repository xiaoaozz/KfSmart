import type { NodeProps } from '@xyflow/react'
import { useTranslation } from 'react-i18next'
import BaseNode from './BaseNode'
import type { KbNodeData } from '@/types/workflow'

export default function KbNode({ id, selected, data }: NodeProps) {
  const { t } = useTranslation()
  const d = data as unknown as KbNodeData
  return (
    <BaseNode id={id} type="kb" selected={selected}>
      <div>KB #{d?.knowledgeBaseId ?? '—'}</div>
      <div>
        Top-{d?.topK ?? 5} · {t('workflow.nodeContent.threshold')} {d?.threshold ?? 0.7}
      </div>
    </BaseNode>
  )
}
