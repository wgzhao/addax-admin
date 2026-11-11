import { SysItem } from '@/types/database';
import Requests from '@/utils/requests';

export interface ConfigItem {
  item_key: string;
  item_value: string;
  remark: string;
}

export interface SystemConfig {
  ADDAX: string;
  SWITCH_TIME: string;
  CONCURRENT_LIMIT: number; // 后端返回字符串格式
  QUEUE_SIZE: number; // 后端返回字符串格式
}

class SettingsService {
  baseUrl = 'settings';

  /**
   * 获取所有系统配置
   */
  async getSettings(): Promise<any> {
    try {
      const response = await Requests.get(`${this.baseUrl}/sys-config`);

      return response;
    } catch (error) {
      console.error('获取配置失败:', error);
      throw error;
    }
  }

  /**
   * 保存系统配置
   */
  async saveSettings(config: any): Promise<boolean> {
    await Requests.post(`${this.baseUrl}/sys-config`, config);
    return true;
  }

  /**
   * 获取所有作业配置模板列表
   */
  async getJobConfig(): Promise<Map<string, SysItem>> {
    try {
      const response = await Requests.get<Map<string, SysItem>>(`${this.baseUrl}/job-templates`);
      return response as Map<string, SysItem>;
    } catch (error) {
      console.error('获取作业配置列表失败:', error);
      throw error;
    }
  }

  /**
   * 保存作业配置模板列表
   */
  async saveJobTemplates(templates: SysItem[]): Promise<boolean> {
    try {
      const response = await Requests.put<boolean>(`${this.baseUrl}/job-templates`, templates);
      const respObj: any = response as any;
      if (respObj != null && typeof respObj === 'object' && 'data' in respObj) {
        return respObj.data;
      }
      return response as boolean;
    } catch (error) {
      console.error('保存作业配置模板失败:', error);
      throw error;
    }
  }

  /**
   * 删除作业配置模板
   */
  async deleteJobConfig(id: string): Promise<boolean> {
    try {
      const response = await Requests.delete<boolean>(`${this.baseUrl}/job-template/${id}`);
      const respObj: any = response as any;
      if (respObj != null && typeof respObj === 'object' && 'data' in respObj) {
        return respObj.data;
      }
      return response as boolean;
    } catch (error) {
      console.error('删除作业配置失败:', error);
      throw error;
    }
  }

  /**
   * 触发切日调度任务重新注册
   */
  async rescheduleSwitchTimeTask(): Promise<any> {
    try {
      const response = await Requests.post(`${this.baseUrl}/reschedule-switch-time-task`);
      return response;
    } catch (error) {
      console.error('触发切日调度任务失败:', error);
      throw error;
    }
  }
}

export default new SettingsService();
