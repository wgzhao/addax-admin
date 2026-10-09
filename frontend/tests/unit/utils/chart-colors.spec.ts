import { describe, expect, it } from 'vitest';
import {
  CATEGORICAL_DARK,
  CATEGORICAL_LIGHT,
  JOUR_KIND_META,
  STATUS_META,
  STATUS_ORDER,
  formatSeconds,
} from '@/utils/chart-colors';

describe('chart-colors', () => {
  it('keeps both palettes the same length with unique colors', () => {
    expect(CATEGORICAL_LIGHT).toHaveLength(8);
    expect(CATEGORICAL_DARK).toHaveLength(8);
    expect(new Set(CATEGORICAL_LIGHT).size).toBe(8);
    expect(new Set(CATEGORICAL_DARK).size).toBe(8);
  });

  it('covers every status code in STATUS_ORDER', () => {
    for (const status of STATUS_ORDER) {
      expect(STATUS_META[status], `missing meta for ${status}`).toBeDefined();
      expect(STATUS_META[status].label).not.toBe('');
      expect(STATUS_META[status].token).not.toBe('');
    }
    expect(STATUS_ORDER).toHaveLength(7);
  });

  it('maps every jour kind to a unique palette slot within 8', () => {
    const kinds = Object.values(JOUR_KIND_META);
    expect(kinds).toHaveLength(8);
    const slots = kinds.map(k => k.paletteIndex);
    expect(new Set(slots).size).toBe(8);
    expect(Math.max(...slots)).toBeLessThan(8);
  });
});

describe('formatSeconds', () => {
  it('formats seconds, minutes and hours', () => {
    expect(formatSeconds(45)).toBe('45s');
    expect(formatSeconds(90)).toBe('2m');
    expect(formatSeconds(3600)).toBe('1h');
    expect(formatSeconds(5400)).toBe('1h 30m');
  });

  it('renders a dash for nullish input', () => {
    expect(formatSeconds(null)).toBe('-');
    expect(formatSeconds(undefined)).toBe('-');
  });
});
