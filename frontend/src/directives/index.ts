import type { App } from 'vue';
import { permissionDirective } from './permission';

/**
 * 注册所有全局自定义指令
 */
export function setupDirectives(app: App) {
  // v-permission：基于 RBAC 权限编码控制元素可见性
  app.directive('permission', permissionDirective);
}
