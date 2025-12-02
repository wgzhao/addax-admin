<template>
  <v-dialog v-model="dialog" v-if="dialog" @keyup.esc="dialog = false">
    <v-card>
      <v-card-title>
        {{ title }}
      </v-card-title>
      <v-list-item class="px-2">
        <slot name="header" />
        <template #append>
          <v-btn
            class="btn btn-primary bg-primary"
            text="关闭"
            @click="dialog = false"
          ></v-btn>
        </template>
      </v-list-item>
      <v-card-text>
        <slot />
      </v-card-text>
      <v-card-actions v-if="$slots.actions">
        <slot name="actions" />
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>
<script setup lang="ts">
const dialog = defineModel({ required: true, type: Boolean, default: true });
defineProps({
  title: {
    type: String,
    required: true
  }
});
</script>
