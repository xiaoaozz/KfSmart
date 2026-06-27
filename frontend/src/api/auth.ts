import JSEncrypt from 'jsencrypt'
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

// 模块级缓存公钥，避免每次登录都额外请求一次
let cachedPublicKey: string | null = null

async function fetchPublicKey(): Promise<string> {
  if (cachedPublicKey) return cachedPublicKey
  const key = await http.get<string>('/users/public-key').then((r) => r.data)
  cachedPublicKey = key
  return key
}

async function encryptPassword(password: string): Promise<string> {
  const publicKey = await fetchPublicKey()
  const encryptor = new JSEncrypt()
  encryptor.setPublicKey(publicKey)
  const encrypted = encryptor.encrypt(password)
  if (!encrypted) throw new Error('密码加密失败，请刷新后重试')
  return encrypted
}

export const authApi = {
  getPublicKey: () => http.get<string>('/users/public-key').then((r) => r.data),

  /** 预取并缓存 RSA 公钥，可在登录页 mount 时调用以消除首次登录的额外 RTT */
  prefetchPublicKey: () => fetchPublicKey().catch(() => {}),

  /** 登录：内部用 RSA 加密密码（后端 /users/login 会解密） */
  login: async (params: LoginParams) => {
    const encryptedPassword = await encryptPassword(params.password)
    return http
      .post<AuthResult>('/users/login', { username: params.username, password: encryptedPassword })
      .then((r) => r.data)
  },

  /** 注册：明文密码（后端 registerUser 不解密，直接 encode） */
  register: (params: RegisterParams) =>
    http.post<AuthResult>('/users/register', params).then((r) => r.data),

  sendEmailCode: (email: string) => http.post<void>('/users/send-email-code', { email }),

  refreshToken: (refreshToken: string) =>
    http.post<AuthResult>('/auth/refreshToken', { refreshToken }).then((r) => r.data),

  logout: () => http.post('/users/logout'),

  logoutAll: () => http.post('/users/logout-all'),
}
