import { beforeEach, describe, expect, it, vi } from 'vitest';

// vi.mock factories are hoisted above const declarations, so the fake
// instance must live in vi.hoisted to be reachable from the factory.
const { fakeInstance } = vi.hoisted(() => ({
  fakeInstance: {
    interceptors: {
      request: { use: vi.fn() },
      response: { use: vi.fn() },
    },
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
    patch: vi.fn(),
  },
}));

vi.mock('axios', () => ({
  default: { create: vi.fn(() => fakeInstance) },
}));

vi.mock('@/router', () => ({ default: { push: vi.fn() } }));

// Module-level imports: requests.ts instantiates its own pinia at import time.
import Requests from '@/utils/requests';
import { useAuthStore } from '@/stores/auth';
import pinia from '@/plugins/pinia';
import { useNotifier } from '@/stores/notifier';
import router from '@/router';

const pushMock = vi.mocked(router.push);
const authStore = useAuthStore(pinia);
const notice = useNotifier().notice;

// Capture the interceptor handlers registered when the module loaded. They are
// extracted before any clearAllMocks() so the captured references stay valid.
const requestHandler = fakeInstance.interceptors.request.use.mock.calls[0][0];
const responseErrorHandler = fakeInstance.interceptors.response.use.mock.calls[0][1];

const flushPromises = () => new Promise(resolve => setTimeout(resolve, 0));

describe('requests interceptors', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    authStore.logout();
    notice.value = { show: false, text: '' };
  });

  describe('request interceptor', () => {
    it('adds a Bearer header when a token is present', () => {
      authStore.setToken('tok-123');
      const config: any = { headers: {} };
      expect(requestHandler(config)).toBe(config);
      expect(config.headers.Authorization).toBe('Bearer tok-123');
    });

    it('leaves the Authorization header unset without a token', () => {
      const config: any = { headers: {} };
      requestHandler(config);
      expect(config.headers.Authorization).toBeUndefined();
    });
  });

  describe('response error handler', () => {
    it('logs out, notifies and redirects on 401', async () => {
      authStore.setToken('stale-token');
      const promise = responseErrorHandler({ response: { status: 401, data: {} } });

      await expect(promise).rejects.toThrow('登录已过期');
      await flushPromises();

      expect(authStore.token).toBeNull();
      expect(notice.value.text).toBe('登录已过期，请重新登录');
      expect(notice.value.color).toBe('warning');
      expect(pushMock).toHaveBeenCalledWith('/login');
    });

    it('uses a string response body as the error message', async () => {
      const promise = responseErrorHandler({
        response: { status: 500, data: 'boom', statusText: 'Internal Server Error' },
      });
      await expect(promise).rejects.toThrow('boom');
    });

    it('uses the message field of an object response body', async () => {
      const promise = responseErrorHandler({
        response: { status: 400, data: { message: 'bad request' }, statusText: 'Bad Request' },
      });
      await expect(promise).rejects.toThrow('bad request');
    });

    it('falls back to status text when the body carries no message', async () => {
      const promise = responseErrorHandler({
        response: { status: 502, data: {}, statusText: 'Bad Gateway' },
      });
      await expect(promise).rejects.toThrow('请求错误: 502 Bad Gateway');
    });

    it('uses the raw error message for network failures', async () => {
      const promise = responseErrorHandler(new Error('Network Error'));
      await expect(promise).rejects.toThrow('Network Error');
    });
  });

  it('exposes get/post/put/delete/patch through the axios instance', async () => {
    fakeInstance.get.mockResolvedValue({ ok: true });
    await expect(Requests.get('/x')).resolves.toEqual({ ok: true });
    expect(fakeInstance.get).toHaveBeenCalledWith('/x', { params: undefined });
  });
});
