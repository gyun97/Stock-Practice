// 토큰 관리 유틸리티
export class TokenManager {
  private static instance: TokenManager
  private refreshPromise: Promise<string> | null = null

  private constructor() {}

  public static getInstance(): TokenManager {
    if (!TokenManager.instance) {
      TokenManager.instance = new TokenManager()
    }
    return TokenManager.instance
  }

  // Access Token 가져오기
  public getAccessToken(): string | null {
    return localStorage.getItem('accessToken')
  }

  // Refresh Token 가져오기
  public getRefreshToken(): string | null {
    return localStorage.getItem('refreshToken')
  }

  // 토큰 저장
  public setTokens(accessToken: string, refreshToken?: string): void {
    localStorage.setItem('accessToken', accessToken)
    if (refreshToken) {
      localStorage.setItem('refreshToken', refreshToken)
    }
  }

  // 토큰 삭제
  public clearTokens(): void {
    localStorage.removeItem('accessToken')
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('userInfo')
    localStorage.removeItem('loginMethod')
  }

  // 토큰 만료 확인
  public isTokenExpired(token: string): boolean {
    try {
      const payload = this.decodeJwtPayload(token)
      if (!payload || !payload.exp) return true
      
      const currentTime = Math.floor(Date.now() / 1000)
      return payload.exp < currentTime
    } catch {
      return true
    }
  }

  // JWT 페이로드 디코딩
  private decodeJwtPayload(token: string): any {
    try {
      const base64Url = token.split('.')[1]
      const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/')
      const jsonPayload = decodeURIComponent(
        atob(base64)
          .split('')
          .map(c => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
          .join('')
      )
      return JSON.parse(jsonPayload)
    } catch {
      return null
    }
  }

  // 토큰 자동 갱신
  public async refreshAccessToken(): Promise<string> {
    // 이미 갱신 중이면 기존 Promise 반환
    if (this.refreshPromise) {
      return this.refreshPromise
    }

    this.refreshPromise = this.performTokenRefresh()
    
    try {
      const newToken = await this.refreshPromise
      return newToken
    } finally {
      this.refreshPromise = null
    }
  }

  private async performTokenRefresh(): Promise<string> {
    const refreshToken = this.getRefreshToken()
    
    if (!refreshToken) {
      throw new Error('Refresh token not found')
    }

    try {
      const response = await fetch('/api/v1/users/reissue', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        credentials: 'include', // 쿠키 포함
      })

      if (response.ok) {
        const result = await response.json()
        const newAccessToken = result.data || result.accessToken
        
        if (newAccessToken) {
          this.setTokens(newAccessToken)
          console.log('토큰 갱신 성공')
          return newAccessToken
        } else {
          throw new Error('새로운 액세스 토큰을 받지 못했습니다')
        }
      } else if (response.status === 401) {
        // Refresh Token도 만료된 경우
        console.log('Refresh Token도 만료됨, 로그아웃 처리')
        this.clearTokens()
        window.location.href = '/login'
        throw new Error('Refresh token expired')
      } else {
        throw new Error(`토큰 갱신 실패: ${response.status}`)
      }
    } catch (error) {
      console.error('토큰 갱신 오류:', error)
      this.clearTokens()
      window.location.href = '/login'
      throw error
    }
  }

  // API 요청을 위한 헤더 생성 (자동 토큰 갱신 포함)
  public async getAuthHeaders(): Promise<HeadersInit> {
    let accessToken = this.getAccessToken()
    
    if (!accessToken || this.isTokenExpired(accessToken)) {
      console.log('Access Token 만료, 갱신 시도')
      accessToken = await this.refreshAccessToken()
    }

    return {
      'Authorization': `Bearer ${accessToken}`,
      'Content-Type': 'application/json'
    }
  }

  // fetch 요청 래퍼 (자동 토큰 갱신 포함)
  public async authenticatedFetch(url: string, options: RequestInit = {}): Promise<Response> {
    const headers = await this.getAuthHeaders()
    
    const response = await fetch(url, {
      ...options,
      headers: {
        ...headers,
        ...options.headers
      }
    })

    // 401 응답이면 토큰 갱신 후 재시도
    if (response.status === 401) {
      console.log('401 응답, 토큰 갱신 후 재시도')
      try {
        const newAccessToken = await this.refreshAccessToken()
        const newHeaders = {
          ...headers,
          'Authorization': `Bearer ${newAccessToken}`
        }
        
        return fetch(url, {
          ...options,
          headers: {
            ...newHeaders,
            ...options.headers
          }
        })
      } catch (error) {
        console.error('토큰 갱신 실패:', error)
        return response // 원본 응답 반환
      }
    }

    return response
  }
}

// 싱글톤 인스턴스 내보내기
export const tokenManager = TokenManager.getInstance()
