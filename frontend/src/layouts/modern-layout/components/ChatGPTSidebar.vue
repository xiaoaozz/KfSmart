<script setup lang="ts">
import { ref, computed } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '@/store/modules/auth';

defineOptions({
  name: 'ChatGPTSidebar'
});

const router = useRouter();
const authStore = useAuthStore();

// 主要功能菜单
const mainMenus = [
  {
    name: '新聊天',
    icon: 'i-carbon:edit',
    action: 'newChat'
  },
  {
    name: '搜索聊天',
    icon: 'i-carbon:search',
    action: 'searchChat'
  },
  {
    name: '项目',
    icon: 'i-carbon:folder',
    path: '/knowledge-base'
  },
  {
    name: '库',
    icon: 'i-carbon:document-multiple',
    path: '/knowledge-base'
  },
  {
    name: '应用',
    icon: 'i-carbon:application',
    path: '/dashboard'
  },
  {
    name: 'Codex',
    icon: 'i-carbon:code',
    path: '/dashboard'
  },
  {
    name: '更多',
    icon: 'i-carbon:overflow-menu-horizontal',
    action: 'more'
  }
];

// 最近对话列表（模拟数据，实际应从API获取）
const recentChats = ref([
  { id: 1, title: 'New chat' },
  { id: 2, title: 'KnowFlow 图标设计' },
  { id: 3, title: 'AI知识库命名建议' },
  { id: 4, title: '派聪明命名与图标设计' },
  { id: 5, title: '风控测试人物总结' },
  { id: 6, title: '快手15周年祝福' },
  { id: 7, title: 'Mac 切换 Chrome 标签' },
  { id: 8, title: 'UI设计需求解析' },
  { id: 9, title: 'Podman启动docker-compose服务' },
  { id: 10, title: '智能体网站UI设计' },
  { id: 11, title: 'UI设计图生成请求' },
  { id: 12, title: '登录页面设计提示' },
  { id: 13, title: 'API Key 费用分析' },
  { id: 14, title: 'GPT-5.5介绍' },
  { id: 15, title: 'AgentScope 项目分析' },
  { id: 16, title: '多Agent协作UI参考' }
]);

const currentPath = computed(() => router.currentRoute.value.path);
const activeChat = ref<number | null>(null);

function handleMenuClick(menu: any) {
  if (menu.path) {
    router.push(menu.path);
  } else if (menu.action === 'newChat') {
    router.push('/chat');
    activeChat.value = null;
  } else if (menu.action === 'searchChat') {
    // TODO: 实现搜索功能
    console.log('搜索聊天');
  } else if (menu.action === 'more') {
    // TODO: 实现更多菜单
    console.log('更多');
  }
}

function handleChatClick(chat: any) {
  activeChat.value = chat.id;
  router.push(`/chat?id=${chat.id}`);
}
</script>

<template>
  <aside class="chatgpt-sidebar">
    <!-- Logo 区域 -->
    <div class="sidebar-header">
      <div class="logo-section">
        <span class="logo-text">ChatGPT</span>
        <button class="toggle-btn">
          <div class="i-carbon:chevron-sort" />
        </button>
      </div>
    </div>

    <!-- 主要功能菜单 -->
    <div class="main-menu">
      <button
        v-for="menu in mainMenus"
        :key="menu.name"
        class="menu-item"
        @click="handleMenuClick(menu)"
      >
        <div :class="menu.icon" class="menu-icon" />
        <span class="menu-label">{{ menu.name }}</span>
      </button>
    </div>

    <!-- 最近对话列表 -->
    <div class="recent-section">
      <div class="section-title">最近</div>
      <div class="chat-list">
        <button
          v-for="chat in recentChats"
          :key="chat.id"
          :class="['chat-item', { active: activeChat === chat.id }]"
          @click="handleChatClick(chat)"
        >
          <div class="i-carbon:chat" class="chat-icon" />
          <span class="chat-title">{{ chat.title }}</span>
          <div class="chat-actions">
            <button class="action-btn" @click.stop>
              <div class="i-carbon:overflow-menu-horizontal" />
            </button>
          </div>
        </button>
      </div>
    </div>

    <!-- 底部用户信息 -->
    <div class="sidebar-footer">
      <button class="user-profile">
        <div class="user-avatar">
          <div class="i-carbon:user-avatar-filled" />
        </div>
        <div class="user-info">
          <div class="user-name">{{ authStore.userInfo.username || 'Bigtofu' }}</div>
          <div class="user-badge">Plus</div>
        </div>
        <div class="i-carbon:settings" class="settings-icon" />
      </button>
    </div>
  </aside>
</template>

<style scoped lang="scss">
.chatgpt-sidebar {
  width: 260px;
  height: 100vh;
  background: #f9f9f9;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  border-right: 1px solid #e5e5e5;
  overflow: hidden;

  .dark & {
    background: #171717;
    border-right-color: #2a2a2a;
  }
}

