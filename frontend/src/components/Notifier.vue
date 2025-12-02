<template>
  <v-snackbar v-model="notice.show" :color="notice.color" :timeout="notice.timeout" location="top" style="top: 80px">
    <div class="d-flex align-center">
      <v-icon v-if="notice.icon" class="me-2">{{ notice.icon }}</v-icon>
      <div>
        <div v-if="notice.title" class="font-weight-bold">{{ notice.title }}</div>
        {{ notice.text }}
      </div>
    </div>
    <template #actions>
      <v-btn icon @click="hide">
        <v-icon>mdi-close</v-icon>
      </v-btn>
    </template>
  </v-snackbar>
</template>

<script setup lang="ts">
import { useNotifier } from '@/stores/notifier'
const { notice, hide } = useNotifier()
</script>