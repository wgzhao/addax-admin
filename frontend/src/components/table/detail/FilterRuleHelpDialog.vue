<template>
  <HelpDialogShell
    v-model="open"
    title="过滤规则说明"
    subtitle="过滤规则决定每次从源端读取哪些数据。示例可以直接复制到表单。"
    icon="mdi-filter-cog-outline"
    :max-width="820"
  >
    <div v-if="!mongo && !es" class="empty-rule-note">
      <span class="empty-rule-note__icon">
        <v-icon size="20">mdi-check-circle-outline</v-icon>
      </span>
      <div>
        <strong>留空时无需填写 1=1</strong>
        <p>系统会自动按 <code>1=1</code> 处理，不会附加任何过滤条件。</p>
      </div>
    </div>

    <section v-if="mongo" class="recipe-section">
      <div class="section-heading">
        <h3>MongoDB 过滤</h3>
        <span>查询文档使用 extended JSON，不是 SQL</span>
      </div>
      <div class="recipe-grid">
        <article class="recipe-card">
          <div class="recipe-card__head">
            <span class="recipe-icon recipe-icon--neutral">
              <v-icon size="18">mdi-filter-outline</v-icon>
            </span>
            <div>
              <h4>全量读取</h4>
              <p>留空或填写空文档，读取整个集合。</p>
            </div>
          </div>
          <div class="recipe-code"><code>{}</code></div>
        </article>
        <article class="recipe-card">
          <div class="recipe-card__head">
            <span class="recipe-icon recipe-icon--neutral">
              <v-icon size="18">mdi-filter-variant</v-icon>
            </span>
            <div>
              <h4>等值过滤</h4>
              <p>只读取 status 为 paid 的文档。</p>
            </div>
          </div>
          <div class="recipe-code"><code>{"status":"paid"}</code></div>
        </article>
        <article class="recipe-card">
          <div class="recipe-card__head">
            <span class="recipe-icon recipe-icon--neutral">
              <v-icon size="18">mdi-clock-outline</v-icon>
            </span>
            <div>
              <h4>日期范围</h4>
              <p>日期必须使用 extended JSON，不能写 new Date()。</p>
            </div>
          </div>
          <div class="recipe-code">
            <code>{"created_at":{"$gte":{"$date":"2026-11-01T00:00:00+08:00"}}}</code>
          </div>
        </article>
        <article class="recipe-card">
          <div class="recipe-card__head">
            <span class="recipe-icon recipe-icon--neutral">
              <v-icon size="18">mdi-auto-fix</v-icon>
            </span>
            <div>
              <h4>按目标最大值增量</h4>
              <p>读取目标表中该字段最大值之后的新文档。</p>
            </div>
          </div>
          <div class="recipe-code"><code>__max__seq</code></div>
        </article>
      </div>
      <p class="mongo-note">
        不支持 <code>$where</code>、<code>$function</code>、<code>$accumulator</code>；增量字段建议建索引。
      </p>
    </section>

    <section v-if="es" class="recipe-section">
      <div class="section-heading">
        <h3>Elasticsearch 过滤</h3>
        <span>查询条件使用 Elasticsearch Query DSL JSON，不是 SQL</span>
      </div>
      <div class="recipe-grid">
        <article class="recipe-card">
          <div class="recipe-card__head">
            <span class="recipe-icon recipe-icon--neutral">
              <v-icon size="18">mdi-filter-outline</v-icon>
            </span>
            <div>
              <h4>全量读取</h4>
              <p>留空或 match_all，读取整个索引。</p>
            </div>
          </div>
          <div class="recipe-code"><code>{"match_all":{}}</code></div>
        </article>
        <article class="recipe-card">
          <div class="recipe-card__head">
            <span class="recipe-icon recipe-icon--neutral">
              <v-icon size="18">mdi-filter-variant</v-icon>
            </span>
            <div>
              <h4>等值过滤</h4>
              <p>只读取 status 为 paid 的文档。</p>
            </div>
          </div>
          <div class="recipe-code"><code>{"term":{"status":"paid"}}</code></div>
        </article>
        <article class="recipe-card">
          <div class="recipe-card__head">
            <span class="recipe-icon recipe-icon--neutral">
              <v-icon size="18">mdi-clock-outline</v-icon>
            </span>
            <div>
              <h4>日期范围</h4>
              <p>日期字段使用 range 查询。</p>
            </div>
          </div>
          <div class="recipe-code">
            <code>{"range":{"created_at":{"gte":"2026-11-01T00:00:00+08:00"}}}</code>
          </div>
        </article>
        <article class="recipe-card">
          <div class="recipe-card__head">
            <span class="recipe-icon recipe-icon--neutral">
              <v-icon size="18">mdi-auto-fix</v-icon>
            </span>
            <div>
              <h4>按目标最大值增量</h4>
              <p>读取目标表中该字段最大值之后的新文档。</p>
            </div>
          </div>
          <div class="recipe-code"><code>__max__seq</code></div>
        </article>
      </div>
      <p class="mongo-note">
        不支持 <code>script</code>、<code>script_fields</code>、<code>runtime_mappings</code>；增量字段建议使用数值或 keyword 类型。
      </p>
    </section>

    <section v-if="!mongo && !es" class="recipe-section">
      <div class="section-heading">
        <h3>常用写法</h3>
        <span>从最常见的场景开始选择</span>
      </div>

      <div class="recipe-grid">
        <article class="recipe-card">
          <div class="recipe-card__head">
            <span class="recipe-icon recipe-icon--neutral">
              <v-icon size="18">mdi-table-large</v-icon>
            </span>
            <div>
              <h4>全量读取</h4>
              <p>无需填写过滤条件，读取源表全部数据。</p>
            </div>
          </div>
          <div class="recipe-code recipe-code--empty">
            <code>(保持为空)</code>
          </div>
        </article>

        <article class="recipe-card">
          <div class="recipe-card__head">
            <span class="recipe-icon">
              <v-icon size="18">mdi-calendar-filter-outline</v-icon>
            </span>
            <div>
              <h4>按业务日期过滤</h4>
              <p>适合日分区或只读取当次业务日的数据。</p>
            </div>
          </div>
          <div class="recipe-code">
            <code>{{ businessDateFilter }}</code>
            <v-btn
              size="x-small"
              variant="text"
              icon="mdi-content-copy"
              aria-label="复制业务日期过滤示例"
              @click="copyExample(businessDateFilter)"
            />
          </div>
        </article>

        <article class="recipe-card">
          <div class="recipe-card__head">
            <span class="recipe-icon">
              <v-icon size="18">mdi-filter-outline</v-icon>
            </span>
            <div>
              <h4>字段条件</h4>
              <p>组合源表字段和 SQL 条件，只读取符合条件的数据。</p>
            </div>
          </div>
          <div class="recipe-code">
            <code>status = 1 AND amount &gt;= 100</code>
            <v-btn
              size="x-small"
              variant="text"
              icon="mdi-content-copy"
              aria-label="复制字段条件示例"
              @click="copyExample('status = 1 AND amount >= 100')"
            />
          </div>
        </article>

        <article class="recipe-card recipe-card--accent">
          <div class="recipe-card__head">
            <span class="recipe-icon recipe-icon--accent">
              <v-icon size="18">mdi-trending-up</v-icon>
            </span>
            <div>
              <h4>增量读取</h4>
              <p>按目标端已有最大值继续采集，适合单调递增的主键或编号。</p>
            </div>
          </div>
          <div class="recipe-code">
            <code>__max__id</code>
            <v-btn
              size="x-small"
              variant="text"
              icon="mdi-content-copy"
              aria-label="复制 __max__id"
              @click="copyExample('__max__id')"
            />
          </div>
          <code class="recipe-result">__max__id → id &gt; 123456</code>
        </article>
      </div>
    </section>

    <section v-if="!mongo && !es" class="notes-section">
      <div class="section-heading">
        <h3>使用注意</h3>
      </div>
      <ul class="notes-list">
        <li>
          <v-icon size="16">mdi-database-outline</v-icon>
          <span>字符串和日期值需要按源端数据库语法加引号；日期变量可继续引用动态命名说明。</span>
        </li>
        <li>
          <v-icon size="16">mdi-counter</v-icon>
          <span>
            <code>__max__&lt;field&gt;</code>
            会从目标端上一业务日期分区读取
            <code>&lt;field&gt;</code>
            的最大值，再转换为大于条件。
          </span>
        </li>
        <li>
          <v-icon size="16">mdi-alert-outline</v-icon>
          <span>
            如果取不到最大值或字段类型不兼容，系统会回退为
            <code>1=1</code>
            并记录风险日志，需要检查目标端数据和类型。
          </span>
        </li>
      </ul>
    </section>

    <section class="notes-section">
      <div class="section-heading">
        <h3>补数注意</h3>
      </div>
      <ul class="notes-list">
        <li>
          <v-icon size="16">mdi-calendar-remove-outline</v-icon>
          <span>
            <code>__max__&lt;field&gt;</code>
            只有下界，适合连续每日采集；历史补数会读入后续日期的数据，请改用日期条件，详见用户手册「补数回补」。
          </span>
        </li>
      </ul>
    </section>
  </HelpDialogShell>
