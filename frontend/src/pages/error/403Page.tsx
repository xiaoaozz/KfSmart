import { Button, Result } from 'antd'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'

export default function Page403() {
  const nav = useNavigate()
  const { t } = useTranslation()
  return (
    <Result
      status="403"
      title="403"
      subTitle={t('error.403.subtitle')}
      extra={
        <Button type="primary" onClick={() => nav('/dashboard')}>
          {t('common.backHome')}
        </Button>
      }
    />
  )
}
