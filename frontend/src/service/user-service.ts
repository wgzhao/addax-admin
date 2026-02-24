import Requests from '@/utils/requests'

export interface AccountUser {
  username: string
  enabled: boolean
  authorities: string[]
}

interface CreateUserPayload {
  username: string
  password: string
  enabled?: boolean
  authority?: string
}

interface UpdateUserPayload {
  password?: string
  authority?: string
}

class UserService {
  prefix = '/users'

  me(): Promise<AccountUser> {
    return Requests.get(`${this.prefix}/me`) as unknown as Promise<AccountUser>
  }

  list(): Promise<AccountUser[]> {
    return Requests.get(this.prefix) as unknown as Promise<AccountUser[]>
  }

  create(payload: CreateUserPayload): Promise<AccountUser> {
    return Requests.post(this.prefix, payload) as unknown as Promise<AccountUser>
  }

  update(username: string, payload: UpdateUserPayload): Promise<AccountUser> {
    return Requests.put(`${this.prefix}/${encodeURIComponent(username)}`, payload) as unknown as Promise<AccountUser>
  }

  delete(username: string): Promise<void> {
    return Requests.delete(`${this.prefix}/${encodeURIComponent(username)}`) as unknown as Promise<void>
  }

  enable(username: string): Promise<AccountUser> {
    return Requests.post(`${this.prefix}/${encodeURIComponent(username)}/enable`) as unknown as Promise<AccountUser>
  }

  disable(username: string): Promise<AccountUser> {
    return Requests.post(`${this.prefix}/${encodeURIComponent(username)}/disable`) as unknown as Promise<AccountUser>
  }
}

export default new UserService()
