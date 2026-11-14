<template>
  <div class="dashboard-container" :class="{ 'dark-mode': isDark }">
    <div class="background-overlay" />

    <v-container fluid class="pa-5 dashboard-content">
      <v-tabs v-model="tab" color="primary" density="comfortable" class="dashboard-tabs mb-2">
        <v-tab value="overview">经营总览</v-tab>
        <v-tab value="today">今日运行</v-tab>
        <v-tab value="health">运行健康</v-tab>
        <v-tab value="quality">数据质量</v-tab>
        <v-tab value="sla">耗时与 SLA</v-tab>
      </v-tabs>

      <v-tabs-window v-model="tab" class="dashboard-window" :transition="false">
        <v-tabs-window-item value="overview">
          <div class="executive-overview">
            <DemoBanner v-if="APP_IS_DEMO" />

            <OnboardingChecklist
              v-if="!isEmptyEnvironment"
              :source-count="summary?.allDbSourceCount ?? 0"
              :table-count="summary?.tableCount ?? 0"
              :done-count="totals.done"
              :loading="loading"
            />

            <FilterBar
              v-if="!isEmptyEnvironment"
              v-model="rangeDays"
              :ranges="RANGES"
              :updated-at="updatedAt"
              :loading="refreshing"
              @refresh="refreshAll"
            />

            <ErrorState v-if="error" :message="error" class="exec-error" @retry="refreshAll" />

            <EmptyDashboardGuide v-if="isEmptyEnvironment" />

            <template v-else>
              <HealthHero :health-rate="healthRate" :totals="totals" :status="healthStatus" />

              <KpiRow
                :today-data="todayData"
                :total-data="totalData"
                :avg-time="avgTime"
                :avg-delta="avgDelta"
                :volume-delta="volumeDelta"
                :volume-sparkline="volumeSparkline"
                :total-days="summary?.totalDays ?? 0"
                :coverage="coverage"
                :health-rate="healthRate"
                :done="totals.done"
                :total="totals.total"
                :loading="loading"
              />

              <v-row density="comfortable" class="exec-row">
                <v-col cols="12" lg="7">
                  <TodayPulse compact @show-all="tab = 'today'" />
                </v-col>
                <v-col cols="12" lg="5">
                  <RiskQueue
                    :items="riskItems"
                    :loading="loading"
                    :error="riskError"
                    @retry="refreshAll"
                  />
                </v-col>
              </v-row>

              <v-row density="comfortable" class="exec-row">
                <v-col cols="12" lg="7">
                  <VolumeTrend
                    :series="volumeSeries"
                    :range-days="rangeDays"
                    :loading="loading"
                    :error="volumeError"
                    @retry="refreshAll"
                  />
                </v-col>
                <v-col cols="12" lg="5">
                  <SourceContribution
                    :items="sourceContribution"
                    :range-days="rangeDays"
                    :loading="loading"
                    :error="volumeError"
                    @retry="refreshAll"
                  />
                </v-col>
              </v-row>

              <ValueScale
                :total-data="totalData"
                :total-days="summary?.totalDays ?? 0"
                :source-count="summary?.allDbSourceCount ?? 0"
                :coverage="coverage"
                :health-rate="healthRate"
              />

              <SectionCard
                title="采集源完成率"
                subtitle="按完成率升序，优先暴露风险源"
                icon="mdi-database-sync-outline"
              >
                <template #actions>
                  <v-btn
                    v-if="ratios.length > RATIO_PREVIEW"
                    size="small"
                    variant="text"
                    append-icon="mdi-arrow-right"
                    @click="openRatioDialog"
                  >
                    查看全部 {{ ratios.length }}
                  </v-btn>
                </template>
                <SourceRatioList :ratios="ratios" :limit="RATIO_PREVIEW" />
                <p v-if="hiddenRatioCount > 0" class="ratio-footnote">
                  按完成率升序，仅显示最低的 {{ RATIO_PREVIEW }} 个，另有
                  {{ hiddenRatioCount }} 个已折叠
                </p>
              </SectionCard>
            </template>
          </div>

          <v-dialog
            v-model="ratioDialog"
            class="ds-dialog-overlay"
            scrim="rgba(2, 6, 23, 0.62)"
            max-width="720"
          >
            <v-card>
              <div class="ds-dialog-head">
                <span class="ds-dialog-icon"><v-icon size="20">mdi-chart-donut</v-icon></span>
                <div class="ds-dialog-heading">
                  <h2 class="ds-dialog-title">采集源完成率</h2>
                  <p class="ds-dialog-subtitle">
                    共 {{ ratios.length }} 个采集源，按完成率升序排列；{{
                      ratioFailedCount
                    }}
                    个有失败，{{ ratioUnfinishedCount }} 个未完成
                  </p>
                </div>
                <v-btn
                  icon="mdi-close"
                  variant="text"
                  size="small"
                  aria-label="关闭"
                  @click="ratioDialog = false"
                />
              </div>
              <v-divider />
              <v-card-text class="ds-dialog-body ratio-dialog-body">
                <div class="ratio-dialog-tools">
                  <v-btn
                    size="small"
                    variant="text"
                    class="ratio-filter"
                    :class="{ 'ratio-filter--on': ratioOnlyUnfinished }"
                    @click="ratioOnlyUnfinished = !ratioOnlyUnfinished"
                  >
                    {{ ratioOnlyUnfinished ? '显示全部' : '仅看未完成' }}
                  </v-btn>
                  <span class="ratio-dialog-count">{{ dialogRatios.length }} 个源</span>
                </div>
                <div class="ratio-dialog-list">
                  <SourceRatioList :ratios="dialogRatios" empty-text="没有未完成的采集源" />
                </div>
              </v-card-text>
              <div class="ds-dialog-actions">
                <span class="ds-dialog-actions__hint">
                  完成率按已采集表数计算，未完成时向下取整，不会显示成 100%
                </span>
                <v-spacer />
                <v-btn variant="text" @click="ratioDialog = false">关闭</v-btn>
              </div>
            </v-card>
          </v-dialog>
        </v-tabs-window-item>

        <v-tabs-window-item value="today">
          <TodayPulse />
        </v-tabs-window-item>

        <v-tabs-window-item value="health">
          <v-row>
            <v-col cols="12" lg="7">
              <FailTrendBar />
            </v-col>
            <v-col cols="12" lg="5">
              <FailTopTable />
            </v-col>
          </v-row>
          <v-row>
            <v-col cols="12">
              <MissingCollectCalendar />
            </v-col>
          </v-row>
        </v-tabs-window-item>

        <v-tabs-window-item value="quality">
          <v-row>
            <v-col cols="12" lg="6">
              <ErrorRateLine />
            </v-col>
            <v-col cols="12" lg="6">
              <RecsBytesDualLine />
            </v-col>
          </v-row>
          <v-row>
            <v-col cols="12">
              <SourceContributionArea />
            </v-col>
          </v-row>
        </v-tabs-window-item>

        <v-tabs-window-item value="sla">
          <v-row>
            <v-col cols="12" lg="6">
              <SlowTableBar />
            </v-col>
            <v-col cols="12" lg="6">
              <TakeSecsHistogram />
            </v-col>
          </v-row>
        </v-tabs-window-item>
      </v-tabs-window>
    </v-container>
  </div>
