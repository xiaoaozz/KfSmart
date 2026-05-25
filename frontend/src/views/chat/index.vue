<script setup lang="ts">
import ChatList from './modules/chat-list.vue';
import InputBox from './modules/input-box.vue';
</script>

<template>
  <div class="modern-chat-page h-full flex flex-col relative overflow-hidden">
    <!-- 背景装饰 -->
    <div class="chat-bg-decoration" />

    <!-- 聊天内容区 -->
    <div class="chat-content flex-1 flex flex-col relative z-1">
      <ChatList />
    </div>

    <!-- 输入框区域 -->
    <div class="input-area relative z-2">
      <InputBox />
    </div>
  </div>
</template>

<style scoped lang="scss">
.modern-chat-page {
  position: relative;
  background: #f7fafc;

  /* 背景装饰 */
  .chat-bg-decoration {
    position: absolute;
    inset: 0;
    pointer-events: none;
    z-index: 0;

    &::before {
      content: '';
      position: absolute;
      top: -200px;
      right: -200px;
      width: 500px;
      height: 500px;
      background: radial-gradient(circle, rgba(102, 126, 234, 0.05) 0%, transparent 70%);
      border-radius: 50%;
    }

    &::after {
      content: '';
      position: absolute;
      bottom: -150px;
      left: -150px;
      width: 400px;
      height: 400px;
      background: radial-gradient(circle, rgba(79, 172, 254, 0.05) 0%, transparent 70%);
      border-radius: 50%;
    }
  }

  /* 聊天内容区域 */
  .chat-content {
    min-height: 0;
  }

  /* 输入框区域 */
  .input-area {
    flex-shrink: 0;
    background: linear-gradient(to top, rgba(255, 255, 255, 0.95) 0%, rgba(255, 255, 255, 0.8) 100%);
    backdrop-filter: blur(10px);
    padding-top: 16px;
    box-shadow: 0 -2px 10px rgba(0, 0, 0, 0.05);
  }

  /* 暗色模式 */
  &.dark {
    background: #18181b;

    .chat-bg-decoration {
      &::before {
        background: radial-gradient(circle, rgba(139, 158, 255, 0.08) 0%, transparent 70%);
      }

      &::after {
        background: radial-gradient(circle, rgba(79, 172, 254, 0.08) 0%, transparent 70%);
      }
    }

    .input-area {
      background: linear-gradient(to top, rgba(39, 39, 42, 0.95) 0%, rgba(39, 39, 42, 0.8) 100%);
      box-shadow: 0 -2px 10px rgba(0, 0, 0, 0.2);
    }
  }
}

/* 响应式适配 */
@media (max-width: 768px) {
  .modern-chat-page {
    .input-area {
      padding-top: 12px;
    }
  }
}
</style>
