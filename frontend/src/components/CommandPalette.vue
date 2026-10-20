<template>
  <v-dialog
    v-model="open"
    class="ds-dialog-overlay palette-dialog"
    max-width="620"
    :retain-focus="false"
    @after-enter="focusInput"
  >
    <v-card class="palette">
      <div class="palette-input">
        <v-icon size="18">mdi-magnify</v-icon>
        <input
          ref="inputRef"
          v-model="keyword"
          type="text"
          class="palette-field"
          placeholder="搜索页面、采集表，或输入操作…"
          aria-label="命令面板搜索"
          @keydown.down.prevent="move(1)"
          @keydown.up.prevent="move(-1)"
          @keydown.enter.prevent="runActive"
        />
        <kbd class="palette-kbd">Esc</kbd>
      </div>

      <v-divider />

      <div class="palette-list" role="listbox">
        <template v-for="group in grouped" :key="group.name">
          <div class="palette-group">{{ group.name }}</div>
          <button
            v-for="item in group.items"
            :key="item.id"
            type="button"
            class="palette-item"
            :class="{ 'palette-item--active': item.index === active }"
            role="option"
            :aria-selected="item.index === active"
            @mouseenter="active = item.index"
            @click="run(item)"
          >
            <v-icon size="16" class="palette-icon">{{ item.icon }}</v-icon>
            <span class="palette-label">{{ item.label }}</span>
            <span v-if="item.hint" class="palette-hint">{{ item.hint }}</span>
          </button>
        </template>
        <div v-if="!items.length" class="palette-empty">
          {{ keyword ? '没有匹配的结果' : '输入关键字以搜索' }}
        </div>
      </div>

      <div class="palette-footer">
        <span>↑↓ 选择</span>
        <span>Enter 打开</span>
        <span>Esc 关闭</span>
        <span class="palette-shortcut">Ctrl / ⌘ + K</span>
      </div>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
  import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
  import { useRouter } from 'vue-router';
  import tableService from '@/service/table-service';
  import { useAppTheme } from '@/composables/useAppTheme';
  import { commandPaletteOpen, toggleCommandPalette } from '@/stores/command-palette';

  interface PaletteItem {
    id: string;
    group: string;
    label: string;
    hint?: string;
    icon: string;
    index: number;
    run: () => void;
  }

  const open = commandPaletteOpen;
  const keyword = ref('');
  const active = ref(0);
  const inputRef = ref<HTMLInputElement | null>(null);
  const tableResults = ref<{ id: number; label: string; hint: string }[]>([]);

  const router = useRouter();
  const { toggleTheme } = useAppTheme();

  const navItems: PaletteItem[] = [];
  const push = (label: string, path: string, icon: string) =>
    navItems.push({
      id: `nav-${path}`,
      group: '跳转',
      label,
      icon,
      index: 0,
      run: () => router.push(path),
    });

  push('首页', '/', 'mdi-home');
  push('采集源管理', '/source', 'mdi-database-cog');
  push('目标端管理', '/target', 'mdi-database-export');
  push('采集表配置', '/table', 'mdi-table');
  push('采集任务', '/task', 'mdi-calendar-clock');
  push('监控与告警', '/monitor', 'mdi-monitor-dashboard');
  push('数据洞察', '/data-insight', 'mdi-chart-box-outline');
  push('日志管理', '/logs', 'mdi-file-document-outline');
  push('字典维护', '/dict', 'mdi-book-open-page-variant');
  push('系统配置', '/sys-settings', 'mdi-cog-outline');
  push('集群状态', '/cluster', 'mdi-server-network');

  const actions: PaletteItem[] = [
    {
      id: 'action-source',
      group: '操作',
      label: '新增采集源',
      icon: 'mdi-plus-circle-outline',
      index: 0,
      run: () => router.push({ path: '/source', query: { action: 'create' } }),
    },
    {
      id: 'action-target',
      group: '操作',
      label: '新增目标端',
      icon: 'mdi-plus-circle-outline',
      index: 0,
      run: () => router.push({ path: '/target', query: { action: 'create' } }),
    },
    {
      id: 'action-table',
      group: '操作',
      label: '新增采集表',
      icon: 'mdi-table-plus',
      index: 0,
      run: () => router.push('/table/batch-add'),
    },
    {
      id: 'action-theme',
      group: '操作',
      label: '切换浅色 / 深色主题',
      icon: 'mdi-theme-light-dark',
      index: 0,
      run: () => toggleTheme(),
    },
  ];

  const items = computed<PaletteItem[]>(() => {
    const q = keyword.value.trim().toLowerCase();
    const match = (item: PaletteItem) => !q || item.label.toLowerCase().includes(q);
    const base = [...navItems.filter(match), ...actions.filter(match)];
    const tables = tableResults.value.map((row, i) => ({
      id: `table-${row.id}`,
      group: '采集表',
      label: row.label,
      hint: row.hint,
      icon: 'mdi-table-arrow-right',
      index: 0,
      run: () =>
        router.push({
          path: `/table/detail/${row.id}`,
          query: { tab: 'info', tblname: row.label },
        }),
    }));
    const list = [...base, ...tables];
    return list.map((item, index) => ({ ...item, index }));
  });

  const grouped = computed(() => {
    const groups: { name: string; items: PaletteItem[] }[] = [];
    for (const item of items.value) {
      const last = groups[groups.length - 1];
      if (last && last.name === item.group) last.items.push(item);
      else groups.push({ name: item.group, items: [item] });
    }
    return groups;
  });

  let timer: ReturnType<typeof setTimeout> | null = null;
  watch(keyword, value => {
    active.value = 0;
    if (timer) clearTimeout(timer);
    const q = value.trim();
    if (q.length < 1) {
      tableResults.value = [];
      return;
    }
    timer = setTimeout(async () => {
      try {
        const page = await tableService.fetchTableList(0, 6, q);
        tableResults.value = (page.content || []).map(row => ({
          id: row.id,
          label: `${row.sourceDb ? row.sourceDb + '.' : ''}${row.sourceTable || row.id}`,
          hint: row.name || '',
        }));
      } catch {
        tableResults.value = [];
      }
    }, 250);
  });

  const focusInput = () => {
    nextTick(() => inputRef.value?.focus());
  };

  const move = (delta: number) => {
    if (!items.value.length) return;
    const next = (active.value + delta + items.value.length) % items.value.length;
    active.value = next;
  };

  const run = (item: PaletteItem) => {
    open.value = false;
    item.run();
  };

  const runActive = () => {
    const item = items.value[active.value];
    if (item) run(item);
  };

  const onKeydown = (event: KeyboardEvent) => {
    if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
      event.preventDefault();
      toggleCommandPalette();
    }
  };

  watch(open, value => {
    if (value) {
      keyword.value = '';
      tableResults.value = [];
      active.value = 0;
      focusInput();
    }
  });

  onMounted(() => window.addEventListener('keydown', onKeydown));
  onUnmounted(() => window.removeEventListener('keydown', onKeydown));
