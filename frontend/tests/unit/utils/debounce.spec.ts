import { afterEach, describe, expect, it, vi } from 'vitest';
import { debounce, throttle } from '@/utils/debounce';

describe('debounce', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('coalesces rapid calls into a single trailing invocation', () => {
    vi.useFakeTimers();
    const fn = vi.fn();
    const d = debounce(fn, 300);
    d(1);
    d(2);
    d(3);
    expect(fn).not.toHaveBeenCalled();
    vi.advanceTimersByTime(300);
    expect(fn).toHaveBeenCalledTimes(1);
    expect(fn).toHaveBeenCalledWith(3);
  });

  it('resets the timer on each call', () => {
    vi.useFakeTimers();
    const fn = vi.fn();
    const d = debounce(fn, 300);
    d(1);
    vi.advanceTimersByTime(200);
    d(2);
    vi.advanceTimersByTime(200);
    expect(fn).not.toHaveBeenCalled();
    vi.advanceTimersByTime(100);
    expect(fn).toHaveBeenCalledTimes(1);
    expect(fn).toHaveBeenCalledWith(2);
  });
});

describe('throttle', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('invokes immediately on the first call', () => {
    vi.useFakeTimers();
    const fn = vi.fn();
    const t = throttle(fn, 200);
    t(1);
    expect(fn).toHaveBeenCalledTimes(1);
    expect(fn).toHaveBeenCalledWith(1);
  });

  it('drops intermediate calls and fires a trailing call with the last args', () => {
    vi.useFakeTimers();
    const fn = vi.fn();
    const t = throttle(fn, 200);
    t(1);
    vi.advanceTimersByTime(100);
    t(2);
    t(3);
    expect(fn).toHaveBeenCalledTimes(1);
    vi.advanceTimersByTime(200);
    expect(fn).toHaveBeenCalledTimes(2);
    expect(fn).toHaveBeenLastCalledWith(3);
  });
});
