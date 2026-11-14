<template>
  <v-menu
    v-model="open"
    :close-on-content-click="false"
    location="bottom end"
    offset="8"
    max-width="420"
    min-width="360"
  >
    <template #activator="{ props }">
      <v-btn
        v-bind="props"
        icon
        class="notification-trigger"
        :aria-label="unreadCount > 0 ? `通知，${unreadCount} 条未读` : '通知'"
        title="通知"
      >
        <v-badge
          :content="badgeText"
          :model-value="unreadCount > 0"
          color="primary"
          offset-x="6"
          offset-y="6"
        >
          <v-icon>mdi-bell-outline</v-icon>
        </v-badge>
      </v-btn>
    </template>

    <v-card class="notification-card" elevation="8">
      <div class="notification-head">
        <div>
          <div class="notification-title">通知</div>
          <div class="notification-sub">
            {{ unreadCount > 0 ? `${unreadCount} 条未读` : '暂无未读通知' }}
            <span v-if="onlyAlerts" class="notification-filter-hint">· 仅显示告警</span>
          </div>
        </div>
        <div class="notification-actions">
          <v-btn
            size="small"
            variant="text"
            class="notification-action"
            :class="{ 'notification-action--on': onlyAlerts }"
            @click="onlyAlerts = !onlyAlerts"
          >
            {{ onlyAlerts ? '仅告警' : '全部' }}
          </v-btn>
          <v-btn
            v-if="unreadCount > 0"
            size="small"
            variant="text"
            class="notification-action"
            :loading="markingAll"
            @click="handleMarkAll"
          >
            全部已读
          </v-btn>
        </div>
      </div>
      <v-divider />
      <div v-if="loading" class="notification-state">
        <v-progress-circular indeterminate size="20" width="2" />
        <span>加载中…</span>
      </div>
      <div v-else-if="error" class="notification-state notification-state--error">
        <v-icon size="18">mdi-alert-circle-outline</v-icon>
        <span>{{ error }}</span>
        <v-btn size="small" variant="text" @click="refresh">重试</v-btn>
      </div>
      <div v-else-if="visibleNotifications.length === 0" class="notification-state">
        <v-icon size="18">mdi-bell-check-outline</v-icon>
        <span>{{ onlyAlerts ? '暂无告警通知' : '暂无通知' }}</span>
        <v-btn
          v-if="onlyAlerts && notifications.length > 0"
          size="small"
          variant="text"
          @click="onlyAlerts = false"
        >
          查看全部
        </v-btn>
      </div>
      <v-list v-else density="comfortable" class="notification-list">
        <v-list-item
          v-for="item in visibleNotifications"
          :key="item.id"
          class="notification-item"
          :class="{ 'notification-item--unread': item.status === 'UNREAD' }"
          @click="handleOpen(item)"
        >
          <template #prepend>
            <div class="notification-icon" :class="`notification-icon--${tone(item)}`">
              <v-icon size="18">{{ iconFor(item) }}</v-icon>
            </div>
          </template>
          <v-list-item-title class="notification-item-title">
            <span class="notification-item-title-text">{{ item.title || '系统通知' }}</span>
            <span v-if="relativeTime(item.createdAt)" class="notification-item-time">
              {{ relativeTime(item.createdAt) }}
            </span>
          </v-list-item-title>
          <v-list-item-subtitle
            v-if="item.content"
            class="notification-item-content"
            :title="item.content"
          >
            {{ item.content }}
          </v-list-item-subtitle>
          <template #append>
            <span v-if="item.status === 'UNREAD'" class="notification-dot" aria-label="未读" />
          </template>
        </v-list-item>
      </v-list>
    </v-card>
  </v-menu>
</template>

