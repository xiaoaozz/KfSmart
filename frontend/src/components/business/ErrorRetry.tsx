import { Button, Result } from 'antd'

interface ErrorRetryProps {
  title?: string
  description?: string
  onRetry?: () => void
}

export default function ErrorRetry({
  title = '加载失败',
  description = '请检查网络连接后重试',
  onRetry,
}: ErrorRetryProps) {
  return (
    <Result
      status="error"
      title={title}
      subTitle={description}
      extra={
        onRetry && (
          <Button type="primary" onClick={onRetry}>
            重新加载
          </Button>
        )
      }
    />
  )
}
