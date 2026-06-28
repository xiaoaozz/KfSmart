import { Button, Dropdown } from 'antd'
import { GlobalOutlined } from '@ant-design/icons'
import { useLocaleStore, type Locale } from '@/stores/locale'

const LOCALE_OPTIONS: { key: Locale; label: string }[] = [
  { key: 'zh-CN', label: '中文' },
  { key: 'en-US', label: 'English' },
  { key: 'ja-JP', label: '日本語' },
]

export default function LanguageSwitch() {
  const locale = useLocaleStore((s) => s.locale)
  const setLocale = useLocaleStore((s) => s.setLocale)

  return (
    <Dropdown
      menu={{
        selectedKeys: [locale],
        items: LOCALE_OPTIONS.map((o) => ({ key: o.key, label: o.label })),
        onClick: ({ key }) => setLocale(key as Locale),
      }}
      placement="bottomRight"
    >
      <Button
        type="text"
        icon={<GlobalOutlined />}
        style={{ color: 'var(--kf-muted-foreground)' }}
      />
    </Dropdown>
  )
}
