import { Avatar } from 'antd'
import type { AvatarProps } from 'antd'

interface UserAvatarProps extends Omit<AvatarProps, 'src' | 'size'> {
  avatar?: string | null
  username?: string | null
  size?: number
}

export default function UserAvatar({
  avatar,
  username,
  size = 32,
  style,
  ...rest
}: UserAvatarProps) {
  return (
    <Avatar
      size={size}
      src={avatar || undefined}
      style={{ background: 'var(--kf-accent)', ...style }}
      {...rest}
    >
      {username?.[0]?.toUpperCase()}
    </Avatar>
  )
}
