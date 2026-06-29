import { useQuery } from '@tanstack/react-query'
import { Skeleton, Empty, Alert } from 'antd'
import { useTranslation } from 'react-i18next'
import ReactMarkdown, { type Components } from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter'
import { oneLight, oneDark } from 'react-syntax-highlighter/dist/esm/styles/prism'
import { docApi } from '@/api/document'
import { useTheme } from '@/hooks/useTheme'
import type { Document } from '@/types/document'
import styles from './DocContentPreview.module.css'

interface DocContentPreviewProps {
  doc: Document
}

export default function DocContentPreview({ doc }: DocContentPreviewProps) {
  const { t } = useTranslation()
  const { isDark } = useTheme()

  const { data, isLoading, isError } = useQuery({
    queryKey: ['docPreview', doc.fileMd5],
    queryFn: () => docApi.preview(doc.fileName, doc.fileMd5),
    enabled: !!doc.fileMd5,
  })

  if (isLoading) {
    return <Skeleton active paragraph={{ rows: 8 }} />
  }

  if (isError) {
    return <Alert type="error" showIcon message={t('doc.contentLoadError')} />
  }

  const content = data?.content?.trim()

  if (!content) {
    return <Empty description={t('doc.contentEmpty')} />
  }

  // Fenced code → syntax highlighter (theme-aware); inline code passthrough.
  // `pre` is flattened to a fragment so the highlighter's own PreTag controls the block layout.
  const components: Components = {
    pre: ({ children }) => <>{children}</>,
    code({ className, children }) {
      const match = /language-(\w+)/.exec(className || '')
      if (match) {
        return (
          <SyntaxHighlighter
            language={match[1]}
            style={isDark ? oneDark : oneLight}
            PreTag="div"
            customStyle={{ margin: 0, fontSize: 13 }}
            codeTagProps={{ style: { fontFamily: 'var(--kf-font-mono)' } }}
          >
            {String(children).replace(/\n$/, '')}
          </SyntaxHighlighter>
        )
      }
      return <code>{children}</code>
    },
  }

  return (
    <div className={styles.prose}>
      <ReactMarkdown remarkPlugins={[remarkGfm]} components={components}>
        {content}
      </ReactMarkdown>
    </div>
  )
}
