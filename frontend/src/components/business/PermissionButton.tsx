import type { ReactNode } from 'react'
import { Tooltip } from 'antd'
import { usePermission } from '@/hooks/usePermission'

interface PermissionButtonProps {
  /** Permission code, e.g. "kb:write" */
  permission: string
  /** 'hide' removes the node; 'disable' renders it disabled (default) */
  mode?: 'hide' | 'disable'
  children: ReactNode
  /** Optional tooltip shown when disabled */
  disabledTip?: string
}

export default function PermissionButton({
  permission,
  mode = 'disable',
  children,
  disabledTip = '您没有权限执行此操作',
}: PermissionButtonProps) {
  const { hasPermission } = usePermission()
  const allowed = hasPermission(permission)

  if (!allowed && mode === 'hide') return null

  if (!allowed) {
    return (
      <Tooltip title={disabledTip}>
        <span style={{ display: 'inline-flex', cursor: 'not-allowed', opacity: 0.5 }}>
          {children}
        </span>
      </Tooltip>
    )
  }

  return <>{children}</>
}