.sidebar-header {
  padding: 12px 12px 8px;
  border-bottom: 1px solid #e5e5e5;

  .dark & {
    border-bottom-color: #2a2a2a;
  }

  .logo-section {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 8px 12px;

    .logo-text {
      font-size: 18px;
      font-weight: 600;
      color: #000;

      .dark & {
        color: #fff;
      }
    }

    .toggle-btn {
      width: 28px;
      height: 28px;
      display: flex;
      align-items: center;
      justify-content: center;
      background: transparent;
      border: none;
      border-radius: 6px;
      cursor: pointer;
      color: #666;
      transition: all 0.2s;

      .dark & {
        color: #999;
      }

      &:hover {
        background: #e5e5e5;

        .dark & {
          background: #2a2a2a;
        }
      }

      div {
        font-size: 18px;
      }
    }
  }
}

.main-menu {
  padding: 8px 8px 12px;
  border-bottom: 1px solid #e5e5e5;

  .dark & {
    border-bottom-color: #2a2a2a;
  }

  .menu-item {
    width: 100%;
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 10px 12px;
    background: transparent;
    border: none;
    border-radius: 8px;
    cursor: pointer;
    transition: all 0.15s;
    text-align: left;
    color: #333;
    font-size: 14px;

    .dark & {
      color: #d4d4d4;
    }

    .menu-icon {
      font-size: 18px;
      flex-shrink: 0;
      color: #666;

      .dark & {
        color: #999;
      }
    }

    .menu-label {
      flex: 1;
      font-weight: 400;
    }

    &:hover {
      background: #ececec;

      .dark & {
        background: #2a2a2a;
      }
    }

    &:active {
      transform: scale(0.98);
    }
  }
}

.recent-section {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  padding: 8px 0;

  .section-title {
    padding: 8px 20px;
    font-size: 12px;
    font-weight: 600;
    color: #666;

    .dark & {
      color: #999;
    }
  }

  .chat-list {
    flex: 1;
    overflow-y: auto;
    overflow-x: hidden;
    padding: 0 8px;

    &::-webkit-scrollbar {
      width: 6px;
    }

    &::-webkit-scrollbar-track {
      background: transparent;
    }

    &::-webkit-scrollbar-thumb {
      background: #d4d4d4;
      border-radius: 3px;

      .dark & {
        background: #404040;
      }

      &:hover {
        background: #b8b8b8;

        .dark & {
          background: #525252;
        }
      }
    }
  }

  .chat-item {
    width: 100%;
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 10px 12px;
    background: transparent;
    border: none;
    border-radius: 8px;
    cursor: pointer;
    transition: all 0.15s;
    text-align: left;
    color: #333;
    font-size: 14px;
    margin-bottom: 2px;
    position: relative;

    .dark & {
      color: #d4d4d4;
    }

    .chat-icon {
      font-size: 16px;
      flex-shrink: 0;
      color: #666;

      .dark & {
        color: #999;
      }
    }

    .chat-title {
      flex: 1;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      font-weight: 400;
    }

    .chat-actions {
      opacity: 0;
      transition: opacity 0.15s;

      .action-btn {
        width: 24px;
        height: 24px;
        display: flex;
        align-items: center;
        justify-content: center;
        background: transparent;
        border: none;
        border-radius: 4px;
        cursor: pointer;
        color: #666;

        .dark & {
          color: #999;
        }

        &:hover {
          background: #d4d4d4;

          .dark & {
            background: #404040;
          }
        }

        div {
          font-size: 16px;
        }
      }
    }

    &:hover {
      background: #ececec;

      .dark & {
        background: #2a2a2a;
      }

      .chat-actions {
        opacity: 1;
      }
    }

    &.active {
      background: #e5e5e5;

      .dark & {
        background: #2f2f2f;
      }
    }

    &:active {
      transform: scale(0.98);
    }
  }
}

.sidebar-footer {
  padding: 12px;
  border-top: 1px solid #e5e5e5;

  .dark & {
    border-top-color: #2a2a2a;
  }

  .user-profile {
    width: 100%;
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 10px 12px;
    background: transparent;
    border: none;
    border-radius: 8px;
    cursor: pointer;
    transition: all 0.15s;

    &:hover {
      background: #ececec;

      .dark & {
        background: #2a2a2a;
      }
    }

    .user-avatar {
      width: 32px;
      height: 32px;
      background: linear-gradient(135deg, #10b981 0%, #059669 100%);
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;

      div {
        font-size: 20px;
        color: white;
      }
    }

    .user-info {
      flex: 1;
      text-align: left;

      .user-name {
        font-size: 14px;
        font-weight: 500;
        color: #000;
        line-height: 1.3;

        .dark & {
          color: #fff;
        }
      }

      .user-badge {
        font-size: 12px;
        color: #666;
        line-height: 1.3;

        .dark & {
          color: #999;
        }
      }
    }

    .settings-icon {
      font-size: 18px;
      color: #666;
      flex-shrink: 0;

      .dark & {
        color: #999;
      }
    }
  }
}
</style>
