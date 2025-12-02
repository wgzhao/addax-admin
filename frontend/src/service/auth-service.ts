import Requests from '@/utils/requests'
import User from '@/types/user'

class AuthService {
  login(user: User) {
    return Requests.post('/auth/login', {
      username: user.username,
      password: user.password
    })
  }

  logout() {
    localStorage.removeItem('token')
  }

  register(user: User) {
    return Requests.post('/auth/register', {
      username: user.username,
      password: user.password
    })
  }
}

export const authService = new AuthService()
