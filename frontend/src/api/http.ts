import axios, {
  type AxiosInstance,
  type InternalAxiosRequestConfig,
  type AxiosResponse,
} from 'axios'
import i18n from '../i18n'
import {
  getStoredRefreshToken,
  updateStoredToken,
  clearAuthStorage,
  getStoredToken,
} from '../stores/auth'

// ------------------------------------------------------------------ camelCase
function toCamel(s: string): string {
  return s.replace(/_([a-z])/g, (_, c: string) => c.toUpperCase())
}

function deepCamel<T>(data: T): T {
  if (Array.isArray(data)) return data.map(deepCamel) as T
  if (data !== null && typeof data === 'object') {
    return Object.fromEntries(
      Object.entries(data as Record<string, unknown>).map(([k, v]) => [toCamel(k), deepCamel(v)]),
    ) as T
  }
  return data
}

// ------------------------------------------------------------------ instance
export const http: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api/v1',
  timeout: 30_000,
  headers: { 'Content-Type': 'application/json' },
})

// ------------------------------------------------------------------ token refresh queue
let isRefreshing = false
let refreshQueue: Array<(token: string) => void> = []

function drainQueue(token: string) {
  refreshQueue.forEach((cb) => cb(token))
  refreshQueue = []
}

async function doRefresh(): Promise<string> {
  const refreshToken = getStoredRefreshToken()
  if (!refreshToken) throw new Error('no_refresh_token')

  const res = await axios.post<{ data: { token: string } }>(
    `${import.meta.env.VITE_API_BASE_URL ?? '/api/v1'}/auth/refreshToken`,
    { refreshToken },
  )
  const newToken = res.data.data.token
  updateStoredToken(newToken)
  return newToken
}

// ------------------------------------------------------------------ request interceptor
http.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = getStoredToken()
  if (token) config.headers.Authorization = `Bearer ${token}`

  const localeRaw = localStorage.getItem('kf-locale')
  const locale: string = localeRaw ? (JSON.parse(localeRaw)?.state?.locale ?? 'zh-CN') : 'zh-CN'
  config.headers['Accept-Language'] = locale

  return config
})

// ------------------------------------------------------------------ response interceptor
http.interceptors.response.use(
  (res: AxiosResponse) => {
    // Unwrap envelope: { code, message, data } → data
    const payload = (res.data as Record<string, unknown>)?.data ?? res.data
    res.data = deepCamel(payload)
    return res
  },
  async (error) => {
    const original = error.config as InternalAxiosRequestConfig & { _retry?: boolean }
    const status: number | undefined = error.response?.status

    // Auth endpoints (login/register) should never trigger token refresh — surface error directly
    const isAuthEndpoint = /\/users\/(login|register|send-email-code)$/.test(original.url ?? '')

    if (status === 401 && !original._retry && !isAuthEndpoint) {
      original._retry = true
      if (isRefreshing) {
        return new Promise((resolve) => {
          refreshQueue.push((token) => {
            original.headers.Authorization = `Bearer ${token}`
            resolve(http(original))
          })
        })
      }
      isRefreshing = true
      try {
        const newToken = await doRefresh()
        isRefreshing = false
        drainQueue(newToken)
        original.headers.Authorization = `Bearer ${newToken}`
        return http(original)
      } catch {
        isRefreshing = false
        refreshQueue = []
        clearAuthStorage()
        window.location.href = '/login'
        return Promise.reject(error)
      }
    }

    // Surface error to components via antd message — imported lazily to avoid circular deps
    const msg: string =
      ((error.response?.data as Record<string, unknown>)?.message as string) ||
      error.message ||
      i18n.t('common.requestFailed')

    import('antd').then(({ message }) => {
      message.error(msg)
    })

    return Promise.reject(error)
  },
)
