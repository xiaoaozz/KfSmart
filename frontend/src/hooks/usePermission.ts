import { useQuery } from '@tanstack/react-query'
import { http } from '@/api/http'
import type { UserInfo } from '@/types/user'

export function useCurrentUser() {
  return useQuery<UserInfo>({
    queryKey: ['users', 'me'],
    queryFn: () => http.get<UserInfo>('/users/me').then((r) => r.data),
    staleTime: 5 * 60_000,
    retry: false,
  })
}

export function usePermission() {
  const { data: user } = useCurrentUser()

  // 后端 role 为枚举名（ADMIN/USER），统一大小写无关判断
  const isAdmin = user?.role?.toUpperCase() === 'ADMIN'

  const hasPermission = (permission: string): boolean => {
    if (!user) return false
    if (isAdmin) return true
    return user.permissions.includes(permission)
  }

  const isAuthenticated = !!user

  return { hasPermission, isAdmin, isAuthenticated, user }
}
