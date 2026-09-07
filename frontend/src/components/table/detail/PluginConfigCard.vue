<template>
  <v-card flat class="ds-card section-card">
    <v-card-text class="section-body">
      <div class="section-head">
        <div>
          <div class="section-title">插件配置</div>
        </div>
      </div>

      <v-row density="comfortable">
        <v-col cols="12">
          <div class="field-block">
            <div class="field-label">读取插件配置 (JSON)</div>
            <v-textarea
              v-model="readerPluginConfigText"
              variant="outlined"
              density="comfortable"
              placeholder='例如: {"fetchSize": 50000}'
              :rules="[jsonObjectOrEmpty]"
              rows="5"
              auto-grow
              hide-details="auto"
              class="mono-input"
            />
          </div>
        </v-col>

        <v-col cols="12">
          <div class="field-block">
            <div class="field-label">写入插件配置 (JSON)</div>
            <v-textarea
              v-model="writerPluginConfigText"
              variant="outlined"
              density="comfortable"
              placeholder='例如: {"writeMode": "append"}'
              :rules="[jsonObjectOrEmpty]"
              rows="5"
              auto-grow
              hide-details="auto"
              class="mono-input"
            />
          </div>
        </v-col>
      </v-row>
    </v-card-text>
  </v-card>
</template>

<script setup lang="ts">
  import { jsonObjectOrEmpty } from '@/utils/json-plugin';

  const readerPluginConfigText = defineModel<string>('readerPluginConfigText', { required: true });
  const writerPluginConfigText = defineModel<string>('writerPluginConfigText', { required: true });
</script>

<style lang="scss" scoped>
  @use './detail-shared' as *;
</style>
