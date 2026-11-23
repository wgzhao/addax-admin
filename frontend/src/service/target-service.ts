import Requests from '@/utils/requests';
import type { EtlTarget } from '@/types/database';

export interface DialectInfo {
  family: string;
  dialect: string;
  writerPlugin?: string | null;
  supported: boolean;
  supportedDialects: string;
}

export interface TargetCheckItem {
  key: string;
  label: string;
  status: 'ok' | 'fail' | 'skip';
  message: string;
  hint: string | null;
}

export interface TargetHealthCheckResult {
  success: boolean;
  family: string;
  checks: TargetCheckItem[];
}

export interface TargetHealthCheckPlan {
  family: string;
  items: { key: string; label: string }[];
}

class TargetService {
  prefix = '/targets';

  list(enabledOnly = true): Promise<EtlTarget[]> {
    return Requests.get(this.prefix, { enabledOnly }) as unknown as Promise<EtlTarget[]>;
  }

  create(data: EtlTarget): Promise<EtlTarget> {
    return Requests.post(this.prefix, data) as unknown as Promise<EtlTarget>;
  }

  update(id: number, data: EtlTarget): Promise<EtlTarget> {
    return Requests.put(`${this.prefix}/${id}`, data) as unknown as Promise<EtlTarget>;
  }

  remove(id: number): Promise<void> {
    return Requests.delete(`${this.prefix}/${id}`) as unknown as Promise<void>;
  }

  testConnect(id: number): Promise<void> {
    return Requests.post(`${this.prefix}/${id}/test-connect`, {}) as unknown as Promise<void>;
  }

  testConnectPayload(data: EtlTarget): Promise<void> {
    return Requests.post(`${this.prefix}/test-connect`, data) as unknown as Promise<void>;
  }

  /**
   * 一次跑完整个清单（API/测试用；界面走 plan + item 逐项回填）。
   */
  healthCheck(data: EtlTarget): Promise<TargetHealthCheckResult> {
    return Requests.post(`${this.prefix}/health-check`, data, {
      timeout: 200_000,
    }) as unknown as Promise<TargetHealthCheckResult>;
  }

  healthCheckPlan(data: EtlTarget): Promise<TargetHealthCheckPlan> {
    return Requests.post(
      `${this.prefix}/health-check/plan`,
      data
    ) as unknown as Promise<TargetHealthCheckPlan>;
  }

  /**
   * 单项校验；HDFS 写入探针可能要跑几秒到几十秒，客户端等待窗口与后端超时保持一致。
   */
  healthCheckItem(data: EtlTarget, key: string): Promise<TargetCheckItem> {
    return Requests.post(`${this.prefix}/health-check/items/${encodeURIComponent(key)}`, data, {
      timeout: 200_000,
    }) as unknown as Promise<TargetCheckItem>;
  }

  detectDialect(url: string, targetType?: string): Promise<DialectInfo> {
    return Requests.post(`${this.prefix}/detect-dialect`, {
      url,
      targetType: targetType || '',
    }) as unknown as Promise<DialectInfo>;
  }
}

export default new TargetService();
