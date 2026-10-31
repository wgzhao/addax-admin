<script setup lang="ts">
  import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch, watchEffect } from 'vue';
  import { useRoute, useRouter } from 'vue-router';
  import PageHeader from '@/components/PageHeader.vue';
  import HelpNav from '@/components/help/HelpNav.vue';
  import HelpToc from '@/components/help/HelpToc.vue';
  import {
    chapterMap,
    defaultChapterSlug,
    manualGroups,
    searchManual,
    type ManualSearchResult,
  } from '@/help/manual';
  import { renderMarkdown } from '@/help/markdown';

  const route = useRoute();
  const router = useRouter();
  const searchQuery = ref('');
  const appVersion = import.meta.env.VITE_APP_VERSION || 'dev';
  const initialTitle = document.title;

  const currentSlug = computed(() => {
    const raw = route.query.chapter;
    return typeof raw === 'string' && chapterMap.has(raw) ? raw : defaultChapterSlug;
  });

  const currentChapter = computed(() => chapterMap.get(currentSlug.value));
  const renderedHtml = computed(() =>
    currentChapter.value
      ? renderMarkdown(currentChapter.value.content, currentChapter.value.slug)
      : ''
  );
  const searchResults = computed(() => searchManual(searchQuery.value));
  const mobileChapters = computed(() =>
    manualGroups.flatMap(group =>
      group.chapters.map(chapter => ({
        title: `${group.label} / ${chapter.title}`,
        value: chapter.slug,
      }))
    )
  );

  function scrollToHeading(id: string) {
    nextTick(() => {
      requestAnimationFrame(() => {
        document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
      });
    });
  }

  function navigateTo(payload: { slug: string; hash?: string }) {
    const hash = payload.hash ? `#${encodeURIComponent(payload.hash)}` : undefined;
    const query = { ...route.query, chapter: payload.slug };
    if (payload.slug === currentSlug.value && !payload.hash) {
      window.scrollTo({ top: 0, behavior: 'auto' });
      return;
    }
    if (payload.slug === currentSlug.value && payload.hash) {
      if (route.hash !== hash) {
        router.replace({ path: '/help', query, hash }).catch(() => scrollToHeading(payload.hash!));
      } else {
        scrollToHeading(payload.hash);
      }
      return;
    }
    router.push({ path: '/help', query, hash }).catch(() => {
      if (payload.hash) scrollToHeading(payload.hash);
    });
  }

  function onContentClick(event: MouseEvent) {
    const target = event.target as HTMLElement | null;
    const anchor = target?.closest('a[data-manual-link]') as HTMLAnchorElement | null;
    if (!anchor) return;
    event.preventDefault();
    const slug = anchor.dataset.chapter;
    if (!slug) return;
    navigateTo({ slug, hash: anchor.dataset.hash || undefined });
  }

  function onTocSelect(id: string) {
    const hash = `#${encodeURIComponent(id)}`;
    if (route.hash === hash) {
      scrollToHeading(id);
      return;
    }
    router
      .replace({ path: '/help', query: { ...route.query, chapter: currentSlug.value }, hash })
      .catch(() => scrollToHeading(id));
  }

  function onMobileSelect(slug: string) {
    if (slug) navigateTo({ slug });
  }

  function onSearchSelect(payload: ManualSearchResult | { slug: string; hash?: string }) {
    navigateTo({ slug: payload.slug, hash: payload.hash });
  }

  watch(
    () => [currentSlug.value, route.hash],
    () => {
      if (route.hash) {
        scrollToHeading(decodeURIComponent(route.hash.slice(1)));
      } else {
        window.scrollTo({ top: 0, behavior: 'auto' });
      }
    },
    { immediate: true }
  );

  watchEffect(() => {
    document.title = currentChapter.value
      ? `${currentChapter.value.title} - 帮助中心`
      : '帮助中心 - Addax Admin';
  });

  onMounted(() => {
    const raw = route.query.chapter;
    const valid = typeof raw === 'string' && chapterMap.has(raw);
    if (!valid && defaultChapterSlug) {
      router.replace({ path: '/help', query: { ...route.query, chapter: defaultChapterSlug } });
    }
  });

  onBeforeUnmount(() => {
    document.title = initialTitle;
  });
</script>

<template>
  <div class="page-shell help-page">
    <PageHeader title="帮助中心" subtitle="使用与运维手册；安装部署请参考项目的安装手册。">
      <v-chip size="small" variant="tonal">v{{ appVersion }}</v-chip>
    </PageHeader>

    <div class="help-layout ds-card">
      <aside class="help-sidebar">
        <HelpNav
          v-model:query="searchQuery"
          :groups="manualGroups"
          :current-slug="currentSlug"
          :results="searchResults"
          @select="onSearchSelect"
        />
      </aside>

      <main class="help-main">
        <div class="help-mobile-nav">
          <v-autocomplete
            :model-value="currentSlug"
            :items="mobileChapters"
            item-title="title"
            item-value="value"
            label="跳转章节"
            prepend-inner-icon="mdi-book-search-outline"
            variant="outlined"
            density="compact"
            hide-details
            clearable
            @update:model-value="onMobileSelect"
          />
        </div>

        <article v-if="currentChapter" class="help-content" @click="onContentClick">
          <div class="help-markdown" v-html="renderedHtml" />
        </article>
        <v-alert v-else type="info" variant="tonal">没有可显示的手册章节。</v-alert>
      </main>

      <aside class="help-rail">
        <HelpToc :headings="currentChapter?.headings || []" @select="onTocSelect" />
      </aside>
    </div>
  </div>
