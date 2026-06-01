<script setup lang="ts">
import { ref, computed } from 'vue';
import { useRouter } from 'vue-router';

defineOptions({
  name: 'ModernSidebar'
});

const router = useRouter();

const collapsed = ref(false);

// 侧边栏菜单配置
const sidebarMenus = [
  {
    title: '工作台',
    icon: 'i-carbon:dashboard',
    children: [
      { name: '概览', path: '/dashboard', icon: 'i-carbon:analytics' },
      { name: '统计', path: '/statistics', icon: 'i-carbon:chart-bar' }
    ]
  },
  {
    title: '内容管理',
    icon: 'i-carbon:document',
    children: [
      { name: '知识库', path: '/knowledge-base', icon: 'i-carbon:folder' },
      { name: '标签管理', path: '/org-tag', icon: 'i-carbon:tag' }
    ]
  }
];

const currentPath = computed(() => router.currentRoute.value.path);

function isActive(path: string) {
  return currentPath.value === path;
}

function navigateTo(path: string) {
  router.push(path);
}

function toggleCollapse() {
  collapsed.value = !collapsed.value;
}
</script>

<template>
  <aside :class="['modern-sidebar', { collapsed }]">
    <!-- 折叠按钮 -->
    <button class="collapse-btn" @click="toggleCollapse">
      <div v-if="collapsed" class="i-carbon:chevron-right" />
      <div v-else class="i-carbon:chevron-left" />
    </button>

    <!-- 菜单内容 -->
    <div class="sidebar-content">
      <div v-for="(group, index) in sidebarMenus" :key="index" class="menu-group">
        <div v-if="!collapsed" class="group-title">
          <div :class="group.icon" />
          <span>{{ group.title }}</span>
        </div>

        <div class="menu-items">
          <button
            v-for="item in group.children"
            :key="item.path"
            :class="['menu-item', { active: isActive(item.path) }]"
            :title="collapsed ? item.name : ''"
            @click="navigateTo(item.path)"
          >
            <div :class="[item.icon, 'item-icon']" />
            <span v-if="!collapsed" class="item-label">{{ item.name }}</span>
          </button>
        </div>
      </div>
    </div>
  </aside>
</template>

<style scoped lang="scss">
.modern-sidebar {
  width: 240px;
  flex-shrink: 0;
  background: white;
  border-right: 1px solid rgba(0, 0, 0, 0.06);
  position: relative;
  transition: width 0.3s ease;
  overflow: hidden;

  &.collapsed {
    width: 64px;
  }

  .dark & {
    background: #27272a;
    border-right-color: rgba(255, 255, 255, 0.06);
  }
}

.collapse-btn {
  position: absolute;
  top: 16px;
  right: -12px;
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: white;
  border: 1px solid rgba(0, 0, 0, 0.1);
  border-radius: 50%;
  cursor: pointer;
  z-index: 10;
  transition: all 0.2s ease;
  color: #667eea;

  .dark & {
    background: #27272a;
    border-color: rgba(255, 255, 255, 0.1);
  }

  &:hover {
    background: #667eea;
    color: white;
    transform: scale(1.1);
  }
}

.sidebar-content {
  padding: 24px 12px;
  height: 100%;
  overflow-y: auto;
  overflow-x: hidden;

  &::-webkit-scrollbar {
    width: 4px;
  }

  &::-webkit-scrollbar-thumb {
    background: rgba(102, 126, 234, 0.2);
    border-radius: 2px;
  }
}

.menu-group {
  margin-bottom: 24px;

  &:last-child {
    margin-bottom: 0;
  }

  .group-title {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 12px;
    font-size: 12px;
    font-weight: 600;
    color: #999;
    text-transform: uppercase;
    letter-spacing: 0.5px;
    margin-bottom: 8px;
  }
}

.menu-items {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border: none;
  background: transparent;
  color: #666;
  font-size: 14px;
  font-weight: 500;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;
  width: 100%;
  text-align: left;

  .dark & {
    color: #999;
  }

  .item-icon {
    font-size: 18px;
    flex-shrink: 0;
  }

  .item-label {
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  &:hover {
    background: rgba(102, 126, 234, 0.08);
    color: #667eea;
  }

  &.active {
    background: rgba(102, 126, 234, 0.15);
    color: #667eea;
    font-weight: 600;
  }

  .collapsed & {
    justify-content: center;
    padding: 10px;
  }
}
</style>
