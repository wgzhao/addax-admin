export interface LoadItemsOptions {
  page: number
  itemsPerPage: number
  sortBy: string | null
}

// 通用分页返回结果
export interface Page<T> {
  content: T[]
  totalElements: number
}
