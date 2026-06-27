import { Button, Result } from 'antd'
import { useNavigate } from 'react-router-dom'

export default function Page500() {
  const nav = useNavigate()
  return (
    <Result
      status="500"
      title="500"
      subTitle="服务器内部错误，请稍后再试"
      extra={
        <Button type="primary" onClick={() => nav('/dashboard')}>
          返回首页
        </Button>
      }
    />
  )
}
