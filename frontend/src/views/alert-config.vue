<template>
  <div class="page-shell alert-config-page">
    <PageHeader
      title="告警配置"
      subtitle="统一管理告警通道、路由规则、投递记录与维护静默；告警异步发送，不影响采集任务。"
    >
      <v-btn
        data-testid="send-test-event"
        variant="tonal"
        prepend-icon="mdi-bell-ring-outline"
        :loading="testingEvent"
        @click="sendTestEvent"
      >
        发送测试告警
      </v-btn>
      <v-btn
        data-testid="channel-create"
        color="primary"
        variant="flat"
        prepend-icon="mdi-plus"
        @click="openChannelCreate"
      >
        新增通道
      </v-btn>
    </PageHeader>

    <v-alert v-if="loadError && !loading" type="error" variant="tonal" title="告警配置加载失败">
      <div class="load-error">
        <span>请检查后端服务与当前账号权限后重试。</span>
        <v-btn size="small" variant="flat" color="error" @click="loadAll">重新加载</v-btn>
      </div>
    </v-alert>

    <v-alert v-if="!loading && summary.paused" type="warning" variant="tonal" class="paused-banner">
      <div class="paused-body">
        <div>
          <strong>尚未配置可用的告警通道，告警已暂停。</strong>
          <p>
            采集失败、恢复与安全事件仍会写入事件与投递记录（状态
            SKIPPED_NO_CHANNEL），但不会触达任何人。
          </p>
        </div>
        <v-btn color="warning" variant="flat" size="small" @click="openChannelCreate">
          配置第一个通道
        </v-btn>
      </div>
    </v-alert>

    <v-card flat class="ds-card alert-shell">
      <v-tabs v-model="tab" class="alert-tabs" show-arrows>
        <v-tab value="channels" data-testid="tab-channels" prepend-icon="mdi-access-point"
          >通道</v-tab
        >
        <v-tab value="rules" data-testid="tab-rules" prepend-icon="mdi-sitemap-outline">规则</v-tab>
        <v-tab value="deliveries" data-testid="tab-deliveries" prepend-icon="mdi-history"
          >投递记录</v-tab
        >
        <v-tab value="silences" data-testid="tab-silences" prepend-icon="mdi-bell-off-outline"
          >静默</v-tab
        >
      </v-tabs>
      <v-divider />

      <v-tabs-window v-model="tab">
        <!-- ── 通道 ─────────────────────────────────────────────── -->
        <v-tabs-window-item value="channels">
          <div class="tab-body">
            <div class="tab-toolbar">
              <div class="tab-summary">
                <strong>{{ summary.enabledChannelCount }}</strong>
                <span>/ {{ summary.channelCount }} 个通道已启用</span>
                <v-chip v-if="summary.paused" size="small" color="warning" variant="tonal">
                  告警已暂停
                </v-chip>
              </div>
              <v-btn
                size="small"
                color="primary"
                variant="flat"
                prepend-icon="mdi-plus"
                data-testid="channel-create-tab"
                @click="openChannelCreate"
              >
                新增通道
              </v-btn>
            </div>

            <div v-if="loading" class="channel-grid">
              <div v-for="n in 3" :key="n" class="ds-card skeleton-card">
                <div class="ds-skeleton" style="height: 20px; width: 45%" />
                <div class="ds-skeleton" style="height: 14px; width: 70%; margin-top: 12px" />
                <div class="ds-skeleton" style="height: 36px; margin-top: 20px" />
              </div>
            </div>

            <div v-else-if="channels.length === 0" class="empty-state">
              <v-icon size="40" color="medium-emphasis">mdi-bell-plus-outline</v-icon>
              <h3>还没有告警通道</h3>
              <p>新增企业微信、钉钉、飞书、邮件或 Webhook 通道，测试成功并启用后告警才会发出。</p>
              <v-btn
                color="primary"
                variant="flat"
                prepend-icon="mdi-plus"
                @click="openChannelCreate"
              >
                新增第一个通道
              </v-btn>
            </div>

            <div v-else class="channel-grid">
              <v-card
                v-for="channel in channels"
                :key="channel.id"
                flat
                class="ds-card channel-card"
                data-testid="channel-card"
                :data-channel-name="channel.name"
              >
                <div class="channel-top">
                  <span
                    class="channel-badge"
                    :style="{
                      background: `${CHANNEL_META[channel.type].color}18`,
                      color: CHANNEL_META[channel.type].color,
                    }"
                  >
                    <v-icon size="22">{{ CHANNEL_META[channel.type].icon }}</v-icon>
                  </span>
                  <div class="channel-identity">
                    <h3>{{ channel.name }}</h3>
                    <p>{{ channel.typeLabel }} · 每分钟最多 {{ channel.rateLimitPerMinute }} 条</p>
                  </div>
                  <v-chip
                    size="small"
                    :color="channel.enabled ? 'success' : undefined"
                    variant="tonal"
                  >
                    {{ channel.enabled ? '已启用' : '已停用' }}
                  </v-chip>
                </div>

                <div class="channel-test-row">
                  <span class="test-label">最近测试</span>
                  <span class="test-time">
                    {{ channel.lastTestAt ? formatTime(channel.lastTestAt) : '尚未测试' }}
                  </span>
                  <v-chip
                    size="x-small"
                    :color="
                      channel.lastTestOk === true
                        ? 'success'
                        : channel.lastTestOk === false
                          ? 'error'
                          : undefined
                    "
                    variant="tonal"
                  >
                    {{
                      channel.lastTestOk === true
                        ? '成功'
                        : channel.lastTestOk === false
                          ? '失败'
                          : '未测试'
                    }}
                  </v-chip>
                </div>

                <v-alert
                  v-if="channel.config._decryptError"
                  type="error"
                  variant="tonal"
                  density="compact"
                  class="channel-alert"
                >
                  {{ channel.config._decryptError }}
                </v-alert>
                <v-alert
                  v-else-if="!channel.lastTestOk"
                  type="info"
                  variant="tonal"
                  density="compact"
                  class="channel-alert"
                >
                  启用前需要通过“测试发送”验证当前配置。
                </v-alert>

                <div class="channel-actions">
                  <v-btn
                    size="small"
                    variant="tonal"
                    prepend-icon="mdi-send-outline"
                    data-testid="channel-test"
                    :loading="testingChannelId === channel.id"
                    @click="testChannel(channel)"
                  >
                    测试发送
                  </v-btn>
                  <v-btn
                    size="small"
                    variant="text"
                    prepend-icon="mdi-pencil-outline"
                    @click="openChannelEdit(channel)"
                  >
                    编辑
                  </v-btn>
                  <v-btn
                    size="small"
                    variant="text"
                    color="error"
                    prepend-icon="mdi-delete-outline"
                    @click="askDeleteChannel(channel)"
                  >
                    删除
                  </v-btn>
                  <v-btn
                    class="channel-toggle"
                    data-testid="channel-enable"
                    size="small"
                    :variant="channel.enabled ? 'text' : 'flat'"
                    :color="channel.enabled ? 'warning' : 'success'"
                    :prepend-icon="
                      channel.enabled ? 'mdi-pause-circle-outline' : 'mdi-play-circle-outline'
                    "
                    :disabled="!channel.enabled && !channel.lastTestOk"
                    :loading="togglingChannelId === channel.id"
                    @click="toggleChannel(channel, !channel.enabled)"
                  >
                    {{ channel.enabled ? '停用' : '启用' }}
                  </v-btn>
                </div>
              </v-card>
            </div>
          </div>
        </v-tabs-window-item>

        <!-- ── 规则 ─────────────────────────────────────────────── -->
        <v-tabs-window-item value="rules">
          <div class="tab-body">
            <div class="tab-toolbar">
              <div class="tab-summary">
                <span>
                  规则按事件类型与范围选择通道；没有任何规则匹配时，事件默认发送到
                  <strong>所有启用通道</strong>。
                </span>
              </div>
              <v-btn
                size="small"
                color="primary"
                variant="flat"
                prepend-icon="mdi-plus"
                data-testid="rule-create"
                @click="openRuleCreate"
              >
                新增规则
              </v-btn>
            </div>

            <v-data-table
              :items="rules"
              :headers="ruleHeaders"
              :loading="loading"
              density="comfortable"
              class="ds-table"
              no-data-text="还没有告警规则，事件将按默认路由发送到所有启用通道"
            >
              <template #item.name="{ item }">
                <div class="cell-stack">
                  <strong>{{ item.name }}</strong>
                  <span>{{ scopeLabel(item.scopeType, item.scopeValue) }}</span>
                </div>
              </template>
              <template #item.eventType="{ item }">
                <v-chip size="small" variant="tonal">
                  {{ eventTypeLabel(item.eventType) }}
                </v-chip>
              </template>
              <template #item.severity="{ item }">
                <v-chip size="small" variant="flat" :color="severityColor(item.severity)">
                  {{ item.severity }}
                </v-chip>
              </template>
              <template #item.channels="{ item }">
                <span class="cell-muted">{{ channelNames(item.channelIds) }}</span>
              </template>
              <template #item.cooldown="{ item }">
                <span class="cell-muted"
                  >{{ item.cooldownSecs }}s / {{ item.groupWindowSecs }}s</span
                >
              </template>
              <template #item.enabled="{ item }">
                <v-switch
                  :model-value="item.enabled"
                  color="success"
                  density="compact"
                  hide-details
                  inset
                  @update:model-value="value => toggleRule(item, !!value)"
                />
              </template>
              <template #item.actions="{ item }">
                <div class="row-actions">
                  <v-btn
                    size="small"
                    variant="text"
                    prepend-icon="mdi-pencil-outline"
                    @click="openRuleEdit(item)"
                  >
                    编辑
                  </v-btn>
                  <v-btn
                    size="small"
                    variant="text"
                    color="error"
                    prepend-icon="mdi-delete-outline"
                    @click="askDeleteRule(item)"
                  >
                    删除
                  </v-btn>
                </div>
              </template>
            </v-data-table>
          </div>
        </v-tabs-window-item>

        <!-- ── 投递记录 ─────────────────────────────────────────── -->
        <v-tabs-window-item value="deliveries">
          <div class="tab-body">
            <div class="tab-toolbar tab-toolbar--filters">
              <v-select
                v-model="deliveryFiltersView.status"
                :items="deliveryStatusOptions"
                label="状态"
                clearable
                density="compact"
                variant="outlined"
                hide-details
                class="filter-select"
                @update:model-value="searchDeliveries"
              />
              <v-select
                v-model="deliveryFiltersView.channelId"
                :items="channelSelectItems"
                item-title="title"
                item-value="id"
                label="通道"
                clearable
                density="compact"
                variant="outlined"
                hide-details
                class="filter-select filter-select--wide"
                @update:model-value="searchDeliveries"
              />
              <v-spacer />
              <v-btn
                variant="tonal"
                size="small"
                prepend-icon="mdi-refresh"
                :loading="deliveryLoading"
                @click="searchDeliveries"
              >
                刷新
              </v-btn>
            </div>

            <v-data-table-server
              :items="deliveries"
              :headers="deliveryHeaders"
              :items-length="deliveryTotal"
              :items-per-page="deliveryPageSize"
              :page="deliveryPage"
              :loading="deliveryLoading"
              density="comfortable"
              class="ds-table"
              no-data-text="暂无投递记录"
              @update:options="loadDeliveries"
            >
              <template #item.createdAt="{ item }">
                <span class="cell-muted">{{ formatTime(item.createdAt) }}</span>
              </template>
              <template #item.eventTitle="{ item }">
                <div class="cell-stack">
                  <strong>{{ item.eventTitle || `事件 #${item.eventId}` }}</strong>
                  <span>{{ eventTypeLabel(item.eventType) }}</span>
                </div>
              </template>
              <template #item.severity="{ item }">
                <v-chip size="small" variant="flat" :color="severityColor(item.severity)">
                  {{ item.severity || '—' }}
                </v-chip>
              </template>
              <template #item.channelName="{ item }">
                <span>{{ item.channelName || '未分配通道' }}</span>
              </template>
              <template #item.status="{ item }">
                <v-chip size="small" variant="tonal" :color="deliveryStatusColor(item.status)">
                  {{ deliveryStatusLabel(item.status) }}
                </v-chip>
              </template>
              <template #item.attempts="{ item }">
                <span class="cell-muted">{{ item.attempts }} / 5</span>
              </template>
              <template #item.responseCode="{ item }">
                <span class="cell-muted">{{ item.responseCode ?? '—' }}</span>
              </template>
              <template #item.actions="{ item }">
                <div class="row-actions">
                  <v-btn size="small" variant="text" @click="openDeliveryDetail(item)">
                    详情
                  </v-btn>
                  <v-btn
                    v-if="item.status === 'FAILED' || item.status === 'DEAD'"
                    size="small"
                    variant="text"
                    color="primary"
                    :data-testid="`retry-delivery-${item.id}`"
                    :loading="retryingDeliveryId === item.id"
                    @click="retryDelivery(item)"
                  >
                    重试
                  </v-btn>
                </div>
              </template>
            </v-data-table-server>
          </div>
        </v-tabs-window-item>

        <!-- ── 静默 ─────────────────────────────────────────────── -->
        <v-tabs-window-item value="silences">
          <div class="tab-body">
            <div class="tab-toolbar">
              <div class="tab-summary">
                静默窗口内命中的事件会记为 SILENCED，不发送外部消息，但仍保留事件与投递记录。
              </div>
              <v-btn
                size="small"
                color="primary"
                variant="flat"
                prepend-icon="mdi-plus"
                @click="openSilenceCreate"
              >
                新增静默
              </v-btn>
            </div>

            <v-data-table
              :items="silences"
              :headers="silenceHeaders"
              :loading="silenceLoading"
              density="comfortable"
              class="ds-table"
              no-data-text="暂无静默窗口"
            >
              <template #item.scopeType="{ item }">
                <span>{{ scopeLabel(item.scopeType, item.scopeValue) }}</span>
              </template>
              <template #item.window="{ item }">
                <div class="cell-stack">
                  <span>{{ formatTime(item.startsAt) }}</span>
                  <span>至 {{ formatTime(item.endsAt) }}</span>
                </div>
              </template>
              <template #item.active="{ item }">
                <v-chip size="small" variant="tonal" :color="item.active ? 'warning' : undefined">
                  {{ item.active ? '生效中' : '未生效' }}
                </v-chip>
              </template>
              <template #item.reason="{ item }">
                <span class="cell-muted">{{ item.reason || '—' }}</span>
              </template>
              <template #item.actions="{ item }">
                <v-btn
                  size="small"
                  variant="text"
                  color="error"
                  prepend-icon="mdi-delete-outline"
                  @click="askDeleteSilence(item)"
                >
                  删除
                </v-btn>
              </template>
            </v-data-table>
          </div>
        </v-tabs-window-item>
      </v-tabs-window>
    </v-card>

    <!-- ── 通道编辑 ───────────────────────────────────────────── -->
    <v-dialog v-model="channelDialog" max-width="760" persistent data-testid="channel-dialog">
      <v-card class="alert-dialog">
        <v-card-title>{{ editingChannel ? '编辑告警通道' : '新增告警通道' }}</v-card-title>
        <v-card-subtitle>
          通道配置加密存储；已保存的密钥字段显示为 ******，保持原样即表示不修改。
        </v-card-subtitle>
        <v-card-text>
          <div class="form-grid">
            <div class="field">
              <label class="field-label">通道名称</label>
              <v-text-field
                v-model="channelFormView.name"
                data-testid="channel-name"
                variant="outlined"
                density="comfortable"
                hide-details="auto"
                placeholder="例如：数据平台值班群"
              />
            </div>
            <div class="field">
              <label class="field-label">通道类型</label>
              <v-select
                v-model="channelFormView.type"
                data-testid="channel-type"
                :items="channelTypeOptions"
                item-title="title"
                item-value="value"
                variant="outlined"
                density="comfortable"
                hide-details="auto"
                :disabled="!!editingChannel"
                @update:model-value="onChannelTypeChange"
              />
            </div>
            <div class="field">
              <label class="field-label">每分钟发送上限</label>
              <v-text-field
                v-model.number="channelFormView.rateLimitPerMinute"
                type="number"
                min="1"
                max="600"
                variant="outlined"
                density="comfortable"
                hide-details="auto"
                suffix="条"
              />
              <p class="field-help">超过上限的投递会推迟到下一分钟，不会丢失。</p>
            </div>
          </div>

          <div class="config-section">
            <h4>{{ CHANNEL_META[channelFormView.type].label }} 配置</h4>

            <div v-if="channelFormView.type === 'WECOM'" class="form-grid">
              <div class="field span-2">
                <label class="field-label">Webhook 地址前缀</label>
                <v-text-field
                  v-model="channelFormView.config.webhookBase"
                  data-testid="channel-webhook-base"
                  variant="outlined"
                  density="comfortable"
                  hide-details="auto"
                  placeholder="https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key="
                />
                <p class="field-help">默认固定前缀；如使用代理或私有网关可修改。</p>
              </div>
              <div class="field span-2">
                <label class="field-label">机器人 Key</label>
                <v-text-field
                  v-model="channelFormView.config.webhookKey"
                  data-testid="channel-webhook-key"
                  :type="secretFlags.webhookKey ? 'text' : 'password'"
                  variant="outlined"
                  density="comfortable"
                  hide-details="auto"
                  placeholder="只填写 key= 后面的值"
                  :append-inner-icon="
                    secretFlags.webhookKey ? 'mdi-eye-off-outline' : 'mdi-eye-outline'
                  "
                  @click:append-inner="secretFlags.webhookKey = !secretFlags.webhookKey"
                />
                <p class="field-help">
                  {{ maskHint(channelFormView.config.webhookKey) }}
                  完整地址：{{ channelFormView.config.webhookBase
                  }}{{ channelFormView.config.webhookKey }}
                </p>
              </div>
              <div class="field">
                <label class="field-label">@ 手机号</label>
                <v-text-field
                  v-model="channelFormView.config.mentionedMobileList"
                  variant="outlined"
                  density="comfortable"
                  hide-details="auto"
                  placeholder="多个手机号用逗号分隔"
                />
              </div>
              <div class="field switch-field">
                <v-switch
                  v-model="channelFormView.config.mentionAll"
                  label="@ 所有人"
                  color="primary"
                  density="compact"
                  hide-details
                  inset
                />
              </div>
            </div>

            <div v-if="channelFormView.type === 'DINGTALK'" class="form-grid">
              <div class="field span-2">
                <label class="field-label">Webhook 地址前缀</label>
                <v-text-field
                  v-model="channelFormView.config.webhookBase"
                  data-testid="channel-webhook-base"
                  variant="outlined"
                  density="comfortable"
                  hide-details="auto"
                  placeholder="https://oapi.dingtalk.com/robot/send?access_token="
                />
                <p class="field-help">默认固定前缀；如使用代理或私有网关可修改。</p>
              </div>
              <div class="field span-2">
                <label class="field-label">Access Token</label>
                <v-text-field
                  v-model="channelFormView.config.webhookKey"
                  data-testid="channel-webhook-key"
                  :type="secretFlags.webhookKey ? 'text' : 'password'"
                  variant="outlined"
                  density="comfortable"
                  hide-details="auto"
                  placeholder="只填写 access_token= 后面的值"
                  :append-inner-icon="
                    secretFlags.webhookKey ? 'mdi-eye-off-outline' : 'mdi-eye-outline'
                  "
                  @click:append-inner="secretFlags.webhookKey = !secretFlags.webhookKey"
                />
                <p class="field-help">
                  {{ maskHint(channelFormView.config.webhookKey) }}
                  完整地址：{{ channelFormView.config.webhookBase
                  }}{{ channelFormView.config.webhookKey }}
                </p>
              </div>
              <div class="field">
                <label class="field-label">加签密钥</label>
                <v-text-field
                  v-model="channelFormView.config.secret"
                  :type="secretFlags.secret ? 'text' : 'password'"
                  variant="outlined"
                  density="comfortable"
                  hide-details="auto"
                  placeholder="可选，启用加签时填写"
                  :append-inner-icon="
                    secretFlags.secret ? 'mdi-eye-off-outline' : 'mdi-eye-outline'
                  "
                  @click:append-inner="secretFlags.secret = !secretFlags.secret"
                />
              </div>
              <div class="field">
                <label class="field-label">@ 手机号</label>
                <v-text-field
                  v-model="channelFormView.config.atMobiles"
                  variant="outlined"
                  density="comfortable"
                  hide-details="auto"
                  placeholder="多个手机号用逗号分隔"
                />
              </div>
              <div class="field switch-field">
                <v-switch
                  v-model="channelFormView.config.atAll"
                  label="@ 所有人"
                  color="primary"
                  density="compact"
                  hide-details
                  inset
                />
              </div>
            </div>

            <div v-if="channelFormView.type === 'FEISHU'" class="form-grid">
              <div class="field span-2">
                <label class="field-label">Webhook 地址前缀</label>
                <v-text-field
                  v-model="channelFormView.config.webhookBase"
                  data-testid="channel-webhook-base"
                  variant="outlined"
                  density="comfortable"
                  hide-details="auto"
                  placeholder="https://open.feishu.cn/open-apis/bot/v2/hook/"
                />
                <p class="field-help">默认固定前缀；如使用代理或私有网关可修改。</p>
              </div>
              <div class="field span-2">
                <label class="field-label">Hook Token</label>
                <v-text-field
                  v-model="channelFormView.config.webhookKey"
                  data-testid="channel-webhook-key"
                  :type="secretFlags.webhookKey ? 'text' : 'password'"
                  variant="outlined"
                  density="comfortable"
                  hide-details="auto"
                  placeholder="只填写 /hook/ 后面的值"
                  :append-inner-icon="
                    secretFlags.webhookKey ? 'mdi-eye-off-outline' : 'mdi-eye-outline'
                  "
                  @click:append-inner="secretFlags.webhookKey = !secretFlags.webhookKey"
                />
                <p class="field-help">
                  {{ maskHint(channelFormView.config.webhookKey) }}
                  完整地址：{{ channelFormView.config.webhookBase
                  }}{{ channelFormView.config.webhookKey }}
                </p>
              </div>
              <div class="field">
                <label class="field-label">签名密钥</label>
                <v-text-field
                  v-model="channelFormView.config.secret"
                  :type="secretFlags.secret ? 'text' : 'password'"
                  variant="outlined"
                  density="comfortable"
                  hide-details="auto"
                  placeholder="可选，启用签名校验时填写"
                  :append-inner-icon="
                    secretFlags.secret ? 'mdi-eye-off-outline' : 'mdi-eye-outline'
                  "
                  @click:append-inner="secretFlags.secret = !secretFlags.secret"
                />
              </div>
            </div>

            <div v-if="channelFormView.type === 'WEBHOOK'" class="form-grid">
              <div class="field span-2">
                <label class="field-label">回调地址</label>
                <v-text-field
                  v-model="channelFormView.config.url"
                  data-testid="channel-url"
                  :type="secretFlags.url ? 'text' : 'password'"
                  variant="outlined"
                  density="comfortable"
                  hide-details="auto"
                  placeholder="https://alert-gateway.example.com/addax"
                  :append-inner-icon="secretFlags.url ? 'mdi-eye-off-outline' : 'mdi-eye-outline'"
                  @click:append-inner="secretFlags.url = !secretFlags.url"
                />
                <p class="field-help">{{ maskHint(channelFormView.config.url) }}</p>
              </div>
              <div class="field">
                <label class="field-label">请求方法</label>
                <v-select
                  v-model="channelFormView.config.method"
                  :items="['POST', 'PUT', 'PATCH']"
                  variant="outlined"
                  density="comfortable"
                  hide-details="auto"
                />
              </div>
              <div class="field">
                <label class="field-label">超时（毫秒）</label>
                <v-text-field
                  v-model.number="channelFormView.config.timeoutMs"
                  type="number"
                  min="1000"
                  max="15000"
                  variant="outlined"
                  density="comfortable"
                  hide-details="auto"
                />
              </div>
              <div class="field span-2">
                <label class="field-label">请求头（JSON）</label>
                <textarea
                  v-model="headersText"
                  class="json-editor"
                  spellcheck="false"
                  placeholder='{"Authorization": "Bearer ..."}'
                />
              </div>
              <div class="field">
                <label class="field-label">HMAC 密钥</label>
                <v-text-field
                  v-model="channelFormView.config.hmacSecret"
                  :type="secretFlags.hmacSecret ? 'text' : 'password'"
                  variant="outlined"
                  density="comfortable"
                  hide-details="auto"
                  placeholder="可选；启用后请求头 X-Addax-Signature: sha256=..."
                  :append-inner-icon="
                    secretFlags.hmacSecret ? 'mdi-eye-off-outline' : 'mdi-eye-outline'
                  "
                  @click:append-inner="secretFlags.hmacSecret = !secretFlags.hmacSecret"
                />
              </div>
              <div class="field">
                <label class="field-label">成功状态码</label>
                <v-text-field
                  v-model="channelFormView.config.successStatus"
                  variant="outlined"
                  density="comfortable"
                  hide-details="auto"
                  placeholder="200, 201, 202, 204"
                />
              </div>
              <div class="field span-2">
                <label class="field-label">请求体模板（可选）</label>
                <textarea
                  v-model="channelFormView.config.payloadTemplate"
                  class="json-editor json-editor--tall"
                  spellcheck="false"
                  placeholder='{"title":"{{title}}","severity":"{{severity}}","text":"{{text}}"}'
                />
                <p v-pre class="field-help">
                  支持 {{ title }}、{{ text }}、{{ markdown }}、{{ severity }}、{{ eventType }}、{{
                    occurredAt
                  }}、{{ data }}；留空时发送默认 JSON。
                </p>
              </div>
              <div class="field span-2">
                <v-switch
                  v-model="channelFormView.config.allowPrivateNetwork"
                  data-testid="channel-allow-private"
                  label="允许内网地址"
                  color="warning"
                  density="compact"
                  hide-details
                  inset
                />
                <p class="field-help">
                  默认拒绝回环、私有与链路本地地址；仅当 Webhook
                  位于可信内网时开启，操作会写入审计。
                </p>
              </div>
            </div>

            <div v-if="channelFormView.type === 'EMAIL'" class="form-grid">
              <div class="field span-2">
                <label class="field-label">SMTP 服务器</label>
                <v-text-field
                  v-model="channelFormView.config.host"
                  data-testid="channel-email-host"
                  variant="outlined"
                  density="comfortable"
                  hide-details="auto"
                  placeholder="smtp.example.com"
                />
                <p class="field-help">只填写主机名或 IP，端口在右侧单独填写。</p>
              </div>
              <div class="field">
                <label class="field-label">端口</label>
                <v-text-field
                  v-model.number="channelFormView.config.port"
                  data-testid="channel-email-port"
                  type="number"
                  min="1"
                  max="65535"
                  variant="outlined"
                  density="comfortable"
                  hide-details="auto"
                />
              </div>
              <div class="field">
                <label class="field-label">加密方式</label>
                <v-select
                  v-model="channelFormView.config.security"
                  data-testid="channel-email-security"
                  :items="emailSecurityOptions"
                  item-title="title"
                  item-value="value"
                  variant="outlined"
                  density="comfortable"
                  hide-details="auto"
                  @update:model-value="onEmailSecurityChange"
                />
              </div>
              <div class="field">
                <label class="field-label">用户名</label>
                <v-text-field
                  v-model="channelFormView.config.username"
                  data-testid="channel-email-username"
                  variant="outlined"
                  density="comfortable"
                  hide-details="auto"
                  placeholder="不需要认证时留空"
                  autocomplete="off"
                />
              </div>
              <div class="field">
                <label class="field-label">密码</label>
                <v-text-field
                  v-model="channelFormView.config.password"
                  data-testid="channel-email-password"
                  :type="secretFlags.emailPassword ? 'text' : 'password'"
                  variant="outlined"
                  density="comfortable"
                  hide-details="auto"
                  placeholder="不需要认证时留空"
                  autocomplete="new-password"
                  :append-inner-icon="
                    secretFlags.emailPassword ? 'mdi-eye-off-outline' : 'mdi-eye-outline'
                  "
                  @click:append-inner="secretFlags.emailPassword = !secretFlags.emailPassword"
                />
                <p class="field-help">{{ maskHint(channelFormView.config.password) }}</p>
              </div>
              <div class="field">
                <label class="field-label">发件人邮箱</label>
                <v-text-field
                  v-model="channelFormView.config.from"
                  data-testid="channel-email-from"
                  variant="outlined"
                  density="comfortable"
                  hide-details="auto"
                  placeholder="alerts@example.com"
                />
              </div>
              <div class="field">
                <label class="field-label">发件人名称</label>
                <v-text-field
                  v-model="channelFormView.config.fromName"
                  variant="outlined"
                  density="comfortable"
                  hide-details="auto"
                  placeholder="Addax Admin"
                />
              </div>
              <div class="field span-2">
                <label class="field-label">收件人</label>
                <v-text-field
                  v-model="channelFormView.config.to"
                  data-testid="channel-email-to"
                  variant="outlined"
                  density="comfortable"
                  hide-details="auto"
                  placeholder="ops@example.com，多个邮箱用逗号分隔"
                />
              </div>
              <div class="field span-2">
                <label class="field-label">抄送</label>
                <v-text-field
                  v-model="channelFormView.config.cc"
                  data-testid="channel-email-cc"
                  variant="outlined"
                  density="comfortable"
                  hide-details="auto"
                  placeholder="可选，多个邮箱用逗号分隔"
                />
              </div>
              <div class="field">
                <label class="field-label">主题前缀</label>
                <v-text-field
                  v-model="channelFormView.config.subjectPrefix"
                  variant="outlined"
                  density="comfortable"
                  hide-details="auto"
                  placeholder="可选，例如 [Addax]"
                />
              </div>
              <div class="field">
                <label class="field-label">超时（毫秒）</label>
                <v-text-field
                  v-model.number="channelFormView.config.timeoutMs"
                  type="number"
                  min="1000"
                  max="30000"
                  variant="outlined"
                  density="comfortable"
                  hide-details="auto"
                />
              </div>
              <div class="field span-2">
                <v-switch
                  v-model="channelFormView.config.allowPrivateNetwork"
                  data-testid="channel-email-private"
                  label="允许内网 SMTP 服务器"
                  color="warning"
                  density="compact"
                  hide-details
                  inset
                />
                <p class="field-help">
                  默认拒绝回环、私有与链路本地地址；企业内网邮件中继需开启，操作会写入审计。
                </p>
              </div>
            </div>
          </div>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" :disabled="channelSaving" @click="channelDialog = false"
            >取消</v-btn
          >
          <v-btn
            color="primary"
            variant="flat"
            prepend-icon="mdi-content-save-outline"
            data-testid="channel-save"
            :loading="channelSaving"
            @click="saveChannel"
          >
            保存通道
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- ── 规则编辑 ───────────────────────────────────────────── -->
    <v-dialog v-model="ruleDialog" max-width="680" persistent data-testid="rule-dialog">
      <v-card class="alert-dialog">
        <v-card-title>{{ editingRule ? '编辑告警规则' : '新增告警规则' }}</v-card-title>
        <v-card-subtitle>
          没有规则匹配时，事件默认发送到所有启用通道；规则用于收窄范围与指定通道。
        </v-card-subtitle>
        <v-card-text>
          <div class="form-grid">
            <div class="field span-2">
              <label class="field-label">规则名称</label>
              <v-text-field
                v-model="ruleFormView.name"
                data-testid="rule-name"
                variant="outlined"
                density="comfortable"
                hide-details="auto"
                placeholder="例如：核心源采集失败升级"
              />
            </div>
            <div class="field">
              <label class="field-label">事件类型</label>
              <v-select
                v-model="ruleFormView.eventType"
                data-testid="rule-event-type"
                :items="eventTypeOptions"
                item-title="title"
                item-value="value"
                variant="outlined"
                density="comfortable"
                hide-details="auto"
              />
            </div>
            <div class="field">
              <label class="field-label">级别</label>
              <v-select
                v-model="ruleFormView.severity"
                :items="severityOptions"
                item-title="title"
                item-value="value"
                variant="outlined"
                density="comfortable"
                hide-details="auto"
              />
            </div>
            <div class="field">
              <label class="field-label">范围</label>
              <v-select
                v-model="ruleFormView.scopeType"
                :items="scopeOptions"
                item-title="title"
                item-value="value"
                variant="outlined"
                density="comfortable"
                hide-details="auto"
              />
            </div>
            <div v-if="ruleFormView.scopeType !== 'ALL'" class="field">
              <label class="field-label">范围值</label>
              <v-text-field
                v-model="ruleFormView.scopeValue"
                variant="outlined"
                density="comfortable"
                hide-details="auto"
                :placeholder="
                  ruleFormView.scopeType === 'SOURCE'
                    ? '源数据库名，例如 sales'
                    : '采集表 ID 或 sourceDb.sourceTable'
                "
              />
            </div>
            <div class="field span-2">
              <label class="field-label">发送通道</label>
              <v-select
                v-model="ruleFormView.channelIds"
                data-testid="rule-channels"
                :items="channelSelectItems"
                item-title="title"
                item-value="id"
                multiple
                chips
                closable-chips
                variant="outlined"
                density="comfortable"
                hide-details="auto"
                placeholder="选择接收该规则事件的通道"
              />
            </div>
            <div class="field">
              <label class="field-label">冷却时间（秒）</label>
              <v-text-field
                v-model.number="ruleFormView.cooldownSecs"
                type="number"
                min="0"
                max="86400"
                variant="outlined"
                density="comfortable"
                hide-details="auto"
              />
            </div>
            <div class="field">
              <label class="field-label">聚合窗口（秒）</label>
              <v-text-field
                v-model.number="ruleFormView.groupWindowSecs"
                type="number"
                min="0"
                max="86400"
                variant="outlined"
                density="comfortable"
                hide-details="auto"
              />
            </div>
            <div class="field span-2">
              <v-switch
                v-model="ruleFormView.enabled"
                label="启用规则"
                color="success"
                density="compact"
                hide-details
                inset
              />
            </div>
          </div>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" :disabled="ruleSaving" @click="ruleDialog = false">取消</v-btn>
          <v-btn
            color="primary"
            variant="flat"
            prepend-icon="mdi-content-save-outline"
            data-testid="rule-save"
            :loading="ruleSaving"
            @click="saveRule"
          >
            保存规则
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- ── 静默编辑 ───────────────────────────────────────────── -->
    <v-dialog v-model="silenceDialog" max-width="560" persistent data-testid="silence-dialog">
      <v-card class="alert-dialog">
        <v-card-title>新增静默窗口</v-card-title>
        <v-card-subtitle>窗口内命中的事件会记录为 SILENCED，不发送外部消息。</v-card-subtitle>
        <v-card-text>
          <div class="form-grid">
            <div class="field">
              <label class="field-label">范围</label>
              <v-select
                v-model="silenceFormView.scopeType"
                :items="scopeOptions"
                item-title="title"
                item-value="value"
                variant="outlined"
                density="comfortable"
                hide-details="auto"
              />
            </div>
            <div v-if="silenceFormView.scopeType !== 'ALL'" class="field">
              <label class="field-label">范围值</label>
              <v-text-field
                v-model="silenceFormView.scopeValue"
                variant="outlined"
                density="comfortable"
                hide-details="auto"
                :placeholder="
                  silenceFormView.scopeType === 'SOURCE'
                    ? '源数据库名'
                    : '采集表 ID 或 sourceDb.sourceTable'
                "
              />
            </div>
            <div class="field">
              <label class="field-label">开始时间</label>
              <v-text-field
                v-model="silenceFormView.startsAt"
                type="datetime-local"
                variant="outlined"
                density="comfortable"
                hide-details="auto"
              />
            </div>
            <div class="field">
              <label class="field-label">结束时间</label>
              <v-text-field
                v-model="silenceFormView.endsAt"
                type="datetime-local"
                variant="outlined"
                density="comfortable"
                hide-details="auto"
              />
            </div>
            <div class="field span-2">
              <label class="field-label">原因</label>
              <v-text-field
                v-model="silenceFormView.reason"
                variant="outlined"
                density="comfortable"
                hide-details="auto"
                placeholder="例如：数据库维护窗口"
              />
            </div>
          </div>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" :disabled="silenceSaving" @click="silenceDialog = false"
            >取消</v-btn
          >
          <v-btn
            color="primary"
            variant="flat"
            prepend-icon="mdi-bell-off-outline"
            :loading="silenceSaving"
            @click="saveSilence"
          >
            创建静默
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- ── 投递详情 ───────────────────────────────────────────── -->
    <v-dialog v-model="deliveryDialog" max-width="640">
      <v-card v-if="selectedDelivery" class="alert-dialog">
        <v-card-title>投递详情 #{{ selectedDelivery.id }}</v-card-title>
        <v-card-subtitle>
          {{ selectedDelivery.eventTitle || `事件 #${selectedDelivery.eventId}` }}
        </v-card-subtitle>
        <v-card-text>
          <v-table density="compact" class="detail-table">
            <tbody>
              <tr>
                <th>通道</th>
                <td>{{ selectedDelivery.channelName || '未分配' }}</td>
              </tr>
              <tr>
                <th>状态</th>
                <td>
                  <v-chip
                    size="small"
                    variant="tonal"
                    :color="deliveryStatusColor(selectedDelivery.status)"
                  >
                    {{ deliveryStatusLabel(selectedDelivery.status) }}
                  </v-chip>
                </td>
              </tr>
              <tr>
                <th>尝试次数</th>
                <td>{{ selectedDelivery.attempts }} / 5</td>
              </tr>
              <tr>
                <th>创建 / 发送</th>
                <td>
                  {{ formatTime(selectedDelivery.createdAt) }} /
                  {{ selectedDelivery.sentAt ? formatTime(selectedDelivery.sentAt) : '—' }}
                </td>
              </tr>
              <tr>
                <th>下次重试</th>
                <td>
                  {{
                    selectedDelivery.nextRetryAt ? formatTime(selectedDelivery.nextRetryAt) : '—'
                  }}
                </td>
              </tr>
              <tr>
                <th>响应状态</th>
                <td>{{ selectedDelivery.responseCode ?? '—' }}</td>
              </tr>
            </tbody>
          </v-table>
          <div v-if="selectedDelivery.responseBody" class="detail-block">
            <h4>响应内容</h4>
            <pre>{{ selectedDelivery.responseBody }}</pre>
          </div>
          <div v-if="selectedDelivery.lastError" class="detail-block detail-block--error">
            <h4>错误信息</h4>
            <pre>{{ selectedDelivery.lastError }}</pre>
          </div>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="deliveryDialog = false">关闭</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <ConfirmDialog
      v-model="confirmView.open"
      :title="confirmView.title"
      :message="confirmView.message"
      :confirm-text="confirmView.confirmText"
      :loading="confirmView.loading"
      @confirm="runConfirm"
      @cancel="confirmView.open = false"
    />
  </div>