</template>

<style scoped>
  .help-layout {
    display: grid;
    grid-template-columns: 240px minmax(0, 1fr) 200px;
    min-height: 62vh;
    overflow: hidden;
  }

  .help-sidebar {
    min-width: 0;
    border-right: 1px solid var(--ds-border-subtle);
    background: rgba(var(--v-theme-surface), 0.45);
  }

  .help-sidebar :deep(.help-nav) {
    position: sticky;
    top: 16px;
    max-height: calc(100vh - 120px);
    overflow-y: auto;
  }

  .help-main {
    min-width: 0;
    padding: 26px 34px 56px;
  }

  .help-rail {
    min-width: 0;
    border-left: 1px solid var(--ds-border-subtle);
  }

  .help-rail :deep(.help-toc) {
    position: sticky;
    top: 16px;
    max-height: calc(100vh - 120px);
    overflow-y: auto;
  }

  .help-mobile-nav {
    display: none;
    margin-bottom: 16px;
  }

  .help-content {
    max-width: 880px;
    margin: 0 auto;
  }

  .help-markdown {
    color: rgb(var(--v-theme-on-surface));
    font-size: 14px;
    line-height: 1.8;
  }

  .help-markdown :deep(h1) {
    margin: 0 0 18px;
    font-size: 1.5rem;
    font-weight: 600;
    line-height: 1.35;
  }

  .help-markdown :deep(h2) {
    margin: 30px 0 12px;
    padding-bottom: 7px;
    border-bottom: 1px solid var(--ds-border-subtle);
    font-size: 1.12rem;
    font-weight: 600;
    line-height: 1.4;
    scroll-margin-top: 18px;
  }

  .help-markdown :deep(h3) {
    margin: 22px 0 8px;
    font-size: 1rem;
    font-weight: 600;
    line-height: 1.45;
    scroll-margin-top: 18px;
  }

  .help-markdown :deep(p),
  .help-markdown :deep(li) {
    color: var(--ds-text-2);
  }

  .help-markdown :deep(p) {
    margin: 10px 0;
  }

  .help-markdown :deep(ul),
  .help-markdown :deep(ol) {
    margin: 10px 0;
    padding-left: 22px;
  }

  .help-markdown :deep(li + li) {
    margin-top: 4px;
  }

  .help-markdown :deep(a) {
    color: rgb(var(--v-theme-primary));
    text-decoration: none;
  }

  .help-markdown :deep(a:hover) {
    text-decoration: underline;
  }

  .help-markdown :deep(strong) {
    color: rgb(var(--v-theme-on-surface));
    font-weight: 600;
  }

  .help-markdown :deep(code) {
    padding: 1px 5px;
    border-radius: 5px;
    background: rgba(var(--v-theme-primary), 0.08);
    color: rgb(var(--v-theme-primary));
    font-family: 'SF Mono', 'Monaco', 'Inconsolata', 'Roboto Mono', 'Consolas', monospace;
    font-size: 0.9em;
  }

  .help-markdown :deep(pre) {
    margin: 14px 0;
    padding: 14px 16px;
    border: 1px solid var(--ds-border-subtle);
    border-radius: 10px;
    background: rgba(var(--v-theme-on-surface), 0.045);
    overflow-x: auto;
  }

  .help-markdown :deep(pre code) {
    padding: 0;
    background: transparent;
    color: rgb(var(--v-theme-on-surface));
    font-size: 13px;
    line-height: 1.65;
    white-space: pre;
  }

  .help-markdown :deep(blockquote) {
    margin: 14px 0;
    padding: 10px 16px;
    border-left: 3px solid rgb(var(--v-theme-primary));
    background: rgba(var(--v-theme-primary), 0.05);
    color: var(--ds-text-2);
  }

  .help-markdown :deep(blockquote p) {
    margin: 0;
  }

  .help-markdown :deep(table) {
    display: block;
    width: 100%;
    margin: 14px 0;
    border-collapse: collapse;
    overflow-x: auto;
    font-size: 13px;
  }

  .help-markdown :deep(th),
  .help-markdown :deep(td) {
    padding: 8px 10px;
    border: 1px solid var(--ds-border-subtle);
    text-align: left;
    vertical-align: top;
    white-space: nowrap;
  }

  .help-markdown :deep(th) {
    background: rgba(var(--v-theme-on-surface), 0.04);
    color: rgb(var(--v-theme-on-surface));
    font-weight: 600;
  }

  .help-markdown :deep(hr) {
    margin: 28px 0;
    border: 0;
    border-top: 1px solid var(--ds-border-subtle);
  }

  @media (max-width: 1180px) {
    .help-layout {
      grid-template-columns: 230px minmax(0, 1fr);
    }

    .help-rail {
      display: none;
    }
  }

  @media (max-width: 900px) {
    .help-layout {
      grid-template-columns: minmax(0, 1fr);
    }

    .help-sidebar {
      display: none;
    }

    .help-main {
      padding: 18px 16px 40px;
    }

    .help-mobile-nav {
      display: block;
    }

    .help-markdown :deep(th),
    .help-markdown :deep(td) {
      white-space: normal;
    }
  }
</style>

<route lang="json">
{
  "meta": {
    "title": "帮助中心",
    "icon": "mdi-book-open-variant",
    "requiresAuth": true,
    "navHidden": true
  }
}
</route>
