import { Button, Result } from 'antd'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'

export default function Page500() {
  const nav = useNavigate()
  const { t } = useTranslation()
  return (
    <Result
      status="500"
      title="500"
      subTitle={t('error.500.subtitle')}
      extra={
        <Button type="primary" onClick={() => nav('/dashboard')}>
          {t('common.backHome')}
        </Button>
      }
    />
  )
}
