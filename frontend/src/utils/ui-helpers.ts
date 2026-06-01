/**
 * 响应式和性能优化工具函数
 * PaiSmart Frontend Utils
 */

/**
 * 防抖函数
 * @param fn 要执行的函数
 * @param delay 延迟时间（毫秒）
 */
export function debounce<T extends (...args: any[]) => any>(
  fn: T,
  delay: number = 300
): (...args: Parameters<T>) => void {
  let timeoutId: ReturnType<typeof setTimeout> | null = null;
  
  return function(this: any, ...args: Parameters<T>) {
    if (timeoutId) {
      clearTimeout(timeoutId);
    }
    timeoutId = setTimeout(() => {
      fn.apply(this, args);
    }, delay);
  };
}

/**
 * 节流函数
 * @param fn 要执行的函数
 * @param limit 时间间隔（毫秒）
 */
export function throttle<T extends (...args: any[]) => any>(
  fn: T,
  limit: number = 300
): (...args: Parameters<T>) => void {
  let inThrottle: boolean = false;
  
  return function(this: any, ...args: Parameters<T>) {
    if (!inThrottle) {
      fn.apply(this, args);
      inThrottle = true;
      setTimeout(() => {
        inThrottle = false;
      }, limit);
    }
  };
}

/**
 * 图片懒加载指令
 * 使用: v-lazy-load="imageSrc"
 */
export const vLazyLoad = {
  mounted(el: HTMLImageElement, binding: any) {
    const imageSrc = binding.value;
    
    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            el.src = imageSrc;
            el.classList.add('loaded');
            observer.unobserve(el);
          }
        });
      },
      {
        rootMargin: '50px'
      }
    );
    
    observer.observe(el);
  }
};

/**
 * 滚动显示动画指令
 * 使用: v-scroll-reveal
 */
export const vScrollReveal = {
  mounted(el: HTMLElement) {
    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            el.classList.add('is-visible');
            observer.unobserve(el);
          }
        });
      },
      {
        threshold: 0.1,
        rootMargin: '0px 0px -50px 0px'
      }
    );
    
    el.classList.add('scroll-reveal');
    observer.observe(el);
  }
};

/**
 * 响应式断点
 */
export const breakpoints = {
  xs: 0,
  sm: 640,
  md: 768,
  lg: 1024,
  xl: 1280,
  '2xl': 1536
};

/**
 * 检查当前屏幕尺寸
 */
export function useBreakpoint() {
  const width = ref(window.innerWidth);
  
  const updateWidth = throttle(() => {
    width.value = window.innerWidth;
  }, 200);
  
  onMounted(() => {
    window.addEventListener('resize', updateWidth);
  });
  
  onUnmounted(() => {
    window.removeEventListener('resize', updateWidth);
  });
  
  return {
    width,
    isMobile: computed(() => width.value < breakpoints.md),
    isTablet: computed(() => width.value >= breakpoints.md && width.value < breakpoints.lg),
    isDesktop: computed(() => width.value >= breakpoints.lg),
    isLargeDesktop: computed(() => width.value >= breakpoints.xl)
  };
}

/**
 * 虚拟滚动 Hook
 * 用于优化大列表渲染性能
 */
export function useVirtualScroll<T>(
  items: Ref<T[]>,
  itemHeight: number = 60,
  containerHeight: number = 500
) {
  const scrollTop = ref(0);
  const visibleCount = Math.ceil(containerHeight / itemHeight) + 2;
  
  const startIndex = computed(() => 
    Math.max(0, Math.floor(scrollTop.value / itemHeight) - 1)
  );
  
  const endIndex = computed(() => 
    Math.min(items.value.length, startIndex.value + visibleCount)
  );
  
  const visibleItems = computed(() => 
    items.value.slice(startIndex.value, endIndex.value)
  );
  
  const offsetY = computed(() => startIndex.value * itemHeight);
  
  const totalHeight = computed(() => items.value.length * itemHeight);
  
  const handleScroll = throttle((event: Event) => {
    const target = event.target as HTMLElement;
    scrollTop.value = target.scrollTop;
  }, 16); // ~60fps
  
  return {
    visibleItems,
    offsetY,
    totalHeight,
    handleScroll,
    containerHeight
  };
}

/**
 * 性能监控
 */