</script>

<style scoped>
  .palette {
    overflow: hidden;
    border-radius: 12px;
  }

  .palette-input {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 14px 16px;
  }

  .palette-field {
    flex: 1;
    border: none;
    outline: none;
    background: transparent;
    color: rgb(var(--v-theme-on-surface));
    font-size: 15px;
  }

  .palette-kbd {
    padding: 2px 6px;
    border: 1px solid var(--ds-border-default);
    border-radius: 4px;
    font-size: 11px;
    color: var(--ds-text-2);
  }

  .palette-list {
    max-height: 380px;
    overflow-y: auto;
    padding: 6px;
  }

  .palette-group {
    padding: 8px 10px 4px;
    font-size: 11px;
    letter-spacing: 0.4px;
    color: var(--ds-text-3);
  }

  .palette-item {
    display: flex;
    align-items: center;
    gap: 10px;
    width: 100%;
    padding: 9px 10px;
    border: none;
    border-radius: 8px;
    background: transparent;
    color: rgb(var(--v-theme-on-surface));
    font-size: 14px;
    text-align: left;
    cursor: pointer;
  }

  .palette-item--active {
    background: rgba(var(--v-theme-primary), 0.1);
  }

  .palette-icon {
    color: var(--ds-text-2);
  }

  .palette-label {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .palette-hint {
    font-size: 12px;
    color: var(--ds-text-2);
  }

  .palette-empty {
    padding: 28px 0;
    text-align: center;
    font-size: 13px;
    color: var(--ds-text-2);
  }

  .palette-footer {
    display: flex;
    gap: 14px;
    padding: 8px 16px;
    border-top: 1px solid var(--ds-border-subtle);
    font-size: 11px;
    color: var(--ds-text-3);
  }

  .palette-shortcut {
    margin-left: auto;
  }
</style>
