<template>
  <section class="empty-guide">
    <div class="empty-guide__head">
      <div class="empty-guide__icon">
        <v-icon size="26">mdi-database-plus-outline</v-icon>
      </div>
      <div>
        <h2>开始你的第一条采集链路</h2>
        <p>连接一个采集源和目标端，选择要采集的表，平台会接管调度、执行与运行监控。</p>
      </div>
    </div>

    <ol class="empty-guide__steps">
      <li>
        <span class="step-index">01</span>
        <div>
          <div class="step-title">连接采集源</div>
          <div class="step-desc">支持 MySQL、PostgreSQL、SQL Server、Oracle、MongoDB、Elasticsearch。</div>
        </div>
        <v-btn size="small" color="primary" variant="flat" to="/source?action=create">
          新增采集源
        </v-btn>
      </li>
      <li>
        <span class="step-index">02</span>
        <div>
          <div class="step-title">配置目标端</div>
          <div class="step-desc">Hive / HDFS 或关系型数据库，平台按目标契约创建和演进表结构。</div>
        </div>
        <v-btn size="small" variant="tonal" to="/target?action=create">新增目标端</v-btn>
      </li>
      <li>
        <span class="step-index">03</span>
        <div>
          <div class="step-title">批量新增采集表</div>
          <div class="step-desc">从源库选择表，预览字段映射与目标策略，一次创建数百张表。</div>
        </div>
        <v-btn size="small" variant="tonal" to="/table/batch-add">新增采集表</v-btn>
      </li>
    </ol>

    <div class="empty-guide__foot">
      <span>第一次使用？</span>
      <router-link to="/help">查看快速上手</router-link>
      <span class="dot">·</span>
      <a :href="APP_DOCS_URL" target="_blank" rel="noopener noreferrer">安装与部署文档</a>
    </div>
  </section>
</template>

<script setup lang="ts">
  import { APP_DOCS_URL } from '@/config/branding';
</script>

<style scoped>
  .empty-guide {
    padding: 28px 28px 22px;
    border: 1px solid var(--ds-border-default);
    border-radius: 14px;
    background:
      radial-gradient(circle at 4% 0%, rgba(var(--v-theme-primary), 0.08), transparent 36%),
      var(--ds-surface-raised);
  }

  .empty-guide__head {
    display: flex;
    align-items: flex-start;
    gap: 14px;
  }

  .empty-guide__icon {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 46px;
    height: 46px;
    border-radius: 12px;
    background: rgba(var(--v-theme-primary), 0.1);
    color: rgb(var(--v-theme-primary));
    flex: none;
  }

  h2 {
    margin: 0;
    font-size: 20px;
    font-weight: 700;
    color: rgb(var(--v-theme-on-surface));
  }

  p {
    margin: 5px 0 0;
    max-width: 720px;
    font-size: 13px;
    line-height: 1.7;
    color: var(--ds-text-2);
  }

  .empty-guide__steps {
    display: flex;
    flex-direction: column;
    gap: 8px;
    margin: 22px 0 0;
    padding: 0;
    list-style: none;
  }

  .empty-guide__steps li {
    display: flex;
    align-items: center;
    gap: 14px;
    padding: 12px 14px;
    border: 1px solid var(--ds-border-subtle);
    border-radius: 10px;
    background: rgba(var(--v-theme-on-surface), 0.02);
  }

  .step-index {
    width: 28px;
    font-size: 12px;
    font-weight: 700;
    color: var(--ds-text-3);
    font-variant-numeric: tabular-nums;
  }

  .empty-guide__steps li > div {
    flex: 1 1 auto;
    min-width: 0;
  }

  .step-title {
    font-size: 13px;
    font-weight: 600;
    color: rgb(var(--v-theme-on-surface));
  }

  .step-desc {
    margin-top: 2px;
    font-size: 12px;
    color: var(--ds-text-3);
  }

  .empty-guide__foot {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 4px;
    margin-top: 18px;
    font-size: 12px;
    color: var(--ds-text-3);
  }

  .empty-guide__foot a {
    color: rgb(var(--v-theme-primary));
    text-decoration: none;
  }

  .dot {
    opacity: 0.6;
  }

  @media (max-width: 760px) {
    .empty-guide__steps li {
      flex-wrap: wrap;
    }
  }
</style>
