import { http } from './http'

export interface LoginParams {
  username: string
  password: string
  remember?: boolean
}

export interface RegisterParams {
  username: string
  email: string
  password: string
  emailCode: string
}

export interface AuthResult {
  token: string
  refreshToken: string
}

export const authApi = {
  login: (params: LoginParams) => http.post<AuthResult>('/users/login', params).then((r) => r.data),

  register: (params: RegisterParams) =>
    http.post<AuthResult>('/users/register', params).then((r) => r.data),

  sendEmailCode: (email: string) => http.post<void>('/users/send-email-code', { email }),

  refreshToken: (refreshToken: string) =>
    http.post<AuthResult>('/auth/refreshToken', { refreshToken }).then((r) => r.data),

  logout: () => http.post('/users/logout'),
}
