import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { App } from 'antd'
import { useTranslation } from 'react-i18next'
import { profileApi, type AddFavoriteParams } from '@/api/profile'

export function useFavorite() {
  const qc = useQueryClient()
  const { message } = App.useApp()
  const { t } = useTranslation()

  const { data } = useQuery({
    queryKey: ['favorites'],
    queryFn: () => profileApi.getFavorites(),
    staleTime: 30_000,
  })

  const addMutation = useMutation({
    mutationFn: (params: AddFavoriteParams) => profileApi.addFavorite(params),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['favorites'] })
      message.success(t('profile.favorites.favoriteSuccess'))
    },
  })

  const removeMutation = useMutation({
    mutationFn: (id: number) => profileApi.removeFavorite(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['favorites'] })
      message.success(t('profile.favorites.unfavoriteSuccess'))
    },
  })

  function getFavorite(type: string, targetId: string) {
    return (data ?? []).find((f) => f.type === type && f.targetId === targetId)
  }

  function isFavorited(type: string, targetId: string) {
    return !!getFavorite(type, targetId)
  }

  function toggle(params: AddFavoriteParams) {
    const existing = getFavorite(params.type, params.targetId)
    if (existing) {
      removeMutation.mutate(existing.id)
    } else {
      addMutation.mutate(params)
    }
  }

  const isPending = addMutation.isPending || removeMutation.isPending

  return { isFavorited, toggle, isPending }
}
