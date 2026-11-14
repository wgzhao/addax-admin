<template>
  <div class="calendars-page page-shell ds-management-page">
    <PageHeader title="采集日历" subtitle="维护交易日历与工作日历的日期集合，控制源端可以采集的日期。" />

    <v-alert
      v-for="warning in coverageWarnings"
      :key="warning"
      type="warning"
      variant="tonal"
      density="comfortable"
      class="mb-2"
      closable
    >
      {{ warning }}
    </v-alert>

    <v-card flat class="ds-card">
      <v-card-text class="section-body">
        <div class="section-header">
          <div>
            <div class="section-title">日历日期</div>
            <div class="section-subtitle">
              交易日历决定业务日期，工作日历决定采集窗口。缺日会让使用该日历的源静默停采。
            </div>
          </div>
        </div>

        <v-btn-toggle v-model="selectedCode" mandatory color="primary" variant="outlined" divided>
          <v-btn v-for="cal in calendars" :key="cal.code" :value="cal.code" class="calendar-tab">
            {{ cal.name }}
            <span class="tab-code">（{{ cal.code }}）</span>
          </v-btn>
        </v-btn-toggle>

        <div class="toolbar">
          <v-text-field
            v-model.number="year"
            label="年份"
            type="number"
            variant="outlined"
            density="compact"
            hide-details
            class="year-field"
            :rules="[yearRule]"
            @update:model-value="loadDates"
          />
          <span class="date-count">{{ dates.length }} 天</span>
          <v-spacer />
          <v-btn
            color="secondary"
            variant="tonal"
            size="small"
            prepend-icon="mdi-calendar-plus"
            @click="openBatchDialog"
          >
            批量生成
          </v-btn>
          <v-btn
            v-if="selectedCode === 1021"
            color="secondary"
            variant="tonal"
            size="small"
            prepend-icon="mdi-calendar-refresh"
            @click="openTradeDialog"
          >
            年度补全交易日历
          </v-btn>
          <v-btn
            color="primary"
            variant="flat"
            size="small"
            prepend-icon="mdi-plus"
            @click="openAddDateDialog"
          >
            新增日期
          </v-btn>
        </div>

        <v-skeleton-loader v-if="loading" type="table-row@6" class="ds-skeleton" />
        <template v-else-if="dates.length === 0">
          <EmptyState
            title="该年份暂无日期"
            description="可点击「批量生成」按年写入，或「新增日期」手工补单日。"
            :primary="{ label: '批量生成', icon: 'mdi-calendar-plus' }"
            @primary="openBatchDialog()"
          />
        </template>
        <v-data-table
          v-else
          :items="dates"
          :headers="dateHeaders"
          item-key="itemKey"
          density="comfortable"
        >
          <template #item.itemKey="{ item }">
            <span class="ds-cell-primary ds-mono-text">{{ item.itemKey }}</span>
          </template>

          <template #item.remark="{ item }">
            <span class="ds-cell-support">{{ item.remark || '-' }}</span>
          </template>

          <template #item.actions="{ item }">
            <div class="ds-action-inline">
              <v-btn
                size="small"
                variant="text"
                color="error"
                class="px-2"
                @click.stop="deleteDate(item.itemKey)"
              >
                <v-icon size="16" class="mr-1">mdi-delete</v-icon>删除
              </v-btn>
            </div>
          </template>
        </v-data-table>
      </v-card-text>
    </v-card>

    <v-dialog v-model="addDateDialog" class="ds-dialog-overlay" max-width="480">
      <v-card>
        <div class="ds-dialog-head">
          <span class="ds-dialog-icon"><v-icon size="20">mdi-calendar-plus</v-icon></span>
          <div class="ds-dialog-heading">
            <h2 class="ds-dialog-title">新增日历日期</h2>
            <p class="ds-dialog-subtitle">{{ selectedName }} · 用于补上节假日调休或漏维护的日期</p>
          </div>
          <v-btn
            icon="mdi-close"
            variant="text"
            size="small"
            aria-label="关闭"
            @click="addDateDialog = false"
          />
        </div>
        <v-divider />
        <v-card-text class="ds-dialog-body">
          <v-form ref="addDateForm" @submit.prevent="saveAddDate">
            <div class="field">
              <label class="field-label" for="date-input">日期</label>
              <v-text-field
                id="date-input"
                v-model="addDateValue"
                placeholder="yyyyMMdd"
                variant="outlined"
                density="compact"
                hide-details="auto"
                :rules="[dateRule]"
              />
              <p class="field-help">格式 yyyyMMdd，例如 20261001。已存在的日期不会重复写入。</p>
            </div>
          </v-form>
        </v-card-text>
        <v-card-actions class="ds-dialog-actions">
          <v-spacer />
          <v-btn variant="text" @click="addDateDialog = false">取消</v-btn>
          <v-btn color="primary" variant="flat" @click="saveAddDate">保存</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-dialog v-model="batchDialog" class="ds-dialog-overlay" max-width="520">
      <v-card>
        <div class="ds-dialog-head">
          <span class="ds-dialog-icon"><v-icon size="20">mdi-calendar-plus</v-icon></span>
          <div class="ds-dialog-heading">
            <h2 class="ds-dialog-title">批量生成日历日期</h2>
            <p class="ds-dialog-subtitle">{{ selectedName }} · 按年批量写入</p>
          </div>
          <v-btn
            icon="mdi-close"
            variant="text"
            size="small"
            aria-label="关闭"
            @click="batchDialog = false"
          />
        </div>
        <v-divider />
        <v-card-text class="ds-dialog-body">
          <div class="dialog-stack">
            <div class="field">
              <label class="field-label" for="batch-year">年份</label>
              <v-text-field
                id="batch-year"
                v-model="batchYear"
                type="number"
                placeholder="YYYY"
                variant="outlined"
                density="compact"
                hide-details="auto"
                :rules="[batchYearRule]"
              />
            </div>
            <v-checkbox
              v-if="selectedCode === 1021"
              v-model="batchIncludeWeekend"
              label="同时生成周末日期"
              density="compact"
              hide-details
            />
            <v-alert type="info" variant="tonal" density="comfortable">
              <div class="batch-hint">{{ batchPreview }}</div>
              <div class="batch-hint">
                {{
                  selectedCode === 1022
                    ? '工作日历只生成周一至周五，法定节假日需删除，调休上班日需手工补上。'
                    : '交易日历需按实际休市日调整。'
                }}
                已存在的日期不会被覆盖，可重复执行。
              </div>
            </v-alert>
          </div>
        </v-card-text>
        <v-card-actions class="ds-dialog-actions">
          <v-spacer />
          <v-btn variant="text" @click="batchDialog = false">取消</v-btn>
          <v-btn color="primary" variant="flat" @click="batchGenerate">生成并保存</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-dialog v-model="tradeDialog" class="ds-dialog-overlay" max-width="520">
      <v-card>
        <div class="ds-dialog-head">
          <span class="ds-dialog-icon"><v-icon size="20">mdi-calendar-sync-outline</v-icon></span>
          <div class="ds-dialog-heading">
            <h2 class="ds-dialog-title">年度补全交易日历</h2>
            <p class="ds-dialog-subtitle">优先使用柜台接口，失败时退化为固定规则。</p>
          </div>
          <v-btn
            icon="mdi-close"
            variant="text"
            size="small"
            aria-label="关闭"
            @click="tradeDialog = false"
          />
        </div>
        <v-divider />
        <v-card-text class="ds-dialog-body">
          <div class="dialog-stack">
            <div class="field">
              <label class="field-label" for="trade-year">年份</label>
              <v-text-field
                id="trade-year"
                v-model.number="tradeYear"
                type="number"
                placeholder="YYYY"
                variant="outlined"
                density="compact"
                hide-details="auto"
              />
            </div>
            <v-alert type="info" variant="tonal" density="comfortable">
              <div class="batch-hint">接口超时、结构变化或年份未发布时会给出提示，并自动退化：</div>
              <div class="batch-hint">
                周一至周五，扣除元旦 1 月 1-3 日与国庆 10 月 1-7 日。可重复执行，不会重复写入。
              </div>
            </v-alert>
          </div>
        </v-card-text>
        <v-card-actions class="ds-dialog-actions">
          <v-spacer />
          <v-btn variant="text" @click="tradeDialog = false">取消</v-btn>
          <v-btn color="primary" variant="flat" :loading="tradeLoading" @click="runTradeRefresh">
            开始补全
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-dialog v-model="resultDialog" class="ds-dialog-overlay" max-width="480">
      <v-card>
        <v-card-title>交易日历补全结果</v-card-title>
        <v-card-text class="result-lines">{{ resultText }}</v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn color="primary" variant="flat" @click="resultDialog = false">知道了</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <ConfirmDialog
      v-model="confirmState.open"
      :title="confirmState.title"
      :message="confirmState.message"
      confirm-text="删除"
      @confirm="runConfirmed"
    />
  </div>
