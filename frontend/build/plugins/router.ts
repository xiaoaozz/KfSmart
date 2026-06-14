import type { RouteMeta } from 'vue-router';
import ElegantVueRouter from '@elegant-router/vue/vite';
import type { RouteKey } from '@elegant-router/types';

export function setupElegantRouter() {
  return ElegantVueRouter({
    layouts: {
      base: 'src/layouts/base-layout/index.vue',
      blank: 'src/layouts/blank-layout/index.vue'
    },
    routePathTransformer(routeName, routePath) {
      const key = routeName as RouteKey;

      if (key === 'login') {
        const modules: UnionKey.LoginModule[] = ['pwd-login', 'code-login', 'register', 'reset-pwd', 'bind-wechat'];

        const moduleReg = modules.join('|');

        return `/login/:module(${moduleReg})?`;
      }

      return routePath;
    },
    onRouteMetaGen(routeName) {
      const key = routeName as RouteKey;

      const constantRoutes: RouteKey[] = ['login', '403', '404', '500'];
      const aiCenterMeta: Partial<Record<string, Partial<RouteMeta>>> = {
        'ai-assistant': {
          icon: 'solar:chat-round-call-line-duotone',
          order: 1
        },
        'ai-assistant_chat': {
          icon: 'solar:chat-round-call-line-duotone',
          order: 1
        },
        'ai-assistant_knowledge-base': {
          icon: 'solar:folder-line-duotone',
          order: 2
        },
        'ai-assistant_document-management': {
          icon: 'carbon:document',
          order: 3
        },
        'ai-center': {
          icon: 'carbon:ai-status',
          order: 3.5
        },
        'ai-center_mcp-tools': {
          icon: 'carbon:tool-kit',
          order: 2
        },
        'ai-center_prompt-management': {
          icon: 'carbon:text-annotation-toggle',
          order: 3
        },
        'ai-center_model-management': {
          icon: 'carbon:model',
          order: 4
        },
        'ai-center_agent-marketplace': {
          icon: 'carbon:store',
          order: 5
        },
        'ai-center_run-analysis': {
          icon: 'carbon:chart-line-data',
          order: 6
        }
      };

      const meta: Partial<RouteMeta> = {
        title: key,
        i18nKey: `route.${key}` as App.I18n.I18nKey,
        ...aiCenterMeta[key]
      };

      if (constantRoutes.includes(key)) {
        meta.constant = true;
      }

      return meta;
    }
  });
}
