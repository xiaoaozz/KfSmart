import type { Directive, DirectiveBinding } from 'vue';
import { useAuthStore } from '@/store/modules/auth';

/**
 * v-permission 自定义指令
 * 用于控制按钮、菜单项等元素的可见性，基于 RBAC 权限编码
 *
 * 用法：
 *   <button v-permission="'kb:write'">创建知识库</button>
 *   <button v-permission="['kb:write', 'kb:admin']">创建知识库（需全部权限）</button>
 *   <button v-permission:any="['kb:write', 'system:admin']">（满足任一权限即可）</button>
 *
 * 说明：
 *   - 无修饰符 / .all 修饰符：用户必须拥有所有指定权限才显示
 *   - .any 修饰符：用户拥有任一指定权限即显示
 */
export const permissionDirective: Directive = {
  mounted(el: HTMLElement, binding: DirectiveBinding) {
    checkPermission(el, binding);
  },
  updated(el: HTMLElement, binding: DirectiveBinding) {
    checkPermission(el, binding);
  }
};

function checkPermission(el: HTMLElement, binding: DirectiveBinding) {
  const authStore = useAuthStore();
  const permCodes: string[] = Array.isArray(binding.value)
    ? binding.value
    : [binding.value as string];

  let hasAccess: boolean;

  if (binding.modifiers.any) {
    // 满足任一权限即可
    hasAccess = authStore.hasAnyPermission(permCodes);
  } else {
    // 默认：必须拥有全部权限
    hasAccess = permCodes.every(code => authStore.hasPermission(code));
  }

  if (!hasAccess) {
    // 移除 DOM 元素（比 display:none 更彻底，防止 CSS 绕过）
    el.parentNode?.removeChild(el);
  }
}