<script setup lang="ts">
  import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
  import { useRouter } from 'vue-router';
  import notificationCenter from '@/stores/notification-center';
  import type { UserNotification } from '@/service/notification-service';

  const router = useRouter();
  const open = ref(false);
  const loading = ref(false);
  const markingAll = ref(false);
  const error = ref<string | null>(null);
  const onlyAlerts = ref(true);

  const notifications = computed(() => notificationCenter.notifications.value);
  const unreadCount = computed(() => notificationCenter.unreadCount.value);
  const badgeText = computed(() => (unreadCount.value > 99 ? '99+' : String(unreadCount.value)));

  const tone = (item: UserNotification) => {
    const type = (item.type || '').toUpperCase();
    if (type.includes('ERROR') || type.includes('FAIL')) return 'error';
    if (type.includes('WARN')) return 'warning';
    if (type.includes('SUCCESS')) return 'success';
    // Why: the backend does not always set `type`; infer from the copy so a
    // completed run does not look like an informational message. "失败 0 个"
    // is a successful batch, so read the count instead of the word.
    const text = `${item.title || ''} ${item.content || ''}`;
    const failureCount = text.match(/失败\s*(\d+)/);
    if (failureCount) return Number(failureCount[1]) > 0 ? 'error' : 'success';
    if (text.includes('失败') || text.includes('错误')) return 'error';
    if (text.includes('完成') || text.includes('成功')) return 'success';
    return 'info';
  };

  const iconFor = (item: UserNotification) => {
    const toneValue = tone(item);
    if (toneValue === 'error') return 'mdi-alert-circle-outline';
    if (toneValue === 'warning') return 'mdi-alert-outline';
    if (toneValue === 'success') return 'mdi-check-circle-outline';
    return 'mdi-information-outline';
  };

  // The bell is an exception inbox, not a per-run activity feed. Success
  // messages remain available via "全部" but do not compete with failures.
  const visibleNotifications = computed(() =>
    onlyAlerts.value
      ? notifications.value.filter(item => tone(item) !== 'success')
      : notifications.value
  );

  const refresh = async () => {
    loading.value = true;
    error.value = null;
    try {
      await Promise.all([
        notificationCenter.refreshList('ALL', 20),
        notificationCenter.refreshUnreadCount(),
      ]);
    } catch (err) {
      error.value = err instanceof Error ? err.message : String(err);
    } finally {
      loading.value = false;
    }
  };

  const handleMarkAll = async () => {
    markingAll.value = true;
    try {
      await notificationCenter.markAllRead();
    } finally {
      markingAll.value = false;
    }
  };

  const relativeTime = (value?: string): string => {
    if (!value) return '';
    const ts = new Date(value).getTime();
    if (Number.isNaN(ts)) return '';
    const diff = Date.now() - ts;
    if (diff < 60_000) return '刚刚';
    if (diff < 3_600_000) return `${Math.floor(diff / 60_000)} 分钟前`;
    if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)} 小时前`;
    if (diff < 7 * 86_400_000) return `${Math.floor(diff / 86_400_000)} 天前`;
    return new Date(ts).toLocaleDateString();
  };

  // Collection notifications carry the table id in refId ("tid"), so the bell can lead
  // straight to the run log that holds the full stack trace behind the short reason.
  const targetFor = (item: UserNotification): string | null => {
    if (!item.refId) return null;
    const type = (item.refType || '').toUpperCase();
    if (
      type.includes('TID') ||
      type.includes('TABLE') ||
      type.includes('TASK') ||
      type.includes('EXECUTION')
    ) {
      return `/table/detail/${item.refId}?tab=logs`;
    }
    return null;
  };

  const handleOpen = async (item: UserNotification) => {
    if (item.status === 'UNREAD') {
      try {
        await notificationCenter.markRead(item.id);
      } catch {
        // Reading a notification must never block navigation.
      }
    }
    const target = targetFor(item);
    if (target) {
      open.value = false;
      router.push(target);
    }
  };

  let timer: number | undefined;
  onMounted(async () => {
    try {
      await notificationCenter.refreshUnreadCount();
    } catch {
      // The bell must stay usable when the endpoint is unavailable.
    }
    timer = window.setInterval(() => {
      void notificationCenter.refreshUnreadCount().catch(() => undefined);
    }, 60_000);
  });

  onBeforeUnmount(() => {
    if (timer) window.clearInterval(timer);
  });

  watch(open, value => {
    if (value) void refresh();
  });
</script>

<style scoped>
  .notification-trigger {
    margin-inline-end: 2px;
  }

  .notification-card {
    overflow: hidden;
    border: 1px solid var(--ds-border-default);
    border-radius: 12px;
  }

  .notification-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding: 12px 14px 10px;
  }

  .notification-title {
    font-size: 15px;
    font-weight: 600;
    color: rgb(var(--v-theme-on-surface));
  }

  .notification-sub {
    margin-top: 2px;
    font-size: 12px;
    color: var(--ds-text-3);
  }

  .notification-actions {
    display: inline-flex;
    align-items: center;
    justify-content: flex-end;
    flex-wrap: wrap;
    gap: 2px;
  }

  .notification-action {
    letter-spacing: 0;
    font-size: 12px;
  }

  .notification-action--on {
    color: rgb(var(--v-theme-primary));
    font-weight: 600;
  }

  .notification-filter-hint {
    margin-inline-start: 4px;
    color: var(--ds-text-3);
  }

  .notification-state {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    min-height: 96px;
    padding: 16px;
    font-size: 13px;
    color: var(--ds-text-2);
  }

  .notification-state--error {
    color: rgb(var(--v-theme-error));
  }

  .notification-list {
    max-height: 420px;
    overflow-y: auto;
    padding: 4px;
  }

  .notification-item {
    border-radius: 8px;
    cursor: pointer;
    padding-block: 8px;
  }

  .notification-item--unread {
    background: rgba(var(--v-theme-primary), 0.06);
  }

  .notification-icon {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 30px;
    height: 30px;
    border-radius: 8px;
    margin-inline-end: 4px;
    background: rgba(var(--v-theme-primary), 0.1);
    color: rgb(var(--v-theme-primary));
  }

  .notification-icon--error {
    background: rgba(var(--v-theme-error), 0.12);
    color: rgb(var(--v-theme-error));
  }

  .notification-icon--warning {
    background: rgba(var(--v-theme-warning), 0.14);
    color: rgb(var(--v-theme-warning));
  }

  .notification-icon--success {
    background: rgba(var(--v-theme-success), 0.12);
    color: rgb(var(--v-theme-success));
  }

  .notification-item-title {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    gap: 8px;
    font-size: 13px;
    font-weight: 600;
    line-height: 1.4;
  }

  .notification-item-title-text {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .notification-item-time {
    flex: 0 0 auto;
    font-size: 11px;
    font-weight: 400;
    color: var(--ds-text-3);
  }

  .notification-item-content {
    margin-top: 2px;
    font-size: 12px;
    line-height: 1.45;
    white-space: normal;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }

  .notification-dot {
    width: 7px;
    height: 7px;
    border-radius: 50%;
    background: rgb(var(--v-theme-primary));
  }
</style>
