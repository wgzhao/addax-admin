<template>
  <v-form ref="form" fast-fail @submit.prevent="save">
    <v-card class="compact-card">
      <v-card-title class="d-flex align-center">
        <v-icon class="me-2">mdi-database</v-icon>
        {{ mode === 'add' ? '新增采集源' : mode === 'edit' ? '编辑采集源' : '采集源详情' }}
        <v-spacer />
      </v-card-title>

      <v-card-text class="pa-4">
        <!-- 基本信息区块 -->
        <v-row dense class="mb-2" align="start" justify="start">
          <v-col cols="12" md="3">
            <v-text-field v-model="sourceItem.code" label="采集编号" placeholder="两位大写字母" prepend-inner-icon="mdi-pound"
              autocomplete="off" :rules="[rules.required, rules.codeExistsRule]" :disabled="mode === 'edit'"
              variant="outlined" density="compact" />
          </v-col>
          <v-col cols="12" md="3">
            <v-text-field v-model="sourceItem.name" label="采集名称" :rules="[rules.required]"
              prepend-inner-icon="mdi-label" variant="outlined" density="compact" />
          </v-col>
          <v-col cols="12" md="3">
            <v-text-field v-model="sourceItem.startAt" placeholder="HH:mm 或 HH:mm:ss" label="采集时间"
              :rules="[timeFormatRule]" :error-messages="timeError" prepend-inner-icon="mdi-clock-outline"
              variant="outlined" density="compact" />
          </v-col>
          <v-col cols="12" md="3" class="d-flex align-center">
            <v-switch v-model="sourceItem.enabled" color="primary" hide-details density="compact">
              <template #append>
                <v-chip size="x-small" :color="sourceItem.enabled ? 'success' : 'error'"
                  :text="sourceItem.enabled ? '已启用' : '已禁用'" class="ml-1"></v-chip>
              </template>
            </v-switch>
          </v-col>
        </v-row>

        <!-- 连接信息区块 -->
        <v-row dense class="mb-2" align="start" justify="start">
          <v-col cols="12" md="5">
            <v-text-field v-model="sourceItem.url" placeholder="jdbc:hive2://host:port" label="JDBC 连接地址"
              :rules="[rules.required]" prepend-inner-icon="mdi-link" variant="outlined" density="compact" />
          </v-col>
          <v-col cols="12" md="2">
            <v-text-field v-model="sourceItem.username" label="用户名" prepend-inner-icon="mdi-account" variant="outlined"
              density="compact" autocomplete="off" />
          </v-col>
          <v-col cols="12" md="3">
            <v-text-field v-model="sourceItem.pass" :type="showPassword ? 'text' : 'password'" label="密码"
              prepend-inner-icon="mdi-lock" variant="outlined" density="compact" autocomplete="new-password"
              :append-inner-icon="showPassword ? 'mdi-eye-off' : 'mdi-eye'"
              @click:append-inner="showPassword = !showPassword" />
          </v-col>
          <v-col cols="12" md="2">
            <v-btn color="info" text="测试连接" v-if="mode === 'add' || mode === 'edit'" @click="testConnect"
              prepend-icon="mdi-connection"></v-btn>
          </v-col>
        </v-row>

        <!-- 备注信息 -->
        <v-row dense>
          <v-col cols="12">
            <v-textarea v-model="sourceItem.remark" label="备注信息" auto-grow rows="4" variant="outlined"
              prepend-inner-icon="mdi-text" density="compact" />
          </v-col>
        </v-row>
      </v-card-text>

      <v-card-actions class="pa-3">
        <v-spacer></v-spacer>
        <v-btn color="secondary" @click="close" variant="tonal" prepend-icon="mdi-close">
          关闭
        </v-btn>
        <v-btn type="submit" color="primary" v-if="mode === 'add' || mode === 'edit'" prepend-icon="mdi-content-save">
          保存
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-form>
</template>
<script setup lang="ts">
import { onMounted, ref, computed } from "vue";
import { notify } from '@/stores/notifier';
import sourceService from "@/service/source-service";
import { EtlSource } from "@/types/database";

const props = defineProps({
  sid: Number,
  mode: String
});