</template>

<script setup lang="ts">
  import { computed, ref } from 'vue';
  import { useTheme } from 'vuetify';
  import FilterBar from '@/components/common/FilterBar.vue';
  import ErrorState from '@/components/common/ErrorState.vue';
  import SectionCard from '@/components/common/SectionCard.vue';
  import OnboardingChecklist from '@/components/onboarding/OnboardingChecklist.vue';
  import EmptyDashboardGuide from '@/components/onboarding/EmptyDashboardGuide.vue';
  import DemoBanner from '@/components/common/DemoBanner.vue';
  import { APP_IS_DEMO } from '@/config/branding';
  import HealthHero from '@/components/dashboard/executive/HealthHero.vue';
  import KpiRow from '@/components/dashboard/executive/KpiRow.vue';
  import RiskQueue from '@/components/dashboard/executive/RiskQueue.vue';
  import VolumeTrend from '@/components/dashboard/executive/VolumeTrend.vue';
  import SourceContribution from '@/components/dashboard/executive/SourceContribution.vue';
  import ValueScale from '@/components/dashboard/executive/ValueScale.vue';
  import SourceRatioList from '@/components/dashboard/SourceRatioList.vue';
  import TodayPulse from '@/components/dashboard/TodayPulse.vue';
  import FailTrendBar from '@/components/dashboard/FailTrendBar.vue';
  import FailTopTable from '@/components/dashboard/FailTopTable.vue';
  import MissingCollectCalendar from '@/components/dashboard/MissingCollectCalendar.vue';
  import ErrorRateLine from '@/components/dashboard/ErrorRateLine.vue';
  import RecsBytesDualLine from '@/components/dashboard/RecsBytesDualLine.vue';
  import SourceContributionArea from '@/components/dashboard/SourceContributionArea.vue';
  import SlowTableBar from '@/components/dashboard/SlowTableBar.vue';
  import TakeSecsHistogram from '@/components/dashboard/TakeSecsHistogram.vue';
  import { useExecutiveDashboard } from '@/composables/useExecutiveDashboard';
  import { loadTodayPulse } from '@/composables/useTodayPulse';

  const vuetifyTheme = useTheme();
  const isDark = computed(() => vuetifyTheme.current.value.dark);
  const tab = ref('overview');

  const RANGES = [
    { label: '近 7 天', value: 7 },
    { label: '近 30 天', value: 30 },
  ];
  const RATIO_PREVIEW = 8;

  const {
    summary,
    loading,
    refreshing,
    error,
    riskError,
    volumeError,
    updatedAt,
    rangeDays,
    ratios,
    totals,
    healthRate,
    healthStatus,
    volumeSeries,
    volumeDelta,
    volumeSparkline,
    sourceContribution,
    riskItems,
    todayData,
    totalData,
    avgTime,
    avgDelta,
    coverage,
    reload,
  } = useExecutiveDashboard();

  const isEmptyEnvironment = computed(
    () =>
      !loading.value &&
      (summary.value?.allDbSourceCount ?? 0) === 0 &&
      (summary.value?.allTableCount ?? 0) === 0
  );

  const refreshAll = async () => {
    await Promise.all([reload(), loadTodayPulse(true)]);
  };

  const ratioDialog = ref(false);
  const ratioOnlyUnfinished = ref(false);

  const ratioFailedCount = computed(
    () => ratios.value.filter(ratio => Number(ratio.failed ?? 0) > 0).length
  );
  const ratioUnfinishedCount = computed(
    () => ratios.value.filter(ratio => Number(ratio.done ?? 0) < Number(ratio.total ?? 0)).length
  );
  const hiddenRatioCount = computed(() => Math.max(ratios.value.length - RATIO_PREVIEW, 0));
  const dialogRatios = computed(() =>
    ratioOnlyUnfinished.value
      ? ratios.value.filter(ratio => Number(ratio.done ?? 0) < Number(ratio.total ?? 0))
      : ratios.value
  );

  const openRatioDialog = () => {
    ratioOnlyUnfinished.value = false;
    ratioDialog.value = true;
  };
