import 'vue-markdown-shiki/style';
import markdownPlugin from 'vue-markdown-shiki';
import './plugins/assets';
import { setupAppVersionNotification, setupDayjs, setupIconifyOffline, setupLoading, setupNProgress } from './plugins';
import { setupStore } from './store';
import { setupRouter } from './router';
import { setupI18n } from './locales';
import { setupDirectives } from './directives';
import App from './App.vue';
async function setupApp() {
  setupLoading();

  setupNProgress();

  setupIconifyOffline();

  setupDayjs();

  const app = createApp(App);

  setupStore(app);

  await setupRouter(app);

  setupI18n(app);

  // 注册全局自定义指令（含 v-permission）
  setupDirectives(app);

  setupAppVersionNotification();

  app.use(markdownPlugin);

  app.mount('#app');
}

setupApp();
