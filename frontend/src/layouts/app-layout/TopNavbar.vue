<template>
  <nav class="top-navbar">
    <div class="top-navbar__container">
      <!-- 左侧：Logo和标题 -->
      <div class="top-navbar__left">
        <!-- 菜单按钮 (移动端) -->
        <IconButton
          v-if="showMenuButton"
          icon="mdi:menu"
          color="primary"
          size="medium"
          class="top-navbar__menu-btn"
          @click="handleToggleSidebar"
        />

        <!-- Logo -->
        <div class="top-navbar__logo" @click="handleLogoClick">
          <div class="logo-icon">
            <icon-component name="mdi:robot" class="text-icon-xl" />
          </div>
          <h1 v-if="!hideTitle" class="logo-text">{{ title }}</h1>
        </div>

        <!-- 导航链接 (可选) -->
        <div v-if="navItems.length > 0" class="top-navbar__nav">
          <a
            v-for="item in navItems"
            :key="item.key"
            :href="item.href"
            :class="['nav-item', { 'is-active': item.key === activeKey }]"
            @click.prevent="handleNavClick(item)"
          >
            <icon-component v-if="item.icon" :name="item.icon" class="text-icon-md" />
            <span>{{ item.label }}</span>
          </a>
        </div>
      </div>

      <!-- 右侧：搜索、通知、用户 -->
      <div class="top-navbar__right">
        <!-- 搜索框 (可选) -->
        <div v-if="showSearch" class="top-navbar__search">
          <SearchBar
            v-model="searchKeyword"
            :placeholder="searchPlaceholder"
            size="small"
            @search="handleSearch"
          />
        </div>

        <!-- 通知按钮 -->
        <div v-if="showNotification" class="top-navbar__action">
          <IconButton
            icon="mdi:bell-outline"
            color="primary"
            size="medium"
            @click="handleNotificationClick"
          />
          <span v-if="notificationCount > 0" class="notification-badge">
            {{ notificationCount > 99 ? '99+' : notificationCount }}
          </span>
        </div>

        <!-- 帮助按钮 -->
        <div v-if="showHelp" class="top-navbar__action">
          <IconButton
            icon="mdi:help-circle-outline"
            color="primary"
            size="medium"
            @click="handleHelpClick"
          />
        </div>

        <!-- 用户信息 -->
        <div v-if="user" class="top-navbar__user" @click="handleUserClick">
          <Avatar
            :src="user.avatar"
            :text="user.name"
            :size="32"
            class="user-avatar"
          />
          <div v-if="!hideUserInfo" class="user-info">
            <p class="user-name">{{ user.name }}</p>
            <p v-if="user.role" class="user-role">{{ user.role }}</p>
          </div>
          <icon-component name="mdi:chevron-down" class="text-icon-sm user-dropdown-icon" />
        </div>

        <!-- 退出按钮 (无用户时) -->
        <ModernButton
          v-else-if="showLogout"
          variant="outline"
          color="primary"
          size="small"
          icon="mdi:logout"
          @click="handleLogout"
        >
          退出登录
        </ModernButton>
      </div>
    </div>
  </nav>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { IconButton, ModernButton, SearchBar, Avatar } from '@/components/base';

interface NavItem {
  key: string;
  label: string;
  href: string;
  icon?: string;
}

interface User {
  id: string | number;
  name: string;
  avatar?: string;
  role?: string;
}

interface TopNavbarProps {
  title?: string;
  hideTitle?: boolean;
  hideUserInfo?: boolean;
  showMenuButton?: boolean;
  showSearch?: boolean;
  showNotification?: boolean;
  showHelp?: boolean;
  showLogout?: boolean;
  searchPlaceholder?: string;
  notificationCount?: number;
  navItems?: NavItem[];
  activeKey?: string;
  user?: User | null;
}

const props = withDefaults(defineProps<TopNavbarProps>(), {
  title: 'KnowFlow',
  hideTitle: false,
  hideUserInfo: false,
  showMenuButton: false,
  showSearch: true,
  showNotification: true,
  showHelp: true,
  showLogout: false,
  searchPlaceholder: '搜索...',
  notificationCount: 0,
  navItems: () => [],
  activeKey: '',
  user: null,
});