</template>

<script setup lang="ts">
  import PageHeader from '@/components/PageHeader.vue';
  import ConfirmDialog from '@/components/ConfirmDialog.vue';
  import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
  import dayjs from 'dayjs';
  import { notify } from '@/stores/notifier';
  import alertService, {
    type AlertChannel,
    type AlertChannelRequest,
    type AlertChannelType,
    type AlertDelivery,
    type AlertEventType,
    type AlertRule,
    type AlertRuleRequest,
    type AlertScopeType,
    type AlertSeverity,
    type AlertSilence,
    type AlertSummary,
  } from '@/service/alert-service';

  const MASK = '******';

  const CHANNEL_META: Record<AlertChannelType, { label: string; icon: string; color: string }> = {
    WECOM: { label: '企业微信', icon: 'mdi-wechat', color: '#07c160' },
    DINGTALK: { label: '钉钉', icon: 'mdi-chat-processing-outline', color: '#1677ff' },
    FEISHU: { label: '飞书', icon: 'mdi-send-circle-outline', color: '#3370ff' },
    WEBHOOK: { label: 'Webhook', icon: 'mdi-web', color: '#7c3aed' },
    EMAIL: { label: '邮件', icon: 'mdi-email-outline', color: '#0ea5e9' },
  };

  const channelTypeOptions = (Object.keys(CHANNEL_META) as AlertChannelType[]).map(value => ({
    value,
    title: CHANNEL_META[value].label,
  }));

  const eventTypeOptions: Array<{ value: AlertEventType; title: string }> = [
    { value: 'COLLECT_FAILURE', title: '采集失败' },
    { value: 'COLLECT_RECOVERY', title: '采集恢复' },
    { value: 'SECURITY', title: '安全告警' },
    { value: 'SYSTEM', title: '系统告警' },
  ];

  const severityOptions: Array<{ value: AlertSeverity; title: string }> = [
    { value: 'P1', title: 'P1 · 紧急' },
    { value: 'P2', title: 'P2 · 重要' },
    { value: 'P3', title: 'P3 · 提示' },
  ];

  const scopeOptions: Array<{ value: AlertScopeType; title: string }> = [
    { value: 'ALL', title: '全部' },
    { value: 'SOURCE', title: '按数据源' },
    { value: 'TABLE', title: '按采集表' },
  ];

  const emailSecurityOptions = [
    { value: 'STARTTLS', title: 'STARTTLS（587）' },
    { value: 'SSL', title: 'SSL/TLS（465）' },
    { value: 'NONE', title: '不加密（25）' },
  ];
  const EMAIL_DEFAULT_PORT: Record<string, number> = { NONE: 25, STARTTLS: 587, SSL: 465 };

  const deliveryStatusOptions = [
    { value: 'PENDING', title: '等待发送' },
    { value: 'SENDING', title: '发送中' },
    { value: 'SENT', title: '已发送' },
    { value: 'FAILED', title: '待重试' },
    { value: 'DEAD', title: '死信' },
    { value: 'SKIPPED_NO_CHANNEL', title: '无可用通道' },
    { value: 'SILENCED', title: '已静默' },
  ];

  const ruleHeaders = [
    { title: '规则', key: 'name', minWidth: 220 },
    { title: '事件', key: 'eventType', width: 130 },
    { title: '级别', key: 'severity', width: 90 },
    { title: '通道', key: 'channels', minWidth: 180 },
    { title: '冷却 / 聚合', key: 'cooldown', width: 140 },
    { title: '启用', key: 'enabled', width: 90, sortable: false },
    { title: '操作', key: 'actions', width: 170, sortable: false },
  ];

  const deliveryHeaders = [
    { title: '时间', key: 'createdAt', width: 165 },
    { title: '告警事件', key: 'eventTitle', minWidth: 240 },
    { title: '级别', key: 'severity', width: 80 },
    { title: '通道', key: 'channelName', width: 140 },
    { title: '状态', key: 'status', width: 120 },
    { title: '尝试', key: 'attempts', width: 80 },
    { title: '响应', key: 'responseCode', width: 80 },
    { title: '操作', key: 'actions', width: 140, sortable: false },
  ];

  const silenceHeaders = [
    { title: '范围', key: 'scopeType', minWidth: 160 },
    { title: '窗口', key: 'window', minWidth: 220 },
    { title: '状态', key: 'active', width: 100 },
    { title: '原因', key: 'reason', minWidth: 200 },
    { title: '创建人', key: 'createdBy', width: 120 },
    { title: '操作', key: 'actions', width: 100, sortable: false },
  ];

  const tab = ref<'channels' | 'rules' | 'deliveries' | 'silences'>('channels');
  const loading = ref(true);
  const loadError = ref(false);
  const testingEvent = ref(false);

  const channels = ref<AlertChannel[]>([]);
  const rules = ref<AlertRule[]>([]);
  const summary = ref<AlertSummary>({ channelCount: 0, enabledChannelCount: 0, paused: true });

  const channelSelectItems = computed(() =>
    channels.value.map(channel => ({
      id: channel.id,
      title: `${channel.name}（${channel.typeLabel}${channel.enabled ? '' : ' · 已停用'}）`,
    }))
  );

  const channelNameMap = computed(() => {
    const map = new Map<number, string>();
    channels.value.forEach(channel => map.set(channel.id, channel.name));
    return map;
  });

  const formatTime = (value: string | null | undefined) =>
    value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '—';

  const eventTypeLabel = (value: string | null | undefined) =>
    eventTypeOptions.find(item => item.value === value)?.title || value || '—';

  const severityColor = (value: string | null | undefined) => {
    if (value === 'P1') return 'error';
    if (value === 'P2') return 'warning';
    if (value === 'P3') return 'info';
    return undefined;
  };

  const deliveryStatusLabel = (status: string) =>
    deliveryStatusOptions.find(item => item.value === status)?.title || status;

  const deliveryStatusColor = (status: string) => {
    switch (status) {
      case 'SENT':
        return 'success';
      case 'PENDING':
      case 'SENDING':
        return 'info';
      case 'FAILED':
        return 'warning';
      case 'DEAD':
        return 'error';
      case 'SILENCED':
        return 'secondary';
      default:
        return undefined;
    }
  };

  const scopeLabel = (scopeType: string | null, scopeValue: string | null) => {
    if (!scopeType || scopeType === 'ALL') return '全部范围';
    if (scopeType === 'SOURCE') return `源 · ${scopeValue || '—'}`;
    if (scopeType === 'TABLE') return `表 · ${scopeValue || '—'}`;
    return scopeType;
  };

  const channelNames = (ids: number[] | null) => {
    if (!ids || ids.length === 0) return '默认（全部启用通道）';
    return ids.map(id => channelNameMap.value.get(id) || `#${id}`).join('、');
  };

  const maskHint = (value: unknown) =>
    value === MASK ? '已配置，显示为 ******；保持原样即可不修改。' : '保存后密钥字段不会回显。';

  const loadChannels = async () => {
    const [channelList, channelSummary] = await Promise.all([
      alertService.listChannels(),
      alertService.getSummary(),
    ]);
    channels.value = channelList;
    summary.value = channelSummary;
  };

  const loadRules = async () => {
    rules.value = await alertService.listRules();
  };

  const loadAll = async () => {
    loading.value = true;
    loadError.value = false;
    try {
      await Promise.all([loadChannels(), loadRules()]);
    } catch (error: any) {
      loadError.value = true;
      notify(`加载告警配置失败：${error?.message || error}`, 'error');
    } finally {
      loading.value = false;
    }
  };

  // ── 通道表单 ─────────────────────────────────────────────────
  const channelDialog = ref(false);
  const channelSaving = ref(false);
  const editingChannel = ref<AlertChannel | null>(null);
  const testingChannelId = ref<number | null>(null);
  const togglingChannelId = ref<number | null>(null);
  const showSecret = reactive<Record<string, boolean>>({});
  const headersText = ref('{}');

  type ImChannelType = 'WECOM' | 'DINGTALK' | 'FEISHU';

  // Vendor webhook prefixes are stable; the operator only fills the credential
  // segment. The prefix stays editable for proxies or private gateways.
  const IM_WEBHOOK: Record<
    ImChannelType,
    { base: string; keyLabel: string; keyPlaceholder: string; marker: string }
  > = {
    WECOM: {
      base: 'https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=',
      keyLabel: '机器人 Key',
      keyPlaceholder: '只填写 key= 后面的值',
      marker: 'key=',
    },
    DINGTALK: {
      base: 'https://oapi.dingtalk.com/robot/send?access_token=',
      keyLabel: 'Access Token',
      keyPlaceholder: '只填写 access_token= 后面的值',
      marker: 'access_token=',
    },
    FEISHU: {
      base: 'https://open.feishu.cn/open-apis/bot/v2/hook/',
      keyLabel: 'Hook Token',
      keyPlaceholder: '只填写 /hook/ 后面的值',
      marker: '/hook/',
    },
  };

  const emptyConfig = (type: AlertChannelType): Record<string, any> => {
    switch (type) {
      case 'WECOM':
        return {
          webhookBase: IM_WEBHOOK.WECOM.base,
          webhookKey: '',
          mentionAll: false,
          mentionedMobileList: '',
        };
      case 'DINGTALK':
        return {
          webhookBase: IM_WEBHOOK.DINGTALK.base,
          webhookKey: '',
          secret: '',
          atMobiles: '',
          atAll: false,
        };
      case 'FEISHU':
        return { webhookBase: IM_WEBHOOK.FEISHU.base, webhookKey: '', secret: '' };
      case 'EMAIL':
        return {
          host: '',
          port: 587,
          security: 'STARTTLS',
          username: '',
          password: '',
          from: '',
          fromName: '',
          to: '',
          cc: '',
          subjectPrefix: '',
          timeoutMs: 10000,
          allowPrivateNetwork: false,
        };
      default:
        return {
          url: '',
          method: 'POST',
          headers: {},
          hmacSecret: '',
          payloadTemplate: '',
          successStatus: '200,201,202,204',
          timeoutMs: 5000,
          allowPrivateNetwork: false,
        };
    }
  };

  const defaultRate = (type: AlertChannelType) =>
    ({ WECOM: 20, DINGTALK: 20, FEISHU: 100, WEBHOOK: 60, EMAIL: 30 })[type];

  const channelForm = reactive({
    name: '',
    type: 'WECOM' as AlertChannelType,
    rateLimitPerMinute: 20,
    originalWebhookBase: '',
    config: emptyConfig('WECOM') as Record<string, any>,
  });

  // Only a deliberate security-mode change moves the port, and only when the
  // current value is empty or one of the standard defaults.
  const onEmailSecurityChange = (value: string) => {
    const currentPort = Number(channelForm.config?.port);
    if (!currentPort || [25, 465, 587].includes(currentPort)) {
      channelForm.config.port = EMAIL_DEFAULT_PORT[value] || 587;
    }
  };

  const splitWebhookUrl = (type: ImChannelType, url: string) => {
    const meta = IM_WEBHOOK[type];
    if (!url) {
      return { base: meta.base, key: '' };
    }
    if (url === MASK) {
      return { base: meta.base, key: MASK };
    }
    if (url.startsWith(meta.base)) {
      return { base: meta.base, key: url.slice(meta.base.length) };
    }
    const markerIndex = url.indexOf(meta.marker);
    if (markerIndex >= 0) {
      const end = markerIndex + meta.marker.length;
      return { base: url.slice(0, end), key: url.slice(end) };
    }
    const slash = url.lastIndexOf('/');
    if (slash > 0 && slash < url.length - 1) {
      return { base: url.slice(0, slash + 1), key: url.slice(slash + 1) };
    }
    return { base: url, key: '' };
  };

  const joinList = (value: unknown) =>
    Array.isArray(value) ? value.join(', ') : String(value ?? '');

  const splitList = (value: unknown): string[] =>
    String(value ?? '')
      .split(',')
      .map(item => item.trim())
      .filter(Boolean);

  const openChannelCreate = () => {
    editingChannel.value = null;
    channelForm.name = '';
    channelForm.type = 'WECOM';
    channelForm.rateLimitPerMinute = defaultRate('WECOM');
    channelForm.originalWebhookBase = '';
    channelForm.config = emptyConfig('WECOM');
    headersText.value = '{}';
    channelDialog.value = true;
  };

  const openChannelEdit = (channel: AlertChannel) => {
    editingChannel.value = channel;
    channelForm.name = channel.name;
    channelForm.type = channel.type;
    channelForm.rateLimitPerMinute = channel.rateLimitPerMinute;
    const config = JSON.parse(JSON.stringify(channel.config || {}));
    if (channel.type === 'WECOM' || channel.type === 'DINGTALK' || channel.type === 'FEISHU') {
      const split = splitWebhookUrl(channel.type, String(config.webhookUrl || ''));
      config.webhookBase = split.base;
      config.webhookKey = split.key;
      channelForm.originalWebhookBase = split.base;
      delete config.webhookUrl;
    }
    if (channel.type === 'WECOM') {
      config.mentionedMobileList = joinList(config.mentionedMobileList);
    }
    if (channel.type === 'DINGTALK') {
      config.atMobiles = joinList(config.atMobiles);
    }
    if (channel.type === 'WEBHOOK') {
      config.successStatus = joinList(config.successStatus);
      headersText.value = JSON.stringify(config.headers || {}, null, 2);
    }
    if (channel.type === 'EMAIL') {
      // Older API clients may have stored recipient arrays; the form uses one
      // comma-separated field so editing does not rewrite the stored shape.
      config.to = joinList(config.to);
      config.cc = joinList(config.cc);
      config.password = config.password ?? '';
      config.security = config.security || 'STARTTLS';
      config.port = Number(config.port) || EMAIL_DEFAULT_PORT[config.security];
    }
    channelForm.config = config;
    channelDialog.value = true;
  };

  const onChannelTypeChange = (value: AlertChannelType) => {
    if (editingChannel.value) return;
    channelForm.config = emptyConfig(value);
    channelForm.rateLimitPerMinute = defaultRate(value);
    channelForm.originalWebhookBase = '';
    headersText.value = '{}';
  };

  const buildChannelConfig = (): Record<string, any> => {
    const config = JSON.parse(JSON.stringify(channelForm.config || {}));
    if (
      channelForm.type === 'WECOM' ||
      channelForm.type === 'DINGTALK' ||
      channelForm.type === 'FEISHU'
    ) {
      const meta = IM_WEBHOOK[channelForm.type];
      const base = String(config.webhookBase || '').trim();
      const key = String(config.webhookKey || '').trim();
      delete config.webhookBase;
      delete config.webhookKey;
      if (!base) {
        throw new Error('Webhook 地址前缀不能为空');
      }
      if (key === MASK) {
        if (base !== channelForm.originalWebhookBase) {
          throw new Error('修改 Webhook 地址前缀后，请重新填写 Key/Token');
        }
        // The sentinel tells the backend to keep the stored URL untouched.
        config.webhookUrl = MASK;
      } else {
        if (!key && base === meta.base) {
          throw new Error('请填写 Key/Token');
        }
        config.webhookUrl = `${base}${key}`;
      }
    }
    if (channelForm.type === 'WECOM') {
      config.mentionedMobileList = splitList(config.mentionedMobileList);
      config.mentionAll = !!config.mentionAll;
    }
    if (channelForm.type === 'DINGTALK') {
      config.atMobiles = splitList(config.atMobiles);
      config.atAll = !!config.atAll;
    }
    if (channelForm.type === 'WEBHOOK') {
      try {
        config.headers = headersText.value.trim() ? JSON.parse(headersText.value) : {};
      } catch (error) {
        throw new Error('请求头必须是合法的 JSON 对象');
      }
      config.successStatus = splitList(config.successStatus)
        .map(value => Number(value))
        .filter(value => Number.isFinite(value));
      if (config.successStatus.length === 0) {
        config.successStatus = [200, 201, 202, 204];
      }
      config.timeoutMs = Number(config.timeoutMs) || 5000;
      config.allowPrivateNetwork = !!config.allowPrivateNetwork;
    }
    if (channelForm.type === 'EMAIL') {
      config.host = String(config.host || '').trim();
      config.security = String(config.security || 'STARTTLS').toUpperCase();
      config.port = Number(config.port) || EMAIL_DEFAULT_PORT[config.security] || 587;
      config.username = String(config.username || '').trim();
      config.password = String(config.password || '');
      config.from = String(config.from || '').trim();
      config.fromName = String(config.fromName || '').trim();
      config.to = String(config.to || '').trim();
      config.cc = String(config.cc || '').trim();
      config.subjectPrefix = String(config.subjectPrefix || '').trim();
      config.timeoutMs = Number(config.timeoutMs) || 10000;
      config.allowPrivateNetwork = !!config.allowPrivateNetwork;
      if (!config.host) throw new Error('请填写 SMTP 服务器地址');
      if (!config.from) throw new Error('请填写发件人邮箱');
      if (!config.to) throw new Error('请填写收件人');
      if (config.username && !config.password) {
        throw new Error('填写 SMTP 用户名时必须同时填写密码');
      }
      if (!config.username && config.password) {
        throw new Error('填写 SMTP 密码时必须同时填写用户名');
      }
    }
    return config;
  };

  const saveChannel = async () => {
    if (!channelForm.name.trim()) {
      notify('请填写通道名称', 'warning');
      return;
    }
    let config: Record<string, any>;
    try {
      config = buildChannelConfig();
    } catch (error: any) {
      notify(error.message, 'error');
      return;
    }
    const payload: AlertChannelRequest = {
      name: channelForm.name.trim(),
      type: channelForm.type,
      config,
      rateLimitPerMinute: Number(channelForm.rateLimitPerMinute) || defaultRate(channelForm.type),
    };
    channelSaving.value = true;
    try {
      if (editingChannel.value) {
        await alertService.updateChannel(editingChannel.value.id, payload);
        notify('通道已更新；如配置有变化，需要重新测试后才能启用', 'success');
      } else {
        await alertService.createChannel(payload);
        notify('通道已保存，请先测试发送成功后再启用', 'success');
      }
      channelDialog.value = false;
      await loadChannels();
    } catch (error: any) {
      notify(`保存通道失败：${error?.message || error}`, 'error');
    } finally {
      channelSaving.value = false;
    }
  };

  const testChannel = async (channel: AlertChannel) => {
    testingChannelId.value = channel.id;
    try {
      const result = await alertService.testChannel(channel.id);
      notify(
        result.ok ? `测试消息已送达（${result.latencyMs}ms）` : `测试失败：${result.message}`,
        result.ok ? 'success' : 'error'
      );
      await loadChannels();
    } catch (error: any) {
      notify(`测试发送失败：${error?.message || error}`, 'error');
    } finally {
      testingChannelId.value = null;
    }
  };

  const toggleChannel = async (channel: AlertChannel, enabled: boolean) => {
    if (enabled && !channel.lastTestOk) {
      notify('请先完成测试发送并确认成功后再启用通道', 'warning');
      return;
    }
    togglingChannelId.value = channel.id;
    try {
      await alertService.updateChannel(channel.id, {
        name: channel.name,
        type: channel.type,
        config: channel.config,
        rateLimitPerMinute: channel.rateLimitPerMinute,
        enabled,
      });
      notify(enabled ? '通道已启用' : '通道已停用', 'success');
      await loadChannels();
    } catch (error: any) {
      notify(`更新通道状态失败：${error?.message || error}`, 'error');
    } finally {
      togglingChannelId.value = null;
    }
  };

  // ── 规则表单 ─────────────────────────────────────────────────
  const ruleDialog = ref(false);
  const ruleSaving = ref(false);
  const editingRule = ref<AlertRule | null>(null);
  const ruleForm = reactive({
    name: '',
    eventType: 'COLLECT_FAILURE' as AlertEventType,
    severity: 'P1' as AlertSeverity,
    scopeType: 'ALL' as AlertScopeType,
    scopeValue: '',
    channelIds: [] as number[],
    cooldownSecs: 300,
    groupWindowSecs: 60,
    enabled: true,
  });

  const openRuleCreate = () => {
    editingRule.value = null;
    ruleForm.name = '';
    ruleForm.eventType = 'COLLECT_FAILURE';
    ruleForm.severity = 'P1';
    ruleForm.scopeType = 'ALL';
    ruleForm.scopeValue = '';
    ruleForm.channelIds = [];
    ruleForm.cooldownSecs = 300;
    ruleForm.groupWindowSecs = 60;
    ruleForm.enabled = true;
    ruleDialog.value = true;
  };

  const openRuleEdit = (rule: AlertRule) => {
    editingRule.value = rule;
    ruleForm.name = rule.name;
    ruleForm.eventType = rule.eventType;
    ruleForm.severity = rule.severity;
    ruleForm.scopeType = rule.scopeType;
    ruleForm.scopeValue = rule.scopeValue || '';
    ruleForm.channelIds = [...(rule.channelIds || [])];
    ruleForm.cooldownSecs = rule.cooldownSecs;
    ruleForm.groupWindowSecs = rule.groupWindowSecs;
    ruleForm.enabled = rule.enabled;
    ruleDialog.value = true;
  };

  const rulePayload = (enabled?: boolean): AlertRuleRequest => ({
    name: ruleForm.name.trim(),
    eventType: ruleForm.eventType,
    severity: ruleForm.severity,
    scopeType: ruleForm.scopeType,
    scopeValue: ruleForm.scopeType === 'ALL' ? null : ruleForm.scopeValue.trim(),
    channelIds: ruleForm.channelIds,
    cooldownSecs: Number(ruleForm.cooldownSecs) || 0,
    groupWindowSecs: Number(ruleForm.groupWindowSecs) || 0,
    enabled: enabled ?? ruleForm.enabled,
  });

  const saveRule = async () => {
    if (!ruleForm.name.trim()) {
      notify('请填写规则名称', 'warning');
      return;
    }
    if (ruleForm.scopeType !== 'ALL' && !ruleForm.scopeValue.trim()) {
      notify('范围不是“全部”时必须填写范围值', 'warning');
      return;
    }
    ruleSaving.value = true;
    try {
      if (editingRule.value) {
        await alertService.updateRule(editingRule.value.id, rulePayload());
        notify('规则已更新', 'success');
      } else {
        await alertService.createRule(rulePayload());
        notify('规则已创建', 'success');
      }
      ruleDialog.value = false;
      await loadRules();
    } catch (error: any) {
      notify(`保存规则失败：${error?.message || error}`, 'error');
    } finally {
      ruleSaving.value = false;
    }
  };

  const toggleRule = async (rule: AlertRule, enabled: boolean) => {
    try {
      await alertService.updateRule(rule.id, {
        name: rule.name,
        eventType: rule.eventType,
        severity: rule.severity,
        scopeType: rule.scopeType,
        scopeValue: rule.scopeValue,
        channelIds: rule.channelIds,
        cooldownSecs: rule.cooldownSecs,
        groupWindowSecs: rule.groupWindowSecs,
        enabled,
      });
      notify(enabled ? '规则已启用' : '规则已停用', 'success');
      await loadRules();
    } catch (error: any) {
      notify(`更新规则状态失败：${error?.message || error}`, 'error');
    }
  };

  // ── 投递记录 ─────────────────────────────────────────────────
  const deliveryFilters = reactive({ status: '', channelId: null as number | null });
  const deliveries = ref<AlertDelivery[]>([]);
  const deliveryLoading = ref(false);
  const deliveryTotal = ref(0);
  const deliveryPage = ref(1);
  const deliveryPageSize = ref(20);
  const retryingDeliveryId = ref<number | null>(null);
  const deliveryDialog = ref(false);
  const selectedDelivery = ref<AlertDelivery | null>(null);

  const loadDeliveries = async (options?: { page?: number; itemsPerPage?: number }) => {
    if (options?.page) deliveryPage.value = options.page;
    if (options?.itemsPerPage) deliveryPageSize.value = options.itemsPerPage;
    deliveryLoading.value = true;
    try {
      const result = await alertService.listDeliveries({
        page: deliveryPage.value - 1,
        pageSize: deliveryPageSize.value,
        status: deliveryFilters.status || undefined,
        channelId: deliveryFilters.channelId || undefined,
      });
      deliveries.value = result.content;
      deliveryTotal.value = result.totalElements;
    } catch (error: any) {
      notify(`加载投递记录失败：${error?.message || error}`, 'error');
    } finally {
      deliveryLoading.value = false;
    }
  };

  const searchDeliveries = () => {
    deliveryPage.value = 1;
    loadDeliveries();
  };

  const retryDelivery = async (delivery: AlertDelivery) => {
    retryingDeliveryId.value = delivery.id;
    try {
      await alertService.retryDelivery(delivery.id);
      notify('已重新加入投递队列', 'success');
      await loadDeliveries();
    } catch (error: any) {
      notify(`重试失败：${error?.message || error}`, 'error');
    } finally {
      retryingDeliveryId.value = null;
    }
  };

  const openDeliveryDetail = (delivery: AlertDelivery) => {
    selectedDelivery.value = delivery;
    deliveryDialog.value = true;
  };

  // ── 静默 ─────────────────────────────────────────────────────
  const silences = ref<AlertSilence[]>([]);
  const silenceLoading = ref(false);
  const silenceDialog = ref(false);
  const silenceSaving = ref(false);
  const silenceForm = reactive({
    scopeType: 'ALL' as AlertScopeType,
    scopeValue: '',
    startsAt: '',
    endsAt: '',
    reason: '',
  });

  const toLocalInput = (date: Date) => {
    const pad = (value: number) => String(value).padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(
      date.getHours()
    )}:${pad(date.getMinutes())}`;
  };

  const loadSilences = async () => {
    silenceLoading.value = true;
    try {
      silences.value = await alertService.listSilences();
    } catch (error: any) {
      notify(`加载静默窗口失败：${error?.message || error}`, 'error');
    } finally {
      silenceLoading.value = false;
    }
  };

  const openSilenceCreate = () => {
    const now = new Date();
    const later = new Date(now.getTime() + 2 * 60 * 60 * 1000);
    silenceForm.scopeType = 'ALL';
    silenceForm.scopeValue = '';
    silenceForm.startsAt = toLocalInput(now);
    silenceForm.endsAt = toLocalInput(later);
    silenceForm.reason = '';
    silenceDialog.value = true;
  };

  const saveSilence = async () => {
    if (!silenceForm.startsAt || !silenceForm.endsAt) {
      notify('请选择静默开始和结束时间', 'warning');
      return;
    }
    if (silenceForm.scopeType !== 'ALL' && !silenceForm.scopeValue.trim()) {
      notify('范围不是“全部”时必须填写范围值', 'warning');
      return;
    }
    silenceSaving.value = true;
    try {
      await alertService.createSilence({
        scopeType: silenceForm.scopeType,
        scopeValue: silenceForm.scopeType === 'ALL' ? null : silenceForm.scopeValue.trim(),
        startsAt: new Date(silenceForm.startsAt).toISOString(),
        endsAt: new Date(silenceForm.endsAt).toISOString(),
        reason: silenceForm.reason.trim(),
      });
      notify('静默窗口已创建', 'success');
      silenceDialog.value = false;
      await loadSilences();
    } catch (error: any) {
      notify(`创建静默失败：${error?.message || error}`, 'error');
    } finally {
      silenceSaving.value = false;
    }
  };

  // ── 确认弹窗 ─────────────────────────────────────────────────
  const confirm = reactive({
    open: false,
    title: '',
    message: '',
    confirmText: '删除',
    loading: false,
    action: null as null | (() => Promise<void>),
  });

  const askConfirm = (title: string, message: string, action: () => Promise<void>) => {
    confirm.title = title;
    confirm.message = message;
    confirm.confirmText = '删除';
    confirm.action = action;
    confirm.open = true;
  };

  const runConfirm = async () => {
    if (!confirm.action) return;
    confirm.loading = true;
    try {
      await confirm.action();
      confirm.open = false;
    } finally {
      confirm.loading = false;
      confirm.action = null;
    }
  };

  const askDeleteChannel = (channel: AlertChannel) => {
    askConfirm(
      '删除告警通道',
      `确定删除“${channel.name}”吗？历史投递记录会保留通道名称快照，规则中的引用会同时移除。`,
      async () => {
        try {
          await alertService.deleteChannel(channel.id);
          notify('通道已删除', 'success');
          await Promise.all([loadChannels(), loadRules()]);
        } catch (error: any) {
          notify(`删除通道失败：${error?.message || error}`, 'error');
        }
      }
    );
  };

  const askDeleteRule = (rule: AlertRule) => {
    askConfirm('删除告警规则', `确定删除规则“${rule.name}”吗？`, async () => {
      try {
        await alertService.deleteRule(rule.id);
        notify('规则已删除', 'success');
        await loadRules();
      } catch (error: any) {
        notify(`删除规则失败：${error?.message || error}`, 'error');
      }
    });
  };

  const askDeleteSilence = (silence: AlertSilence) => {
    askConfirm(
      '删除静默窗口',
      `确定删除 ${formatTime(silence.startsAt)} 至 ${formatTime(silence.endsAt)} 的静默吗？`,
      async () => {
        try {
          await alertService.deleteSilence(silence.id);
          notify('静默窗口已删除', 'success');
          await loadSilences();
        } catch (error: any) {
          notify(`删除静默失败：${error?.message || error}`, 'error');
        }
      }
    );
  };

  // ── 测试告警 ─────────────────────────────────────────────────
  const sendTestEvent = async () => {
    if (summary.value.paused) {
      notify('当前没有启用的通道，测试事件会被记录为 SKIPPED_NO_CHANNEL', 'warning');
    }
    testingEvent.value = true;
    try {
      await alertService.sendTestEvent({
        eventType: 'SYSTEM',
        message: '来自告警配置页的测试消息，用于验证规则路由与通道投递。',
      });
      notify('测试告警已生成，正在切换至投递记录', 'success');
      tab.value = 'deliveries';
      await new Promise(resolve => setTimeout(resolve, 2500));
      deliveryPage.value = 1;
      await loadDeliveries();
    } catch (error: any) {
      notify(`发送测试告警失败：${error?.message || error}`, 'error');
    } finally {
      testingEvent.value = false;
    }
  };

  // Volar mis-infers large setup objects as refs in this template; a computed
  // alias keeps template type-checking accurate while script code keeps mutating
  // the original reactive objects.
  const channelFormView = computed(() => channelForm);
  const secretFlags = computed(() => showSecret);
  const ruleFormView = computed(() => ruleForm);
  const silenceFormView = computed(() => silenceForm);
  const confirmView = computed(() => confirm);
  const deliveryFiltersView = computed(() => deliveryFilters);

  watch(tab, value => {
    if (value === 'deliveries') {
      loadDeliveries();
    }
    if (value === 'silences') {
      loadSilences();
    }
  });

  // Keep the delivery log live while the worker is draining the outbox; nothing
  // is more confusing than a row that says "等待发送" after it was delivered.
  let deliveryTimer: ReturnType<typeof setInterval> | null = null;
  const startDeliveryPolling = () => {
    stopDeliveryPolling();
    deliveryTimer = setInterval(() => {
      if (
        tab.value === 'deliveries' &&
        deliveries.value.some(item => item.status === 'PENDING' || item.status === 'SENDING')
      ) {
        loadDeliveries();
      }
    }, 3000);
  };
  const stopDeliveryPolling = () => {
    if (deliveryTimer) {
      clearInterval(deliveryTimer);
      deliveryTimer = null;
    }
  };

  onMounted(() => {
    loadAll();
    startDeliveryPolling();
  });

  onBeforeUnmount(stopDeliveryPolling);
</script>

<style scoped>
  .alert-config-page {
    min-width: 0;
  }

  .load-error,
  .paused-body {
    display: flex;
    align-items: center;
    gap: 16px;
    flex-wrap: wrap;
    justify-content: space-between;
  }

  .paused-body p {
    margin: 4px 0 0;
    font-size: 13px;
    line-height: 1.6;
  }

  .paused-banner {
    margin-bottom: 16px;
  }

  .alert-shell {
    overflow: hidden;
  }

  .alert-tabs {
    padding: 0 8px;
  }

  .tab-body {
    padding: 16px;
  }

  .tab-toolbar {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 14px;
    flex-wrap: wrap;
  }

  .tab-toolbar--filters {
    align-items: center;
  }

  .tab-summary {
    display: flex;
    align-items: center;
    gap: 8px;
    color: var(--ds-text-2);
    font-size: 13px;
    line-height: 1.5;
  }

  .tab-summary strong {
    color: rgb(var(--v-theme-on-surface));
    font-size: 16px;
    font-variant-numeric: tabular-nums;
  }

  .filter-select {
    max-width: 180px;
  }

  .filter-select--wide {
    max-width: 240px;
  }

  .channel-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
    gap: 14px;
  }

  .channel-card {
    display: flex;
    flex-direction: column;
    gap: 12px;
    padding: 16px;
  }

  .channel-top {
    display: flex;
    align-items: flex-start;
    gap: 12px;
  }

  .channel-badge {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 40px;
    height: 40px;
    border-radius: 12px;
    flex: none;
  }

  .channel-identity {
    min-width: 0;
    flex: 1 1 auto;
  }

  .channel-identity h3 {
    margin: 0;
    font-size: 15px;
    font-weight: 600;
    line-height: 1.4;
    word-break: break-word;
  }

  .channel-identity p {
    margin: 3px 0 0;
    color: var(--ds-text-3);
    font-size: 12px;
  }

  .channel-test-row {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 10px;
    border-radius: 8px;
    background: rgba(var(--v-theme-on-surface), 0.035);
    font-size: 12px;
  }

  .test-label {
    color: var(--ds-text-3);
  }

  .test-time {
    margin-right: auto;
    color: var(--ds-text-2);
    font-variant-numeric: tabular-nums;
  }

  .channel-alert {
    margin: 0;
    font-size: 12px;
  }

  .channel-actions {
    display: flex;
    align-items: center;
    gap: 2px;
    margin-top: auto;
    flex-wrap: wrap;
  }

  .channel-actions :deep(.v-btn) {
    min-width: 0;
    padding-inline: 8px;
    font-size: 12px;
    letter-spacing: 0;
  }

  .channel-toggle {
    margin-left: auto;
  }

  .empty-state {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 10px;
    padding: 48px 20px;
    text-align: center;
  }

  .empty-state h3 {
    margin: 0;
    font-size: 16px;
  }

  .empty-state p {
    max-width: 460px;
    margin: 0;
    color: var(--ds-text-3);
    font-size: 13px;
    line-height: 1.6;
  }

  .skeleton-card {
    padding: 18px;
  }

  .cell-stack {
    display: flex;
    flex-direction: column;
    line-height: 1.45;
  }

  .cell-stack span {
    color: var(--ds-text-3);
    font-size: 12px;
  }

  .cell-muted {
    color: var(--ds-text-2);
    font-size: 13px;
  }

  .row-actions {
    display: flex;
    align-items: center;
    gap: 2px;
    flex-wrap: wrap;
  }

  .alert-dialog .v-card-title {
    font-size: 17px;
    font-weight: 600;
  }

  .alert-dialog .v-card-subtitle {
    white-space: normal;
    line-height: 1.5;
  }

  .form-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 16px 20px;
  }

  .field {
    min-width: 0;
  }

  .field.span-2 {
    grid-column: 1 / -1;
  }

  .field-label {
    display: block;
    margin-bottom: 6px;
    font-size: 13px;
    font-weight: 500;
  }

  .field-help {
    margin: 6px 0 0;
    color: var(--ds-text-3);
    font-size: 12px;
    line-height: 1.5;
  }

  .switch-field {
    display: flex;
    align-items: center;
  }

  .config-section {
    margin-top: 20px;
    padding-top: 16px;
    border-top: 1px solid var(--ds-border-subtle);
  }

  .config-section h4 {
    margin: 0 0 14px;
    font-size: 14px;
    font-weight: 600;
  }

  .json-editor {
    width: 100%;
    min-height: 90px;
    padding: 10px 12px;
    border: 1px solid var(--ds-border-default);
    border-radius: 8px;
    background: rgba(var(--v-theme-on-surface), 0.02);
    color: rgb(var(--v-theme-on-surface));
    font-family: 'SFMono-Regular', Menlo, Consolas, monospace;
    font-size: 12.5px;
    line-height: 1.55;
    resize: vertical;
    outline: none;
  }

  .json-editor:focus {
    border-color: rgb(var(--v-theme-primary));
    box-shadow: 0 0 0 1px rgb(var(--v-theme-primary));
  }

  .json-editor--tall {
    min-height: 120px;
  }

  .detail-table th {
    width: 120px;
    color: var(--ds-text-3);
    font-weight: 500;
  }

  .detail-block {
    margin-top: 16px;
  }

  .detail-block h4 {
    margin: 0 0 6px;
    font-size: 13px;
    font-weight: 600;
  }

  .detail-block pre {
    max-height: 220px;
    margin: 0;
    padding: 10px 12px;
    overflow: auto;
    border: 1px solid var(--ds-border-subtle);
    border-radius: 8px;
    background: rgba(var(--v-theme-on-surface), 0.03);
    font-family: 'SFMono-Regular', Menlo, Consolas, monospace;
    font-size: 12px;
    line-height: 1.55;
    white-space: pre-wrap;
    word-break: break-word;
  }

  .detail-block--error pre {
    border-color: rgba(var(--v-theme-error), 0.25);
    color: rgb(var(--v-theme-error));
  }

  @media (max-width: 900px) {
    .form-grid {
      grid-template-columns: minmax(0, 1fr);
    }

    .channel-grid {
      grid-template-columns: minmax(0, 1fr);
    }

    .filter-select,
    .filter-select--wide {
      max-width: none;
      width: 100%;
    }
  }
</style>

<route lang="json">
{
  "meta": {
    "title": "告警配置",
    "icon": "mdi-bell-cog-outline",
    "requiresAuth": true,
    "navGroup": "systemManage",
    "navOrder": 15,
    "roles": ["admin"]
  }
}
</route>