const form = ref<any>(null);

const showPassword = ref(false);

const sourceItem = ref<EtlSource>({
  id: 0,
  code: "",
  name: "",
  url: "",
  username: "",
  pass: "",
  startAt: "",
  prerequisite: "",
  preScript: "",
  remark: "",
  enabled: true
})

const emit = defineEmits(["closeDialog", "save"]);

// 时间格式验证
const timeFormatRule = computed(() => {
  // 支持 HH:mm 和 HH:mm:ss 两种格式
  const timeRegex = /^([0-1][0-9]|2[0-3]):[0-5][0-9](:[0-5][0-9])?$/;
  return (value: string) => {
    if (!value) return true; // 允许空值
    if (!timeRegex.test(value)) {
      return '时间格式不正确，请使用 HH:mm 或 HH:mm:ss 格式（如：08:30 或 08:30:15）';
    }
    return true;
  };
});

// 时间错误消息
const timeError = computed(() => {
  if (!sourceItem.value.startAt) return [];
  // 支持 HH:mm 和 HH:mm:ss 两种格式
  const timeRegex = /^([0-1][0-9]|2[0-3]):[0-5][0-9](:[0-5][0-9])?$/;
  if (!timeRegex.test(sourceItem.value.startAt)) {
    return ['时间格式不正确，请使用 HH:mm 或 HH:mm:ss 格式（如：08:30 或 08:30:15）'];
  }
  return [];
});

// 时间格式化函数：自动补充秒数
const formatTimeInput = (value: string) => {
  if (!value) return value;

  // 如果是 HH:mm 格式，自动补充 :00
  const timeWithoutSeconds = /^([0-1][0-9]|2[0-3]):[0-5][0-9]$/;
  if (timeWithoutSeconds.test(value)) {
    return value + ':00';
  }

  return value;
};

const rules = {
  required: (value: string) => !!value || '必填项',
  codeExistsRule: async (value: string) => {
    if (props.mode !== 'add' || !value) {
      return true;
    }
    try {
      const exists = await sourceService.checkCode(value);
      return !exists || '编号已存在';
    } catch (error) {
      notify('检查编号失败: ' + ((error as Error).message || error), 'error');
      return '编号校验失败，请稍后重试';
    }
  }
};


const save = async () => {
  if (props.mode === "add" || props.mode === "edit") {
    const { valid } = await form.value.validate();
    if (!valid) {
      notify('请修正表单中的错误', 'error');
      return;
    }

    // 验证时间格式
    if (sourceItem.value.startAt && timeError.value.length > 0) {
      notify('请检查启动时间格式', 'error');
      return;
    }

    // 格式化时间输入，自动补充秒数
    if (sourceItem.value.startAt) {
      sourceItem.value.startAt = formatTimeInput(sourceItem.value.startAt);
    }

    sourceService.save(sourceItem.value)
      .then(() => {
        notify('保存成功', 'success');
        emit('save'); // 发出save事件，通知父组件更新列表
        emit('closeDialog'); // 关闭对话框
      })
      .catch(error => {
        notify('保存失败: ' + error, 'error');
      });
  }
};

// 定义关闭对话框的逻辑
const close = () => {
  emit('closeDialog'); // 通知父组件关闭对话框
};

const testConnect = () => {
  sourceService.testConnection({
    url: sourceItem.value.url,
    username: sourceItem.value.username,
    password: sourceItem.value.pass
  })
    .then(resp => {
      if (resp) {
        notify('连接成功', 'success');
      } else {
        notify('连接失败', 'warning');
      }
    })
    .catch(error => {
      notify('连接失败: ' + error, 'error');
    });
}
onMounted(() => {
  console.log("mode = ", props.mode);
  if (props.sid != -1) {
    sourceService.get(Number(props.sid))
      .then(resp => {
        sourceItem.value = resp;
      })
      .catch(error => {
        console.log(error);
        notify(`加载数据源失败: ${error}`, 'error');
      });
  }
})
</script>
<!-- <style scoped>
.compact-card {
  max-width: 1080px; /* 放宽整体宽度，避免拥挤 */
  margin: 0 auto;   /* 居中显示 */
}
</style> -->