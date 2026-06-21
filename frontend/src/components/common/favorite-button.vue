<script lang="ts">
import { fetchDeleteFavorite, fetchGetFavorites, fetchSaveFavorite } from '@/service/api';

let favoritesCache: Api.User.FavoriteItem[] | null = null;
let favoritesPromise: Promise<Api.User.FavoriteItem[]> | null = null;
const listeners = new Set<() => void>();

async function loadFavoriteCache() {
  if (favoritesCache) return favoritesCache;
  if (!favoritesPromise) {
    favoritesPromise = fetchGetFavorites().then(({ data, error }) => {
      favoritesCache = error ? [] : data || [];
      return favoritesCache;
    });
  }
  return favoritesPromise;
}

function syncListeners() {
  listeners.forEach(listener => listener());
}
</script>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';

defineOptions({ name: 'FavoriteButton' });

const props = withDefaults(defineProps<{
  type: Api.User.FavoriteType;
  targetId?: string | number | null;
  title?: string | null;
  description?: string | null;
  meta?: string | null;
  size?: 'tiny' | 'small' | 'medium' | 'large';
  text?: boolean;
  circle?: boolean;
  showLabel?: boolean;
}>(), {
  size: 'tiny',
  text: true,
  circle: false,
  showLabel: false
});

const loading = ref(false);
const cacheVersion = ref(0);

const normalizedTargetId = computed(() => (props.targetId === undefined || props.targetId === null ? '' : String(props.targetId)));

const favorite = computed(() => {
  cacheVersion.value;
  return favoritesCache?.find(item => item.type === props.type && item.targetId === normalizedTargetId.value) || null;
});

const isFavorited = computed(() => Boolean(favorite.value));
const disabled = computed(() => !normalizedTargetId.value || !props.title);

function refreshLocalState() {
  cacheVersion.value += 1;
}

function normalizeText(value?: string | null) {
  return value?.trim() || '';
}

async function toggleFavorite() {
  if (disabled.value || loading.value) return;
  loading.value = true;
  try {
    if (favorite.value) {
      const deletingId = favorite.value.id;
      const { error } = await fetchDeleteFavorite(deletingId);
      if (error) return;
      favoritesCache = (favoritesCache || []).filter(item => item.id !== deletingId);
      window.$message?.success('已取消收藏');
    } else {
      const { data, error } = await fetchSaveFavorite({
        type: props.type,
        targetId: normalizedTargetId.value,
        title: normalizeText(props.title),
        description: normalizeText(props.description),
        meta: normalizeText(props.meta),
        starred: true
      });
      if (error || !data) return;
      favoritesCache = [
        ...(favoritesCache || []).filter(item => !(item.type === data.type && item.targetId === data.targetId)),
        data
      ];
      window.$message?.success('已添加收藏');
    }
    syncListeners();
  } finally {
    loading.value = false;
  }
}

onMounted(async () => {
  listeners.add(refreshLocalState);
  await loadFavoriteCache();
  refreshLocalState();
});

onBeforeUnmount(() => {
  listeners.delete(refreshLocalState);
});

watch(() => [props.type, normalizedTargetId.value], refreshLocalState);
</script>

<template>
  <NTooltip>
    <template #trigger>
      <NButton
        :text="text"
        :circle="circle"
        :size="size"
        :type="isFavorited ? 'warning' : 'default'"
        :loading="loading"
        :disabled="disabled"
        @click.stop="toggleFavorite"
      >
        <template #icon>
          <icon-carbon:star-filled v-if="isFavorited" class="text-yellow-400" />
          <icon-carbon:star v-else />
        </template>
        <span v-if="showLabel">{{ isFavorited ? '已收藏' : '收藏' }}</span>
      </NButton>
    </template>
    {{ isFavorited ? '取消收藏' : '收藏' }}
  </NTooltip>
</template>
