import { request } from '../request';

/**
 * Login
 *
 * @param username User name
 * @param password Password
 */
export function fetchLogin(username: string, password: string) {
  return request<Api.Auth.LoginToken>({
    url: '/users/login',
    method: 'post',
    data: {
      username,
      password
    }
  });
}

export function fetchLogout() {
  return request({ url: '/users/logout', method: 'post' });
}

export function fetchRegister(username: string, password: string) {
  return request({
    url: '/users/register',
    method: 'post',
    data: {
      username,
      password
    }
  });
}

/** Get user info */
export function fetchGetUserInfo() {
  return request<Api.Auth.UserInfo>({ url: '/users/me' });
}

/** Update current user avatar */
export function fetchUpdateUserAvatar(file: File) {
  const formData = new FormData();
  formData.append('file', file);

  return request<Pick<Api.Auth.UserInfo, 'avatar'>>({
    url: '/users/me/avatar',
    method: 'post',
    data: formData
  });
}

/** Update current user profile */
export function fetchUpdateUserProfile(data: Pick<Api.User.Profile, 'email' | 'phone' | 'bio'>) {
  return request<Api.User.Profile>({
    url: '/users/me',
    method: 'put',
    data
  });
}

/** Change current user password */
export function fetchChangePassword(oldPassword: string, newPassword: string) {
  return request({
    url: '/users/me/password',
    method: 'put',
    data: { oldPassword, newPassword }
  });
}

export function fetchGetNotificationPreferences() {
  return request<Api.User.NotificationPreferences>({ url: '/users/notification-preferences' });
}

export function fetchUpdateNotificationPreferences(data: Api.User.NotificationPreferences) {
  return request<Api.User.NotificationPreferences>({
    url: '/users/notification-preferences',
    method: 'put',
    data
  });
}

export function fetchGetFavorites() {
  return request<Api.User.FavoriteItem[]>({ url: '/users/favorites' });
}

export function fetchSaveFavorite(data: Api.User.FavoritePayload) {
  return request<Api.User.FavoriteItem>({
    url: '/users/favorites',
    method: 'post',
    data
  });
}

export function fetchUpdateFavoriteStarred(id: number, starred: boolean) {
  return request<Api.User.FavoriteItem>({
    url: `/users/favorites/${id}/starred`,
    method: 'put',
    data: { starred }
  });
}

export function fetchDeleteFavorite(id: number) {
  return request({ url: `/users/favorites/${id}`, method: 'delete' });
}

export function fetchGetOperationRecords() {
  return request<Api.User.OperationRecord[]>({ url: '/users/operation-records' });
}

/** Get personal usage statistics */
export function fetchGetUsageStats(days: 7 | 30 = 7) {
  return request<Api.User.UsageStats>({ url: '/users/usage-stats', params: { days } });
}

/**
 * Refresh token
 *
 * @param refreshToken Refresh token
 */
export function fetchRefreshToken(refreshToken: string) {
  return request<Api.Auth.LoginToken>({
    url: '/auth/refreshToken',
    method: 'post',
    data: {
      refreshToken
    }
  });
}

/**
 * return custom backend error
 *
 * @param code error code
 * @param msg error message
 */
export function fetchCustomBackendError(code: string, msg: string) {
  return request({ url: '/auth/error', params: { code, msg } });
}