</script>

<route lang="json">
{
  "meta": {
    "title": "首页",
    "icon": "mdi-home",
    "requiresAuth": true,
    "navOrder": 0
  }
}
</route>

<style scoped>
  .dashboard-container {
    min-height: 100vh;
    width: 100%;
    position: relative;
    /* Why: clip keeps the horizontal guard without creating a scroll container */
    overflow-x: clip;
    transition: background 0.3s ease;
    background: rgb(var(--v-theme-surface));
  }

  .dashboard-window {
    overflow: visible;
  }

  .background-overlay {
    position: absolute;
    inset: 0;
    pointer-events: none;
    z-index: 0;
  }

  .dark-mode .background-overlay {
    background: radial-gradient(circle at 20% 0%, rgba(91, 157, 255, 0.06), transparent 40%);
  }

  .dashboard-container:not(.dark-mode) .background-overlay {
    background: radial-gradient(circle at 20% 0%, rgba(23, 111, 214, 0.05), transparent 40%);
  }

  .dashboard-content {
    position: relative;
    z-index: 1;
  }

  .dashboard-tabs {
    border-bottom: 1px solid var(--ds-border-subtle);
  }

  .executive-overview {
    display: flex;
    flex-direction: column;
    gap: 10px;
  }

  .exec-row {
    margin-top: 0;
  }

  .exec-error {
    margin: 0;
  }

  .ratio-footnote {
    margin: 10px 0 0;
    color: var(--ds-text-3);
    font-size: 11px;
  }

  .ratio-dialog-body {
    display: flex;
    flex-direction: column;
    gap: 10px;
  }

  .ratio-dialog-tools {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
  }

  .ratio-filter {
    letter-spacing: 0;
    font-size: 12px;
    color: var(--ds-text-2);
  }

  .ratio-filter--on {
    color: rgb(var(--v-theme-primary));
    font-weight: 600;
  }

  .ratio-dialog-count {
    font-size: 12px;
    color: var(--ds-text-3);
  }

  .ratio-dialog-list {
    max-height: 58vh;
    overflow-y: auto;
    padding-right: 4px;
  }
</style>
