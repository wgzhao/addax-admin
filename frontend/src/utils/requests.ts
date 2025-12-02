// import axios from "axios";
import axios, {
  AxiosInstance,
  AxiosRequestConfig,
  AxiosResponse,
  AxiosError,
  InternalAxiosRequestConfig
} from 'axios'
import { notify } from '@/stores/notifier'
import { useAuthStore } from '@/stores/auth'

// axios.defaults.baseURL = import.meta.env.VITE_API_BASE_URL;
// axios.defaults.timeout = 5000;
// console.log('mode = ' + import.meta.env.MODE)

// const requests = new Requests(import.meta.env.VITE_API_BASE_URL, 5000, authStore)

class Requests {
  private instance: AxiosInstance
  private authStore: ReturnType<typeof useAuthStore>

  constructor(baseURL: string, timeout = 5000, authStore: ReturnType<typeof useAuthStore>) {
    // 创建 Axios 实例
    this.authStore = authStore
    this.instance = axios.create({
      baseURL,
      timeout
    })

    // 配置请求拦截器
    this.instance.interceptors.request.use(
      (config: InternalAxiosRequestConfig) => {
        const token = this.authStore.token // 从 Pinia Store 获取 Token
        if (token) {
          config.headers.Authorization = `Bearer ${token}` // 在请求头中添加 Authorization
        }

        return config
      },
      (error) => {
        return Promise.reject(error) // 请求发生错误时直接抛出
      }
    )

    // 配置响应拦截器
    this.instance.interceptors.response.use(
      (response: AxiosResponse) => {
        return response.data
      },
      (error: AxiosError) => {
        // 统一处理非 2xx 状态码的错误
        let message = '请求发生错误'

        if (error.response) {
          const { status, data } = error.response

          // 处理 401 未授权错误 - Token 过期或无效
          if (status === 401) {
            // 清除本地存储的认证信息
            this.authStore.logout()

            // 显示提示消息
            notify('登录已过期，请重新登录', 'warning')

            // 跳转到登录页面
            // 使用动态导入避免循环依赖
            import('@/router').then((routerModule) => {
              routerModule.default.push('/login')
            })

            return Promise.reject(new Error('登录已过期'))
          }

          // 处理其他错误状态码
          const responseData: any = data
          // 尝试从响应体中获取更具体的错误信息
          if (responseData && typeof responseData === 'string') {
            message = responseData
          } else if (responseData && responseData.message) {
            message = responseData.message
          } else {
            message = `请求错误: ${status} ${error.response.statusText}`
          }
        } else {
          // 设置请求时触发了一个错误
          message = error.message
        }

        // 抛出错误，以便业务代码的 .catch() 块可以捕获
        return Promise.reject(new Error(message))
      }
    )
  }

  get<T = any>(url: string, params?: any, config?: AxiosRequestConfig): Promise<T> {
    return this.instance.get<T>(url, { params, ...config }) as Promise<T>
  }

  post<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> {
    return this.instance.post<T>(url, data, { ...config }) as Promise<T>
  }

  put<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> {
    return this.instance.put<T>(url, data, { ...config }) as Promise<T>
  }

  delete<T = any>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return this.instance.delete<T>(url, { ...config }) as Promise<T>
  }

  patch<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> {
    return this.instance.patch<T>(url, data, { ...config }) as Promise<T>
  }
}

const authStore = useAuthStore()

export default new Requests(import.meta.env.VITE_API_BASE_URL, 5000, authStore)
