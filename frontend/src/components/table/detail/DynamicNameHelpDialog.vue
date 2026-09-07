<template>
  <v-dialog v-model="open" max-width="800">
    <v-card>
      <v-card-title>源表动态命名说明</v-card-title>
      <v-card-text>
        <p>
          系统支持在源表名中使用占位符来拼接运行时变量，例如日期。示例：<strong>t_${biz_date_dash}</strong>，
          实际采集时会解析为 <strong>t_2026-01-27</strong>。
        </p>
        <p>当前支持的变量如下：</p>
        <div class="guide-list">
          <div v-for="v in placeholderVars" :key="v.name" class="guide-item">
            <div class="guide-item__key">{{ v.name }}</div>
            <div class="guide-item__desc">{{ v.desc }}</div>
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

  const placeholderVars = [
    { name: 'biz_date_short', desc: '业务日期(yyyyMMdd)' },
    { name: 'biz_date_dash', desc: '业务日期(yyyy-MM-dd)' },
    { name: 'biz_year', desc: '业务年份(yyyy)' },
    { name: 'biz_month', desc: '业务月份(MM)' },
    { name: 'biz_short_month', desc: '业务月份(M)' },
    { name: 'biz_day', desc: '业务日(dd)' },
    { name: 'biz_short_day', desc: '业务日(d)' },
    { name: 'biz_ym', desc: '业务年月(yyyyMM)' },
    { name: 'biz_short_ym', desc: '业务年月(yyyyM)' },
    { name: 'biz_datetime_short', desc: '业务日期时间(yyyyMMddHHmmss)' },
    { name: 'biz_datetime_dash', desc: '业务日期时间(yyyy-MM-dd HH:mm:ss)' },
    { name: 'biz_datetime_0_dash', desc: '时间为 0 的业务日期时间(yyyy-MM-dd 00:00:00)' },
    { name: 'biz_datetime_0_short', desc: '时间为 0 的业务日期时间(yyyyMMdd000000)' },
    { name: 'curr_date_short', desc: '当前日期(yyyyMMdd)' },
    { name: 'curr_date_dash', desc: '当前日期(yyyy-MM-dd)' },
    { name: 'curr_datetime_short', desc: '当前日期时间(yyyyMMddHHmmss)' },
    { name: 'curr_datetime_dash', desc: '当前日期时间(yyyy-MM-dd HH:mm:ss)' },
    { name: 'curr_datetime_0_dash', desc: '时间为 0 当前日期时间(yyyy-MM-dd 00:00:00)' },
    { name: 'curr_datetime_0_short', desc: '时间为 0 的当前日期时间(yyyyMMdd000000)' },
  ];
</script>

<style lang="scss" scoped>
  @import './_detail-shared.scss';
</style>