</template>

<script setup lang="ts">
  import { notify } from '@/stores/notifier';
  import { copyText } from '@/utils/clipboard';
  import HelpDialogShell from './HelpDialogShell.vue';

  const open = defineModel<boolean>({ default: false });
  defineProps<{ mongo?: boolean; es?: boolean }>();
  const businessDateFilter = "dt='${biz_date_dash}'";

  const copyExample = async (value: string) => {
    try {
      await copyText(value);
      notify('示例已复制', 'success', 1600);
    } catch {
      notify('复制失败，请手动复制', 'error');
    }
  };
</script>

<style scoped>
  .empty-rule-note {
    display: flex;
    align-items: flex-start;
    gap: 12px;
    padding: 13px 14px;
    border: 1px solid rgba(var(--v-theme-primary), 0.16);
    border-radius: 12px;
    background: rgba(var(--v-theme-primary), 0.045);
  }

  .empty-rule-note__icon {
    display: inline-flex;
    color: rgb(var(--v-theme-primary));
  }

  .empty-rule-note strong {
    color: rgb(var(--v-theme-on-surface));
    font-size: 0.9rem;
  }

  .empty-rule-note p {
    margin: 3px 0 0;
    color: var(--ds-text-2);
    font-size: 0.82rem;
    line-height: 1.55;
  }

  .recipe-section,
  .notes-section {
    margin-top: 22px;
  }

  .section-heading {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 10px;
  }

  .section-heading h3 {
    margin: 0;
    color: rgb(var(--v-theme-on-surface));
    font-size: 0.95rem;
    font-weight: 700;
  }

  .section-heading span {
    color: var(--ds-text-3);
    font-size: 0.76rem;
  }

  .recipe-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px;
  }

  .recipe-card {
    min-width: 0;
    padding: 14px;
    border: 1px solid var(--ds-border-default);
    border-radius: 14px;
    background: rgb(var(--v-theme-surface));
  }

  .recipe-card--accent {
    border-color: rgba(var(--v-theme-primary), 0.24);
    background: linear-gradient(
      145deg,
      rgba(var(--v-theme-primary), 0.07),
      rgba(var(--v-theme-primary), 0.015)
    );
  }

  .recipe-card__head {
    display: flex;
    align-items: flex-start;
    gap: 10px;
  }

  .recipe-icon {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 30px;
    height: 30px;
    flex: none;
    border-radius: 9px;
    background: rgba(var(--v-theme-primary), 0.09);
    color: rgb(var(--v-theme-primary));
  }

  .recipe-icon--neutral {
    background: rgba(var(--v-theme-on-surface), 0.06);
    color: var(--ds-text-2);
  }

  .recipe-icon--accent {
    background: rgba(var(--v-theme-primary), 0.14);
  }

  .recipe-card h4 {
    margin: 0;
    color: rgb(var(--v-theme-on-surface));
    font-size: 0.87rem;
    font-weight: 700;
  }

  .recipe-card p {
    margin: 3px 0 0;
    color: var(--ds-text-2);
    font-size: 0.78rem;
    line-height: 1.55;
  }

  .recipe-code {
    display: flex;
    align-items: center;
    gap: 6px;
    min-height: 39px;
    margin-top: 12px;
    padding: 5px 5px 5px 10px;
    border-radius: 9px;
    background: rgba(var(--v-theme-on-surface), 0.045);
  }

  .recipe-code code,
  .recipe-result,
  .empty-rule-note code,
  .notes-list code {
    font-family: 'SF Mono', 'Monaco', 'Inconsolata', 'Roboto Mono', 'Consolas', monospace;
  }

  .recipe-code code {
    min-width: 0;
    flex: 1;
    overflow-wrap: anywhere;
    color: rgb(var(--v-theme-on-surface));
    font-size: 0.8rem;
  }

  .recipe-code--empty code {
    color: var(--ds-text-3);
  }

  .recipe-result {
    display: block;
    margin-top: 8px;
    padding: 7px 10px;
    border-radius: 8px;
    background: rgba(var(--v-theme-primary), 0.075);
    color: rgb(var(--v-theme-primary));
    font-size: 0.78rem;
    font-weight: 700;
    overflow-wrap: anywhere;
  }

  .notes-list {
    display: flex;
    flex-direction: column;
    gap: 10px;
    margin: 0;
    padding: 0;
    list-style: none;
  }

  .notes-list li {
    display: grid;
    grid-template-columns: 18px minmax(0, 1fr);
    gap: 9px;
    color: var(--ds-text-2);
    font-size: 0.8rem;
    line-height: 1.65;
  }

  .notes-list :deep(.v-icon) {
    margin-top: 3px;
    color: var(--ds-text-3);
  }

  @media (max-width: 760px) {
    .section-heading span {
      display: none;
    }

    .recipe-grid {
      grid-template-columns: 1fr;
    }
  }
</style>
