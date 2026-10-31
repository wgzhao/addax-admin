<template>
  <nav class="help-nav" aria-label="帮助中心目录">
    <v-text-field
      v-model="search"
      class="help-nav__search"
      prepend-inner-icon="mdi-magnify"
      placeholder="搜索章节或关键词"
      aria-label="搜索帮助内容"
      variant="outlined"
      density="compact"
      hide-details
      clearable
    />

    <template v-if="query">
      <p class="help-nav__hint">
        {{ results.length ? `找到 ${results.length} 个结果` : '没有匹配的章节' }}
      </p>
      <v-list v-if="results.length" density="compact" nav class="help-nav__list">
        <v-list-item
          v-for="(result, index) in results"
          :key="`${result.slug}-${result.kind}-${result.hash || ''}-${index}`"
          :active="result.slug === currentSlug"
          class="help-nav__result"
          @click="select(result)"
        >
          <template #prepend>
            <v-icon size="16">
              {{ result.kind === 'chapter' ? 'mdi-file-document-outline' : 'mdi-format-header-2' }}
            </v-icon>
          </template>
          <v-list-item-title>{{ result.title }}</v-list-item-title>
          <v-list-item-subtitle v-if="result.context">{{ result.context }}</v-list-item-subtitle>
        </v-list-item>
      </v-list>
    </template>

    <template v-else>
      <section v-for="group in groups" :key="group.key" class="help-nav__group">
        <h2 class="help-nav__group-title">{{ group.label }}</h2>
        <v-list density="compact" nav class="help-nav__list">
          <v-list-item
            v-for="chapter in group.chapters"
            :key="chapter.slug"
            :active="chapter.slug === currentSlug"
            class="help-nav__chapter"
            @click="emit('select', { slug: chapter.slug })"
          >
            <v-list-item-title>{{ chapter.title }}</v-list-item-title>
          </v-list-item>
        </v-list>
      </section>
    </template>
  </nav>
</template>

<script setup lang="ts">
  import { computed } from 'vue';
  import type { ManualGroup, ManualSearchResult } from '@/help/manual';

  const props = defineProps<{
    groups: ManualGroup[];
    currentSlug: string;
    query: string;
    results: ManualSearchResult[];
  }>();

  const emit = defineEmits<{
    'update:query': [value: string];
    select: [payload: { slug: string; hash?: string }];
  }>();

  const search = computed({
    get: () => props.query,
    set: value => emit('update:query', value || ''),
  });

  const select = (result: ManualSearchResult) => {
    emit('select', { slug: result.slug, hash: result.hash });
    emit('update:query', '');
  };
</script>

<style scoped>
  .help-nav {
    display: flex;
    flex-direction: column;
    gap: 10px;
    padding: 14px 12px;
  }

  .help-nav__search {
    flex: 0 0 auto;
  }

  .help-nav__hint {
    margin: 4px 8px 0;
    color: var(--ds-text-3);
    font-size: 12px;
  }

  .help-nav__group-title {
    margin: 12px 8px 4px;
    color: var(--ds-text-3);
    font-size: 12px;
    font-weight: 600;
    letter-spacing: 0.04em;
  }

  .help-nav__list {
    background: transparent;
  }

  .help-nav__chapter,
  .help-nav__result {
    border-radius: 8px;
    min-height: 38px;
  }

  .help-nav__chapter.v-list-item--active,
  .help-nav__result.v-list-item--active {
    background: rgba(var(--v-theme-primary), 0.1);
    color: rgb(var(--v-theme-primary));
    font-weight: 600;
  }

  .help-nav__result :deep(.v-list-item-subtitle) {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
</style>
