import { ref } from 'vue';

export interface Notice {
  id: number;
  text: string;
  color: string;
  timeout: number;
  icon?: string;
  title?: string;
}

const notices = ref<Notice[]>([]);
const timers = new Map<number, ReturnType<typeof setTimeout>>();
const MAX_VISIBLE = 4;
let sequence = 0;

// Why: failures carry information the operator still needs after the next toast arrives,
// so errors stay until dismissed while ordinary confirmations fade out on their own.
const DEFAULT_TIMEOUT: Record<string, number> = {
  success: 3000,
  info: 4000,
  primary: 4000,
  warning: 6000,
  error: 0,
};

export function dismiss(id: number) {
  const timer = timers.get(id);
  if (timer) {
    clearTimeout(timer);
    timers.delete(id);
  }
  notices.value = notices.value.filter(item => item.id !== id);
}

export function notify(
  text: string,
  color: string = 'info',
  timeout?: number,
  title?: string,
  icon?: string
) {
  const id = ++sequence;
  const effectiveTimeout = timeout ?? DEFAULT_TIMEOUT[color] ?? 4000;

  while (notices.value.length >= MAX_VISIBLE) {
    dismiss(notices.value[0].id);
  }

  notices.value = [...notices.value, { id, text, color, timeout: effectiveTimeout, title, icon }];

  if (effectiveTimeout > 0) {
    timers.set(
      id,
      setTimeout(() => dismiss(id), effectiveTimeout)
    );
  }
  return id;
}

export function useNotifier() {
  return { notices, notify, dismiss };
}
