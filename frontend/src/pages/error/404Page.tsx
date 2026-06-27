import { Button, Result } from 'antd'
import { useNavigate } from 'react-router-dom'

export default function Page404() {
  const nav = useNavigate()
  return (
    <Result
      status="404"
      title="404"
      subTitle="您访问的页面不存在"
      extra={
        <Button type="primary" onClick={() => nav('/dashboard')}>
          返回首页
        </Button>
      }
    />
  )
}
