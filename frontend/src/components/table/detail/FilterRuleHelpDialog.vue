<template>
  <v-dialog v-model="open" max-width="860">
    <v-card>
      <v-card-title>过滤规则说明</v-card-title>
      <v-card-text>
        <p>
          过滤规则用于控制每次采集读取哪些数据。未填写时，系统按
          <strong>1=1</strong> 处理，也就是不过滤。
        </p>
        <div class="guide-list">
          <div class="guide-item">
            <div class="guide-item__key">1=1</div>
            <div class="guide-item__desc">不添加任何过滤条件，适合作为默认值。</div>
          </div>
          <div class="guide-item">
            <div class="guide-item__key">字段条件</div>
            <div class="guide-item__desc">
              按源表字段编写筛选条件，例如
              <code>update_time &gt; '${biz_datetime_dash}'</code>。条件中可以直接复用内置变量。
            </div>
          </div>
          <div class="guide-item">
            <div class="guide-item__key">__max__&lt;field&gt;</div>
            <div class="guide-item__desc">
              用于增量采集。系统会读取上一次采集时 <code>&lt;field&gt;</code> 的最大值
              <code>m</code>，并自动转换为
              <code>&lt;field&gt; &gt; m</code
              >。建议选择数值型、单调递增的字段，通常优先使用主键。
            </div>
          </div>
        </div>
      </v-card-text>
      <v-card-actions>
        <v-spacer />
        <v-btn variant="text" @click="open = false">关闭</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
  const open = defineModel<boolean>({ default: false });
</script>

<style lang="scss" scoped>
  @use './detail-shared' as *;
</style>