</template>

<script setup lang="ts">
  import { computed, onMounted, ref, watch } from 'vue';
  import PageHeader from '@/components/PageHeader.vue';
  import EmptyState from '@/components/EmptyState.vue';
  import ConfirmDialog from '@/components/ConfirmDialog.vue';
  import calendarService from '@/service/calendar-service';
  import { notify } from '@/stores/notifier';
  import type { SysItem } from '@/types/database';

  const calendars = [
    { code: 1021, name: '交易日历' },
    { code: 1022, name: '工作日历' },
  ];

  const selectedCode = ref<number>(1021);
  const year = ref<number | null>(new Date().getFullYear());
  const dates = ref<SysItem[]>([]);
  const loading = ref(true);
  const coverageWarnings = ref<string[]>([]);

  const selectedName = computed(
    () => calendars.find(cal => cal.code === selectedCode.value)?.name ?? ''
  );

  const dateHeaders = [
    { title: '日期', key: 'itemKey', minWidth: '160px' },
    { title: '来源', key: 'remark', minWidth: '200px' },
    { title: '操作', key: 'actions', width: 120, sortable: false },
  ];

  const confirmState = ref<{
    open: boolean;
    title: string;
    message: string;
    action: (() => void) | null;
  }>({ open: false, title: '', message: '', action: null });

  const askConfirm = (title: string, message: string, action: () => void) => {
    confirmState.value = { open: true, title, message, action };
  };

  const runConfirmed = () => {
    const action = confirmState.value.action;
    confirmState.value.open = false;
    action?.();
  };

  const yearRule = (value: any) => {
    const y = Number(value);
    return (Number.isInteger(y) && y >= 1900 && y <= 9999) || '请输入 1900-9999 的年份';
  };

  const dateRule = (value: any) => {
    if (!value || !String(value).trim()) return '必填项';
    return /^\d{8}$/.test(String(value).trim()) || '日期格式应为 yyyyMMdd';
  };

  const batchYearRule = (value: any) => {
    const y = Number(value);
    return (Number.isInteger(y) && y >= 1900 && y <= 9999) || '请输入 1900-9999 的年份';
  };

  const countWeekdays = (targetYear: number) => {
    let count = 0;
    const cursor = new Date(targetYear, 0, 1);
    while (cursor.getFullYear() === targetYear) {
      const day = cursor.getDay();
      if (day !== 0 && day !== 6) count += 1;
      cursor.setDate(cursor.getDate() + 1);
    }
    return count;
  };

  const loadDates = () => {
    if (!year.value) return;
    loading.value = true;
    calendarService
      .listDates(selectedCode.value, year.value)
      .then(res => {
        dates.value = res || [];
      })
      .catch(() => {
        dates.value = [];
      })
      .finally(() => {
        loading.value = false;
      });
  };

  const loadCoverage = () => {
    calendarService
      .getCoverage()
      .then(rows => {
        coverageWarnings.value = (rows || [])
          .map(row => row.warning)
          .filter((warning): warning is string => !!warning);
      })
      .catch(error => console.error('加载日历覆盖范围失败', error));
  };

  const refreshAll = () => {
    loadDates();
    loadCoverage();
  };

  // —— 新增单日 ——
  const addDateDialog = ref(false);
  const addDateValue = ref('');
  const addDateForm = ref<any>(null);

  const openAddDateDialog = () => {
    addDateValue.value = '';
    addDateDialog.value = true;
  };

  const saveAddDate = async () => {
    const { valid } = await addDateForm.value.validate();
    if (!valid) return;
    try {
      await calendarService.addDate(selectedCode.value, String(addDateValue.value).trim());
      addDateDialog.value = false;
      notify('日期已加入日历', 'success');
      refreshAll();
    } catch (err: any) {
      notify('新增失败: ' + (err?.message || err), 'error');
    }
  };

  const deleteDate = (dateStr: string) => {
    askConfirm('删除日历日期', `将从「${selectedName.value}」删除日期「${dateStr}」。`, async () => {
      try {
        await calendarService.removeDate(selectedCode.value, dateStr);
        notify('日期已删除', 'success');
        refreshAll();
      } catch (err: any) {
        notify('删除失败: ' + (err?.message || err), 'error');
      }
    });
  };

  // —— 批量生成 ——
  const batchDialog = ref(false);
  const batchYear = ref<number | null>(new Date().getFullYear());
  const batchIncludeWeekend = ref(true);

  const batchPreview = computed(() => {
    const targetYear = Number(batchYear.value);
    if (!Number.isInteger(targetYear) || targetYear < 1900 || targetYear > 9999) {
      return '输入年份后可预览生成数量';
    }
    const workdays = countWeekdays(targetYear);
    if (selectedCode.value === 1022) return `预计生成 ${workdays} 个工作日`;
    const allDays =
      (targetYear % 4 === 0 && targetYear % 100 !== 0) || targetYear % 400 === 0 ? 366 : 365;
    return `预计生成 ${batchIncludeWeekend.value ? allDays : workdays} 天`;
  });

  const openBatchDialog = () => {
    batchYear.value = year.value || new Date().getFullYear();
    batchIncludeWeekend.value = true;
    batchDialog.value = true;
  };

  const batchGenerate = async () => {
    if (!batchYear.value || batchYear.value < 1900 || batchYear.value > 9999) {
      return notify('请输入有效年份', 'warning');
    }
    const include = selectedCode.value === 1021 ? batchIncludeWeekend.value : false;
    try {
      await calendarService.generate(selectedCode.value, batchYear.value, include);
      batchDialog.value = false;
      year.value = batchYear.value;
      notify('日历日期已生成', 'success');
      refreshAll();
    } catch (err: any) {
      notify('批量生成失败: ' + (err?.message || err), 'error');
    }
  };

  // —— 年度补全交易日历 ——
  const tradeDialog = ref(false);
  const tradeLoading = ref(false);
  const tradeYear = ref(new Date().getFullYear() + 1);
  const resultDialog = ref(false);
  const resultText = ref('');

  const openTradeDialog = () => {
    tradeYear.value = new Date().getFullYear() + 1;
    tradeDialog.value = true;
  };

  const runTradeRefresh = async () => {
    const targetYear = tradeYear.value;
    if (!targetYear || targetYear < 2000 || targetYear > 2100) {
      return notify('请输入有效年份', 'warning');
    }
    tradeLoading.value = true;
    try {
      const result = await calendarService.refreshTrade(targetYear);
      tradeDialog.value = false;
      year.value = targetYear;
      refreshAll();
      const lines = [
        `${result.year} 年交易日历补全完成`,
        `来源：${result.source === 'SSE' ? '柜台接口（SSE）' : '默认规则（柜台接口不可用）'}`,
        `交易日 ${result.tradingDays} 天，新增 ${result.added} 天，移除 ${result.removed} 天`,
      ];
      if (result.removedDates?.length) lines.push(`移除日期：${result.removedDates.join('、')}`);
      if (result.warning) lines.push('', `⚠ ${result.warning}`);
      resultText.value = lines.join('\n');
      resultDialog.value = true;
    } catch (err: any) {
      notify('补全失败: ' + (err?.message || err), 'error');
    } finally {
      tradeLoading.value = false;
    }
  };

  onMounted(() => {
    loadDates();
    loadCoverage();
  });

  // 切换日历（交易日历/工作日历）时重新加载日期，并刷新覆盖范围提醒。
  watch(selectedCode, () => {
    loadDates();
    loadCoverage();
  });
