import { computed, type Ref } from 'vue';

function getServiceOrigin() {
  const rawBaseUrl = import.meta.env.VITE_SERVICE_BASE_URL || '';

  if (rawBaseUrl.startsWith('http://') || rawBaseUrl.startsWith('https://')) {
    return rawBaseUrl.replace(/\/api\/v1\/?$/, '').replace(/\/$/, '');
  }

  return '';
}

export function getAvatarText(username?: string) {
  return (username || 'U').trim().charAt(0).toUpperCase() || 'U';
}

export function getVersionedAvatarUrl(avatar?: string | null, version?: number) {
  if (!avatar) {
    return '';
  }

  const baseAvatarUrl = avatar.startsWith('http://') || avatar.startsWith('https://') || avatar.startsWith('data:')
    ? avatar
    : avatar.startsWith('/avatars/')
      ? `${getServiceOrigin()}${avatar}`
      : avatar;

  if (!version) {
    return baseAvatarUrl;
  }

  const separator = baseAvatarUrl.includes('?') ? '&' : '?';
  return `${baseAvatarUrl}${separator}v=${version}`;
}

export function useUserAvatar(userInfo: Ref<Api.Auth.UserInfo>) {
  const avatarVersion = computed(() => userInfo.value.avatarVersion || 0);
  const avatarUrl = computed(() => getVersionedAvatarUrl(userInfo.value.avatar, avatarVersion.value));
  const avatarText = computed(() => getAvatarText(userInfo.value.username));

  return {
    avatarUrl,
    avatarText
  };
}
