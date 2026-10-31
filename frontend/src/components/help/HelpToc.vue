<template>
  <nav v-if="headings.length" class="help-toc" aria-label="页内目录">
    <h2 class="help-toc__title">本页目录</h2>
    <ul class="help-toc__list">
      <li
        v-for="heading in headings"
        :key="heading.id"
        :class="`help-toc__item help-toc__item--h${heading.depth}`"
      >
        <button type="button" class="help-toc__link" @click="emit('select', heading.id)">
          {{ heading.text }}
        </button>
      </li>
    </ul>
  </nav>
</template>

<script setup lang="ts">
  import type { ManualHeading } from '@/help/manual';

  defineProps<{ headings: ManualHeading[] }>();
  const emit = defineEmits<{ select: [id: string] }>();
</script>

<style scoped>
  .help-toc {
    padding: 16px 12px;
  }

  .help-toc__title {
    margin: 0 8px 8px;
    color: var(--ds-text-3);
    font-size: 12px;
    font-weight: 600;
    letter-spacing: 0.04em;
  }

  .help-toc__list {
    margin: 0;
    padding: 0;
    list-style: none;
  }

  .help-toc__item {
    margin: 1px 0;
  }

  .help-toc__item--h3 {
    padding-left: 10px;
  }

  .help-toc__link {
    display: block;
    width: 100%;
    padding: 5px 8px;
    border: 0;
    border-radius: 6px;
    background: transparent;
    color: var(--ds-text-2);
    font: inherit;
    font-size: 12px;
    line-height: 1.45;
    text-align: left;
    cursor: pointer;
  }

  .help-toc__link:hover,
  .help-toc__link:focus-visible {
    background: rgba(var(--v-theme-primary), 0.08);
    color: rgb(var(--v-theme-primary));
  }
</style>