</script>

<style scoped>
  .section-body {
    display: flex;
    flex-direction: column;
    gap: 16px;
  }

  .section-header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 12px;
  }

  .section-title {
    font-size: 1rem;
    font-weight: 700;
    color: rgb(var(--v-theme-on-surface));
  }

  .section-subtitle {
    margin-top: 4px;
    color: var(--ds-text-2);
    line-height: 1.6;
  }

  .calendar-tab {
    min-width: 140px;
  }

  .tab-code {
    font-size: 11px;
    opacity: 0.7;
  }

  .toolbar {
    display: flex;
    align-items: center;
    gap: 12px;
    flex-wrap: wrap;
  }

  .year-field {
    max-width: 140px;
  }

  .date-count {
    color: var(--ds-text-2);
    font-size: 13px;
    font-variant-numeric: tabular-nums;
  }

  .dialog-stack {
    display: flex;
    flex-direction: column;
    gap: 14px;
  }

  .field {
    min-width: 0;
  }

  .field-label {
    display: block;
    margin-bottom: 6px;
    color: rgb(var(--v-theme-on-surface));
    font-size: 12.5px;
    font-weight: 500;
  }

  .field-help {
    margin: 5px 0 0;
    color: var(--ds-text-3);
    font-size: 11.5px;
    line-height: 1.45;
  }

  .batch-hint {
    font-size: 12px;
    line-height: 1.55;
  }

  .result-lines {
    white-space: pre-wrap;
    font-size: 14px;
    line-height: 1.8;
  }
</style>

<route lang="json">
{
  "meta": {
    "title": "采集日历",
    "icon": "mdi-calendar-range",
    "requiresAuth": true,
    "navGroup": "systemManage",
    "navOrder": 18,
    "roles": ["admin"]
  }
}
</route>
