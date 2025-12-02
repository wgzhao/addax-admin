<script setup lang="ts">
import { ref, onMounted, onUnmounted } from "vue";

defineProps(["filename", "fcontent"]);
const dialog = ref(true);

onMounted(() => {
  console.log("LogModal mounted");
  dialog.value = true;
});

onUnmounted(() => {
  console.log("LogModal unmounted");
  dialog.value = false;
});
</script>

<template>
  <v-dialog max-width="800" v-model="dialog">
    <v-card>
      <v-card-title class="headline">
        {{ filename }}
      </v-card-title>
      <v-card-text>
        {{ fcontent }}
      </v-card-text>
      <v-card-actions>
        <v-btn color="green darken-1" @click="dialog = false">Close</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<style scoped></style>
