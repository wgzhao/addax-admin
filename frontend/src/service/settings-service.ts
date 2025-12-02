import Requests from '@/utils/requests'

export interface ConfigItem {
  item_key: string
  item_value: string
  remark: string
}

export interface HiveServer2Config {
  url: string
  username: string
  password: string
  driverPath: string
  driverClassName?: string
}

export interface SystemConfig {
  HDFS_STORAGE_FORMAT: string
  HDFS_COMPRESS_FORMAT: string
  ADDAX: string
  HIVE_SERVER2: HiveServer2Config
  HDFS_PREFIX: string
  SWITCH_TIME: string
  CONCURRENT_LIMIT: number // 后端返回字符串格式
  QUEUE_SIZE: number // 后端返回字符串格式
  HDFS_CONFIG: String
}

class SettingsService {
  baseUrl = 'settings'

  /**
   * 获取所有系统配置
   */
  async getSettings(): Promise<any> {
    try {
      const response = await Requests.get(`${this.baseUrl}/sys-config`)

      return response
    } catch (error) {
      console.error('获取配置失败:', error)
      throw error
    }
  }

  /**
   * 保存系统配置
   */
  async saveSettings(config: any): Promise<boolean> {
    await Requests.post(`${this.baseUrl}/sys-config`, config)
    return true
  }

  /**
   * 测试HiveServer2连接
   */
  testHiveConnection(config: HiveServer2Config): Promise<any> {
    return Requests.post(`${this.baseUrl}/test-hive-connect`, config, {
      timeout: 5000
    }) as unknown as Promise<any>
  }

  /**
   * 验证Addax程序目录
   */
  async validateAddaxPath(path: string): Promise<any> {
    try {
      const response = await Requests.get('/settings/addax-path/validate', { path })
      return response
    } catch (error) {
      console.error(error)
      return null
    }
  }

  /*
   * 保存作业配置模板
   */
  async saveHdfsWriterConfig(configData: string): Promise<boolean> {
    try {
      const response = await Requests.post<boolean>(
        `${this.baseUrl}/addax-hdfs-writer-template`,
        configData
      )
      // 处理不同的返回结构
      const respObj: any = response as any
      if (respObj != null && typeof respObj === 'object' && 'data' in respObj) {
        return respObj.data
      }
      return response as boolean
    } catch (error) {
      console.error('保存作业配置失败:', error)
      throw error
    }
  }

  /**
   * 获取作业配置模板
   */
  async getHdfsWriterConfig(): Promise<any> {
    try {
      const response = await Requests.get(`${this.baseUrl}/addax-hdfs-writer-template`)
      return response
    } catch (error) {
      console.error('获取作业配置失败:', error)
      throw error
    }
  }

  /**
   * 获取所有作业配置模板列表
   */
  async getJobConfigList(): Promise<any[]> {
    try {
      const response = await Requests.get<any[]>(`${this.baseUrl}/job-templates`)
      return response as any[]
    } catch (error) {
      console.error('获取作业配置列表失败:', error)
      throw error
    }
  }

  /**
   * 删除作业配置模板
   */
  async deleteJobConfig(id: string): Promise<boolean> {
    try {
      const response = await Requests.delete<boolean>(`${this.baseUrl}/job-template/${id}`)
      const respObj: any = response as any
      if (respObj != null && typeof respObj === 'object' && 'data' in respObj) {
        return respObj.data
      }
      return response as boolean
    } catch (error) {
      console.error('删除作业配置失败:', error)
      throw error
    }
  }

  /**
   * 获取Addax作业内容
   */
  async getAddaxJobContent(path: string): Promise<any> {
    try {
      const response = await Requests.get('/settings/addax-content', { path })
      return response
    } catch (error) {
      console.error(error)
      return null
    }
  }
}

export default new SettingsService()
