import { CheckOutlined, BgColorsOutlined } from '@ant-design/icons'
import { Popover } from 'antd'
import { useState } from 'react'

import { useThemeStore, type ThemeId } from '@/stores/theme'

import styles from './ThemeSwitch.module.css'

interface ThemeOption {
  id: ThemeId
  name: string
  swatches: [string, string, string]
}

const THEMES: ThemeOption[] = [
  {
    id: 'light',
    name: '企业简约-白',
    swatches: ['#FAFAFA', '#0052FF', '#E2E8F0'],
  },
  {
    id: 'dark',
    name: '企业简约-黑',
    swatches: ['#0A0A0F', '#4D7CFF', '#141420'],
  },
  {
    id: 'earth',
    name: '有机大地风',
    swatches: ['#F7F3EB', '#4F6E35', '#8C5E2A'],
  },
  {
    id: 'corporate',
    name: '企业信任',
    swatches: ['#F8FAFC', '#4F46E5', '#7C3AED'],
  },
  {
    id: 'neo',
    name: '拟态浮雕',
    swatches: ['#E0E5EC', '#6C63FF', '#38B2AC'],
  },
  {
    id: 'mono',
    name: '极简黑白',
    swatches: ['#FFFFFF', '#000000', '#737373'],
  },
  {
    id: 'terminal',
    name: '终端黑绿',
    swatches: ['#0A0A0A', '#33FF00', '#1F521F'],
  },
  {
    id: 'organic',
    name: '自然有机',
    swatches: ['#FDFCF8', '#5D7052', '#C18C5D'],
  },
  {
    id: 'vaporwave',
    name: '蒸汽波霓虹',
    swatches: ['#090014', '#FF00FF', '#00FFFF'],
  },
  {
    id: 'professional',
    name: '典雅衬线',
    swatches: ['#FAFAF8', '#B8860B', '#1A1A1A'],
  },
]

function ThemePicker({ themeId, onSelect }: { themeId: ThemeId; onSelect: (id: ThemeId) => void }) {
  return (
    <div className={styles.pickerWrapper}>
      <div className={styles.pickerTitle}>外观主题</div>
      <div className={styles.picker}>
        {THEMES.map((t) => (
          <button
            key={t.id}
            type="button"
            className={`${styles.themeCard} ${themeId === t.id ? styles.themeCardActive : ''}`}
            onClick={() => onSelect(t.id)}
            aria-pressed={themeId === t.id}
          >
            <div className={styles.swatches}>
              {t.swatches.map((color, i) => (
                <span key={i} className={styles.swatch} style={{ backgroundColor: color }} />
              ))}
            </div>
            <span className={styles.themeName}>{t.name}</span>
            {themeId === t.id && <CheckOutlined className={styles.checkIcon} />}
          </button>
        ))}
      </div>
    </div>
  )
}

export default function ThemeSwitch() {
  const { themeId, setTheme } = useThemeStore()
  const [open, setOpen] = useState(false)

  return (
    <Popover
      content={
        <ThemePicker
          themeId={themeId}
          onSelect={(id) => {
            setTheme(id)
            setOpen(false)
          }}
        />
      }
      trigger="click"
      open={open}
      onOpenChange={setOpen}
      placement="bottomRight"
      arrow={false}
    >
      <button
        className={`${styles.btn} ${open ? styles.btnActive : ''}`}
        aria-label="切换主题"
        aria-haspopup="true"
        aria-expanded={open}
        type="button"
      >
        <span className={styles.icon}>
          <BgColorsOutlined />
        </span>
      </button>
    </Popover>
  )
}
