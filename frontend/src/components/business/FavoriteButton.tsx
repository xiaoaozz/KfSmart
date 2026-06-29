import { Button, Tooltip } from 'antd'
import { StarFilled, StarOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { useFavorite } from '@/hooks/useFavorite'
import type { AddFavoriteParams } from '@/api/profile'

interface FavoriteButtonProps {
  type: AddFavoriteParams['type']
  targetId: string
  title: string
  description?: string
  size?: 'small' | 'middle' | 'large'
  className?: string
}

export default function FavoriteButton({
  type,
  targetId,
  title,
  description,
  size = 'small',
  className,
}: FavoriteButtonProps) {
  const { t } = useTranslation()
  const { isFavorited, toggle, isPending } = useFavorite()
  const favorited = isFavorited(type, targetId)

  return (
    <Tooltip
      title={
        favorited
          ? t('profile.favorites.unfavoriteTooltip')
          : t('profile.favorites.favoriteTooltip')
      }
    >
      <Button
        type="text"
        size={size}
        loading={isPending}
        className={className}
        icon={
          favorited ? (
            <StarFilled style={{ color: '#faad14' }} />
          ) : (
            <StarOutlined style={{ color: 'var(--kf-muted-foreground)' }} />
          )
        }
        onClick={(e) => {
          e.stopPropagation()
          toggle({ type, targetId, title, description })
        }}
      />
    </Tooltip>
  )
}
