import { useState } from 'react'
import { Pagination, Select, InputNumber } from 'antd'

const PRESET_SIZES = [5, 10, 20, 50, 100]

type SizeValue = number | 'custom'

interface PageBarProps {
  current: number
  pageSize: number
  total: number
  onChange: (page: number, size: number) => void
}

export default function PageBar({ current, pageSize, total, onChange }: PageBarProps) {
  const [customMode, setCustomMode] = useState(!PRESET_SIZES.includes(pageSize))
  const [customInput, setCustomInput] = useState<number | null>(
    !PRESET_SIZES.includes(pageSize) ? pageSize : null,
  )

  const handleSizeChange = (val: SizeValue) => {
    if (val === 'custom') {
      setCustomMode(true)
      setCustomInput(pageSize)
      return
    }
    setCustomMode(false)
    setCustomInput(null)
    onChange(1, val)
  }

  const applyCustom = () => {
    if (customInput != null && customInput >= 1) {
      onChange(1, customInput)
    }
  }

  const sizeOptions: { value: SizeValue; label: string }[] = [
    ...PRESET_SIZES.map((n) => ({ value: n as SizeValue, label: `${n} 条/页` })),
    { value: 'custom', label: '自定义' },
  ]

  return (
    <div style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
      <span style={{ fontSize: 13, color: 'var(--kf-muted-foreground)', whiteSpace: 'nowrap' }}>
        共 {total} 条
      </span>
      <Select<SizeValue>
        size="small"
        value={customMode ? 'custom' : pageSize}
        options={sizeOptions}
        onChange={handleSizeChange}
        style={{ width: 108 }}
      />
      {customMode && (
        <InputNumber
          size="small"
          min={1}
          max={9999}
          value={customInput}
          onChange={setCustomInput}
          onPressEnter={() => applyCustom()}
          onBlur={() => applyCustom()}
          style={{ width: 90 }}
          addonAfter="条/页"
        />
      )}
      <Pagination
        size="small"
        current={current}
        pageSize={pageSize}
        total={total}
        onChange={(page) => onChange(page, pageSize)}
        showSizeChanger={false}
        showQuickJumper
      />
    </div>
  )
}
