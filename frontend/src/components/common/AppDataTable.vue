<template>
  <component
    :is="bare ? 'div' : VCard"
    flat
    :class="[
      bare ? null : 'ds-card table-card ds-table-wrap',
      $attrs.class,
      {
        'ds-table-scroll': scrollable,
        'ds-table-sticky-first': stickyFirstColumn,
      },
    ]"
    :style="$attrs.style"
  >
    <div :class="bare ? 'app-table-bare' : 'ds-card__content'">
      <slot name="header">
        <div v-if="title || subtitle || $slots.toolbar" class="app-table-header">
          <div class="app-table-heading">
            <div v-if="title" class="app-table-title">{{ title }}</div>
            <div v-if="subtitle" class="app-table-subtitle">{{ subtitle }}</div>
          </div>
          <div v-if="$slots.toolbar" class="app-table-toolbar">
            <slot name="toolbar" />
          </div>
        </div>
      </slot>

      <v-alert
        v-if="error"
        type="error"
        variant="tonal"
        closable
        class="mb-4"
        data-testid="app-data-table-error"
        @click:close="$emit('update:error', '')"
      >
        {{ error }}
      </v-alert>

      <!-- Why: the table must stay mounted even on the first load. Server tables
           emit `update:options` from their own lifecycle; hiding the table behind
           a skeleton blocks that emission and deadlocks the page. -->
      <component :is="tableComponent" v-bind="tableBindings" data-testid="app-data-table">
        <template v-for="name in forwardedSlots" :key="name" #[name]="slotProps">
          <slot :name="name" v-bind="slotProps || {}" />
        </template>

        <template #no-data>
          <v-skeleton-loader
            v-if="loading && items.length === 0"
            type="table-row@6"
            class="ds-skeleton"
            data-testid="app-data-table-loading"
          />
          <slot v-else name="no-data">
            <slot name="empty">
              <div
                v-if="compactEmpty"
                class="app-table-empty-compact"
                data-testid="app-data-table-empty"
              >
                {{ emptyTitle }}
              </div>
              <EmptyState
                v-else
                :title="emptyTitle"
                :description="emptyDescription"
                :primary="emptyPrimary"
                data-testid="app-data-table-empty"
                @primary="$emit('empty-primary')"
              />
            </slot>
          </slot>
        </template>
      </component>
    </div>
  </component>
</template>

<script setup lang="ts">
  /**
   * Shared data-table surface.
   *
   * Why: the console had 29 hand-written v-data-table instances, each carrying
   * its own card, error alert, skeleton and empty state. Keeping those in one
   * component makes behaviour consistent and leaves the pages with columns and
   * cells only. v-data-table / -server / -virtual stay inside this module so a
   * future engine swap does not touch call sites.
   */
  import { computed, useAttrs, useSlots } from 'vue';
  // Dynamic <component :is> needs concrete component references; the Vuetify
  // auto-import plugin only injects static template tags.
  import { VCard, VDataTable, VDataTableServer, VDataTableVirtual } from 'vuetify/components';
  import EmptyState from '@/components/EmptyState.vue';

  defineOptions({ inheritAttrs: false });

  type Mode = 'client' | 'server' | 'virtual';
  type EmptyAction = { label: string; icon?: string; color?: string };

  const props = withDefaults(
    defineProps<{
      headers: any[];
      items: any[];
      mode?: Mode;
      loading?: boolean;
      error?: string;
      total?: number;
      density?: 'default' | 'comfortable' | 'compact';
      selectable?: boolean;
      scrollable?: boolean;
      stickyFirstColumn?: boolean;
      title?: string;
      subtitle?: string;
      emptyTitle?: string;
      emptyDescription?: string;
      emptyPrimary?: EmptyAction | null;
      compactEmpty?: boolean;
      /** Render without the card shell, for tables already inside a card/tab. */
      bare?: boolean;
    }>(),
    {
      mode: 'client',
      loading: false,
      error: '',
      total: 0,
      density: 'comfortable',
      selectable: false,
      scrollable: false,
      stickyFirstColumn: false,
      title: '',
      subtitle: '',
      emptyTitle: '暂无数据',
      emptyDescription: '当前没有可显示的数据。调整筛选条件后重试。',
      emptyPrimary: null,
      compactEmpty: false,
      bare: false,
    }
  );

  defineEmits<{ 'update:error': [value: string]; 'empty-primary': [] }>();

  const slots = useSlots();
  const attrs = useAttrs();
  const reservedSlots = new Set(['header', 'toolbar', 'empty', 'no-data']);

  // Layout classes/styles belong to the card; everything else is a table prop
  // or listener (update:options, click:row, v-model:selected, ...).
  const tableAttrs = computed(() => {
    const rest = { ...attrs };
    delete rest.class;
    delete rest.style;
    return rest;
  });

  const tableComponent = computed(() => {
    if (props.mode === 'server') return VDataTableServer;
    if (props.mode === 'virtual') return VDataTableVirtual;
    return VDataTable;
  });

  const forwardedSlots = computed(() =>
    Object.keys(slots).filter(name => !reservedSlots.has(name))
  );

  const tableProps = computed(() => ({
    headers: props.headers,
    items: props.items,
    loading: props.loading,
    density: props.density,
    showSelect: props.selectable,
    ...(props.mode === 'server' ? { itemsLength: props.total } : {}),
  }));

  const tableBindings = computed(() => ({ ...tableAttrs.value, ...tableProps.value }));
</script>

<style scoped>
  .app-table-header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 16px;
    margin-bottom: 14px;
  }

  .app-table-heading {
    min-width: 0;
  }

  .app-table-title {
    font-size: var(--ds-fs-title);
    font-weight: 600;
    color: rgb(var(--v-theme-on-surface));
  }

  .app-table-subtitle {
    margin-top: 4px;
    font-size: var(--ds-fs-caption);
    line-height: 1.5;
    color: var(--ds-text-2);
  }

  .app-table-toolbar {
    display: flex;
    align-items: center;
    gap: 8px;
    flex: none;
  }

  .app-table-empty-compact {
    padding: 24px 8px;
    text-align: center;
    font-size: var(--ds-fs-body);
    color: var(--ds-text-3);
  }
</style>