export function usePerformanceMonitor() {
  const metrics = reactive({
    fps: 0,
    memory: 0,
    loadTime: 0
  });
  
  // FPS 监控
  let lastTime = performance.now();
  let frames = 0;
  
  function measureFPS() {
    frames++;
    const currentTime = performance.now();
    
    if (currentTime >= lastTime + 1000) {
      metrics.fps = Math.round((frames * 1000) / (currentTime - lastTime));
      frames = 0;
      lastTime = currentTime;
    }
    
    requestAnimationFrame(measureFPS);
  }
  
  // 内存监控
  function measureMemory() {
    if ('memory' in performance) {
      const memory = (performance as any).memory;
      metrics.memory = Math.round(memory.usedJSHeapSize / 1048576); // MB
    }
  }
  
  // 加载时间
  function measureLoadTime() {
    const navigation = performance.getEntriesByType('navigation')[0] as any;
    if (navigation) {
      metrics.loadTime = Math.round(navigation.loadEventEnd - navigation.fetchStart);
    }
  }
  
  onMounted(() => {
    measureFPS();
    measureMemory();
    measureLoadTime();
    
    const memoryInterval = setInterval(measureMemory, 5000);
    
    onUnmounted(() => {
      clearInterval(memoryInterval);
    });
  });
  
  return metrics;
}

/**
 * 暗色模式检测
 */
export function useDarkMode() {
  const isDark = ref(
    window.matchMedia('(prefers-color-scheme: dark)').matches
  );
  
  const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
  
  const handleChange = (e: MediaQueryListEvent) => {
    isDark.value = e.matches;
  };
  
  onMounted(() => {
    mediaQuery.addEventListener('change', handleChange);
  });
  
  onUnmounted(() => {
    mediaQuery.removeEventListener('change', handleChange);
  });
  
  return isDark;
}

/**
 * 平滑滚动到指定位置
 */
export function smoothScrollTo(
  element: HTMLElement | Window,
  top: number,
  duration: number = 300
) {
  const start = element instanceof Window ? window.pageYOffset : element.scrollTop;
  const change = top - start;
  const startTime = performance.now();
  
  function easeInOutQuad(t: number): number {
    return t < 0.5 ? 2 * t * t : -1 + (4 - 2 * t) * t;
  }
  
  function animateScroll(currentTime: number) {
    const elapsed = currentTime - startTime;
    const progress = Math.min(elapsed / duration, 1);
    const eased = easeInOutQuad(progress);
    const position = start + change * eased;
    
    if (element instanceof Window) {
      window.scrollTo(0, position);
    } else {
      element.scrollTop = position;
    }
    
    if (progress < 1) {
      requestAnimationFrame(animateScroll);
    }
  }
  
  requestAnimationFrame(animateScroll);
}

/**
 * 检测元素是否在视口内
 */
export function useIntersectionObserver(
  target: Ref<HTMLElement | null>,
  callback: (isIntersecting: boolean) => void,
  options?: IntersectionObserverInit
) {
  let observer: IntersectionObserver | null = null;
  
  onMounted(() => {
    if (!target.value) return;
    
    observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          callback(entry.isIntersecting);
        });
      },
      options
    );
    
    observer.observe(target.value);
  });
  
  onUnmounted(() => {
    if (observer) {
      observer.disconnect();
    }
  });
}

/**
 * 复制到剪贴板
 */
export async function copyToClipboard(text: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    // 降级方案
    const textArea = document.createElement('textarea');
    textArea.value = text;
    textArea.style.position = 'fixed';
    textArea.style.left = '-999999px';
    document.body.appendChild(textArea);
    textArea.select();
    
    try {
      document.execCommand('copy');
      document.body.removeChild(textArea);
      return true;
    } catch {
      document.body.removeChild(textArea);
      return false;
    }
  }
}

/**
 * 格式化文件大小
 */
export function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B';
  
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  
  return Math.round((bytes / Math.pow(k, i)) * 100) / 100 + ' ' + sizes[i];
}

/**
 * 颜色转换工具
 */
export function hexToRgba(hex: string, alpha: number = 1): string {
  const r = parseInt(hex.slice(1, 3), 16);
  const g = parseInt(hex.slice(3, 5), 16);
  const b = parseInt(hex.slice(5, 7), 16);
  
  return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}