const emit = defineEmits<{
  logoClick: [];
  toggleSidebar: [];
  search: [keyword: string];
  navClick: [item: NavItem];
  notificationClick: [];
  helpClick: [];
  userClick: [];
  logout: [];
}>();

const searchKeyword = ref('');

const handleLogoClick = () => {
  emit('logoClick');
};

const handleToggleSidebar = () => {
  emit('toggleSidebar');
};

const handleSearch = (keyword: string) => {
  emit('search', keyword);
};

const handleNavClick = (item: NavItem) => {
  emit('navClick', item);
};

const handleNotificationClick = () => {
  emit('notificationClick');
};

const handleHelpClick = () => {
  emit('helpClick');
};

const handleUserClick = () => {
  emit('userClick');
};

const handleLogout = () => {
  emit('logout');
};
</script>

<style scoped>
.top-navbar {
  height: 64px;
  display: flex;
  align-items: center;
  padding: 0 var(--spacing-lg);
}

.top-navbar__container {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--spacing-lg);
}

/* 左侧 */
.top-navbar__left {
  display: flex;
  align-items: center;
  gap: var(--spacing-lg);
  flex: 1;
  min-width: 0;
}

.top-navbar__menu-btn {
  display: none;
}

.top-navbar__logo {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-out-cubic);
  padding: var(--spacing-xs);
  border-radius: var(--radius-md);
}

.top-navbar__logo:hover {
  background: rgba(102, 126, 234, 0.08);
}

.logo-icon {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--gradient-primary);
  border-radius: var(--radius-md);
  color: white;
}

.logo-text {
  font-size: 20px;
  font-weight: 700;
  background: var(--gradient-primary);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  white-space: nowrap;
}

.top-navbar__nav {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
}

.nav-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  padding: var(--spacing-xs) var(--spacing-md);
  border-radius: var(--radius-md);
  color: var(--text-secondary);
  font-size: 14px;
  font-weight: 500;
  text-decoration: none;
  transition: all var(--duration-base) var(--ease-out-cubic);
  cursor: pointer;
}

.nav-item:hover {
  background: rgba(102, 126, 234, 0.08);
  color: var(--color-primary-500);
}

.nav-item.is-active {
  background: rgba(102, 126, 234, 0.12);
  color: var(--color-primary-500);
}

/* 右侧 */
.top-navbar__right {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
}

.top-navbar__search {
  width: 240px;
}

.top-navbar__action {
  position: relative;
}

.notification-badge {
  position: absolute;
  top: -4px;
  right: -4px;
  min-width: 18px;
  height: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-error-500);
  color: white;
  font-size: 11px;
  font-weight: 600;
  border-radius: 9px;
  padding: 0 4px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

/* 用户信息 */
.top-navbar__user {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-xs) var(--spacing-sm);
  border-radius: var(--radius-lg);
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-out-cubic);
}

.top-navbar__user:hover {
  background: rgba(102, 126, 234, 0.08);
}

.user-avatar {
  flex-shrink: 0;
}

.user-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.user-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.user-role {
  font-size: 12px;
  color: var(--text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.user-dropdown-icon {
  flex-shrink: 0;
  color: var(--text-tertiary);
  transition: transform var(--duration-base) var(--ease-out-cubic);
}

.top-navbar__user:hover .user-dropdown-icon {
  transform: translateY(2px);
}

/* 响应式设计 */
@media (max-width: 1024px) {
  .top-navbar__search {
    width: 180px;
  }

  .top-navbar__nav {
    display: none;
  }
}

@media (max-width: 768px) {
  .top-navbar {
    padding: 0 var(--spacing-md);
  }

  .top-navbar__menu-btn {
    display: flex;
  }

  .top-navbar__search {
    display: none;
  }

  .logo-text {
    display: none;
  }

  .user-info {
    display: none;
  }
}

/* 暗色模式 */
.dark .top-navbar__logo:hover {
  background: rgba(102, 126, 234, 0.15);
}

.dark .nav-item:hover {
  background: rgba(102, 126, 234, 0.15);
}

.dark .nav-item.is-active {
  background: rgba(102, 126, 234, 0.2);
}

.dark .top-navbar__user:hover {
  background: rgba(102, 126, 234, 0.15);
}
</style>
