import { Button, Result } from 'antd'
import { useNavigate } from 'react-router-dom'

export default function Page403() {
  const nav = useNavigate()
  return (
    <Result
      status="403"
      title="403"
      subTitle="您没有权限访问此页面"
      extra={
        <Button type="primary" onClick={() => nav('/dashboard')}>
          返回首页
        </Button>
      }
    />
  )
}
