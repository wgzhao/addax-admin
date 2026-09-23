// import axios from "axios";
import axios, {
  AxiosInstance,
  AxiosRequestConfig,
  AxiosResponse,
  AxiosError,
  InternalAxiosRequestConfig,
} from 'axios';
import { notify } from '@/stores/notifier';
import { useAuthStore } from '@/stores/auth';
import pinia from '@/plugins/pinia';
import { getActivePinia, setActivePinia } from 'pinia';

// axios.defaults.baseURL = import.meta.env.VITE_API_BASE_URL;
// axios.defaults.timeout = 5000;
// console.log('mode = ' + import.meta.env.MODE)

// const requests = new Requests(import.meta.env.VITE_API_BASE_URL, 5000, authStore)

// 凭证校验接口：这些接口的 401 是业务错误（账号或密码不正确），而非会话过期
const CREDENTIAL_ENDPOINTS = ['/auth/login'];

function isCredentialRequest(url?: string): boolean {
  if (!url) {
    return false;
  }
  return CREDENTIAL_ENDPOINTS.some(endpoint => url.includes(endpoint));
}

// 请求未到达服务端（网络异常、超时、取消）时，axios 给出的是英文消息，需转为面向用户的中文提示
function resolveTransportMessage(error: AxiosError): string {
  switch (error.code) {
    case AxiosError.ECONNABORTED:
    case AxiosError.ETIMEDOUT:
      return '请求超时，请稍后重试';
    case AxiosError.ERR_NETWORK:
      return '网络连接失败，请检查网络或后端服务是否可用';
    case AxiosError.ERR_CANCELED:
      return '请求已取消';
    default:
      return error.message;
  }
}

class Requests {
  private instance: AxiosInstance;
  private authStore: ReturnType<typeof useAuthStore>;

  constructor(baseURL: string, timeout = 60000) {
    // 创建 Axios 实例
    // Ensure an active pinia for usage outside components
    if (!getActivePinia()) {
      setActivePinia(pinia);
    }
    this.authStore = useAuthStore(pinia);
    this.instance = axios.create({
      baseURL,
      timeout,
    });

    // 配置请求拦截器
    this.instance.interceptors.request.use(
      (config: InternalAxiosRequestConfig) => {
        const token = this.authStore.token; // 从 Pinia Store 获取 Token
        if (token) {
          config.headers.Authorization = `Bearer ${token}`; // 在请求头中添加 Authorization
        }

        return config;
      },
      error => {
        return Promise.reject(error); // 请求发生错误时直接抛出
      }
    );

    // 配置响应拦截器
    this.instance.interceptors.response.use(
      (response: AxiosResponse) => {
        return response.data;
      },
      (error: AxiosError) => {
        // 统一处理非 2xx 状态码的错误
        let message = '请求发生错误';

        if (error.response) {
          const { status, data } = error.response;

          // 处理 401 未授权错误 - Token 过期或无效
          // 登录接口的 401 表示凭证错误，属于业务错误，需保留后端消息交给调用方展示
          if (status === 401 && !isCredentialRequest(error.config?.url)) {
            // 清除本地存储的认证信息
            this.authStore.logout();

            // 显示提示消息
            notify('登录已过期，请重新登录', 'warning');

            // 跳转到登录页面
            // 使用动态导入避免循环依赖
            import('@/router').then(routerModule => {
              routerModule.default.push('/login');
            });

            return Promise.reject(new Error('登录已过期'));
          }

          // 处理其他错误状态码
          const responseData: any = data;
          // 尝试从响应体中获取更具体的错误信息
          if (responseData && typeof responseData === 'string') {
            message = responseData;
          } else if (responseData && responseData.message) {
            message = responseData.message;
          } else {
            message = `请求错误: ${status} ${error.response.statusText}`;
          }
        } else {
          // 请求未发出或未收到响应
          message = resolveTransportMessage(error);
        }

        // 抛出错误，以便业务代码的 .catch() 块可以捕获
        return Promise.reject(new Error(message));
      }
    );
  }

  get<T = any>(url: string, params?: any, config?: AxiosRequestConfig): Promise<T> {
    return this.instance.get<T>(url, { params, ...config }) as Promise<T>;
  }

  post<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> {
    return this.instance.post<T>(url, data, { ...config }) as Promise<T>;
  }

  put<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> {
    return this.instance.put<T>(url, data, { ...config }) as Promise<T>;
  }

  delete<T = any>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return this.instance.delete<T>(url, { ...config }) as Promise<T>;
  }

  patch<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> {
    return this.instance.patch<T>(url, data, { ...config }) as Promise<T>;
  }
}

export default new Requests(import.meta.env.VITE_API_BASE_URL, 5000);
