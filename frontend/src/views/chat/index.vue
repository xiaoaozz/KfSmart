<script setup lang="ts">
import ChatList from './modules/chat-list.vue';
import InputBox from './modules/input-box.vue';
import StatCards from './modules/stat-cards.vue';
import SessionList from './modules/session-list.vue';
import SearchResults from './modules/search-results.vue';
</script>

<template>
  <div class="chat-page flex flex-col h-full bg-gray-50 dark:bg-gray-900">
    <!-- 顶部统计卡片 -->
    <div class="p-6 bg-white dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700">
      <StatCards />
    </div>

    <!-- 主体内容区 -->
    <div class="flex-1 flex overflow-hidden">
      <!-- 左侧：会话列表 -->
      <div class="w-320px border-r border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800">
        <SessionList />
      </div>

      <!-- 中间：对话区域 -->
      <div class="flex-1 flex flex-col">
        <ChatList />
        <InputBox />
      </div>

      <!-- 右侧：检索结果面板 -->
      <div class="w-360px border-l border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800">
        <SearchResults />
      </div>
    </div>
  </div>
</template>

<style scoped></style>
