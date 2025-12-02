import Requests from '@/utils/requests'
import type { EtlTable, VEtlWithSource, EtlColumn, EtlStatistic, EtlSource } from '@/types/database'
import type { Page } from '@/types'

class TableService {
  prefix: string = '/tables'

  // 分页查询采集表
  fetchTableList(
    page: number,
    pageSize: number,
    q: string,
    runStatus?: string,
    sortBy?: any
  ): Promise<Page<VEtlWithSource>> {
    // `sortBy` 应该是一个包含 sortField 和 sortOrder 的对象
    const params = {
      page: page,
      pageSize: pageSize,
      q: q,
      status: runStatus,
      sortField: sortBy?.sortField,
      sortOrder: sortBy?.sortOrder
    }
    return Requests.get(this.prefix, params) as unknown as Promise<Page<VEtlWithSource>>
  }

  // 查询单个采集表
  fetchTableDetail(tableId: number): Promise<VEtlWithSource> {
    return Requests.get(`${this.prefix}/${tableId}`) as unknown as Promise<VEtlWithSource>
  }

  // 删除采集表
  delete(tableId: number): Promise<void> {
    return Requests.delete(`${this.prefix}/${tableId}`) as unknown as Promise<void>
  }

  // 查询表字段对比
  fetchFieldsCompare(tableId: number): Promise<EtlColumn[]> {
    return Requests.get(`${this.prefix}/${tableId}/columns`) as unknown as Promise<EtlColumn[]>
  }

  // 查询表采集统计
  fetchAddaxResult(tableId: number): Promise<EtlStatistic[]> {
    return Requests.get(`${this.prefix}/${tableId}/statistics`) as unknown as Promise<
      EtlStatistic[]
    >
  }

  // 批量保存采集表
  batchSave(data: EtlTable[]): Promise<number> {
    return Requests.post(`${this.prefix}/batch`, data) as unknown as Promise<number>
  }

  // 新增或更新单个采集表
  save(data: EtlTable): Promise<EtlTable> {
    if (data.id) {
      // 如果有id，则为更新
      return Requests.put(`${this.prefix}/${data.id}`, data) as unknown as Promise<EtlTable>
    }
    // 否则为新增
    return Requests.post(this.prefix, data) as unknown as Promise<EtlTable>
  }

  // 启动采集
  execCollect(tableId: number, timeout = 60000): Promise<string> {
    return Requests.post(
      `${this.prefix}/${tableId}/start`,
      {},
      { timeout }
    ) as unknown as Promise<string>
  }

  updateSingleTable(id: number): Promise<Map<string, any>> {
    return Requests.post(
      `${this.prefix}/${id}/actions/refresh`,
      {},
      { timeout: 600000 }
    ) as unknown as Promise<Map<string, any>>
  }
  // 更新表结构
  updateSchema(params: { mode?: string; tid?: number }): Promise<string> {
    if (params.tid) {
      return Requests.post(
        `${this.prefix}/${params.tid}/actions/refresh`,
        {}
      ) as unknown as Promise<string>
    }
    return Requests.post(
      `${this.prefix}/actions/refresh`,
      {},
      { params: { mode: params.mode } }
    ) as unknown as Promise<string>
  }

  // 批量更新表状态
  batchUpdateStatus(data: any): Promise<number> {
    return Requests.post(`${this.prefix}/batch/status`, data) as unknown as Promise<number>
  }

  // 获取Addax Job模板
  fetchAddaxJob(tableId: number): Promise<string> {
    return Requests.get(`${this.prefix}/${tableId}/addax-job`) as unknown as Promise<string>
  }

  updateAddaxJob(tableId) {
    return Requests.put(`${this.prefix}/${tableId}/addax-job`, {}) as unknown as Promise<string>
  }

  // --- 以下接口在变更表中未定义，暂时保留旧逻辑或标记为待处理 ---

  // 3. 执行采集的命令列表 - 未在变更表中找到，需要确认
  fetchCmdList(tid: string) {
    console.warn('fetchCmdList API endpoint may be outdated.')
    return Requests.get(`/table/cmdList/${tid}`)
  }

  // 4. 使用的表 - 未在变更表中找到，需要确认
  fetchTableUsed(sysid: string, tbl: string) {
    console.warn('fetchTableUsed API endpoint may be outdated.')
    return Requests.get(`/table/scene`, {
      params: {
        tbl: tbl,
        sysId: sysid
      }
    })
  }
}

export default new TableService()
