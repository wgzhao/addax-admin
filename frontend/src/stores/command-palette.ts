import { ref } from 'vue';

export const commandPaletteOpen = ref(false);

export const openCommandPalette = () => {
  commandPaletteOpen.value = true;
};

export const toggleCommandPalette = () => {
  commandPaletteOpen.value = !commandPaletteOpen.value;
};
