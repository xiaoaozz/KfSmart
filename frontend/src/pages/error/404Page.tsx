import { Button, Result } from 'antd'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'

export default function Page404() {
  const nav = useNavigate()
  const { t } = useTranslation()
  return (
    <Result
      status="404"
      title="404"
      subTitle={t('error.404.subtitle')}
      extra={
        <Button type="primary" onClick={() => nav('/dashboard')}>
          {t('common.backHome')}
        </Button>
      }
    />
  )
}
