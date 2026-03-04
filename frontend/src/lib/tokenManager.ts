// 토큰 관리 유틸리티
export class TokenManager {
  private static instance: TokenManager
  private refreshPromise: Promise<string> | null = null
  private accessToken: string | null = null // 메모리에 토큰 저장

  private constructor() { }

  public static getInstance(): TokenManager {
    if (!TokenManager.instance) {
      TokenManager.instance = new TokenManager()
    }
    return TokenManager.instance
  }

  // 전역 인터셉터를 우회하기 위한 원본 fetch 가져오기
  private getOriginalFetch() {
    // 동적 import로 원본 fetch 가져오기
    return (window as any).__originalFetch || fetch
  }

  // Access Token 가져오기
  public getAccessToken(): string | null {
    return this.accessToken
  }

  // Refresh Token 가져오기 (쿠키에서 - httpOnly이므로 직접 접근 불가, null 반환)
  // 실제로는 credentials: 'include'로 백엔드 API 호출 시 자동으로 쿠키가 전송됨
  public getRefreshToken(): string | null {
    // httpOnly 쿠키는 JavaScript에서 접근할 수 없으므로 null 반환
    // 쿠키는 fetch 요청 시 credentials: 'include'로 자동 전송됨
    return null
  }

  // 토큰 저장
  public setTokens(accessToken: string, refreshToken?: string): void {
    this.accessToken = accessToken
    // Refresh Token은 httpOnly 쿠키로 관리되므로 메모리에 저장하지 않음
  }

  // 토큰 삭제
  public clearTokens(): void {
    this.accessToken = null
    localStorage.removeItem('userInfo')
    localStorage.removeItem('loginMethod')
    // 참고: httpOnly 쿠키는 JavaScript에서 삭제할 수 없으므로 
    // 백엔드의 /api/v1/users/logout API를 호출해서 삭제해야 합니다.
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
    // httpOnly 쿠키는 자동으로 전송되므로 별도 체크 불필요
    console.log('🔄 토큰 재발급 시작 - /api/v1/users/reissue 호출')
    try {
      // 토큰 재발급 API는 전역 인터셉터를 우회하기 위해 원본 fetch를 직접 사용
      const response = await this.getOriginalFetch()('/api/v1/users/reissue', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        credentials: 'include', // 쿠키 자동 전송
      })

      console.log('토큰 재발급 응답 상태:', response.status)

      if (response.ok) {
        const result = await response.json()
        console.log('✅ 토큰 재발급 응답 데이터:', result)
        let newAccessToken = result.data || result.accessToken

        // "Bearer " prefix 제거 (백엔드에서 이미 포함시켜서 보내므로)
        if (newAccessToken && newAccessToken.startsWith('Bearer ')) {
          newAccessToken = newAccessToken.substring(7)
        }

        if (newAccessToken) {
          this.setTokens(newAccessToken)
          console.log('✨ 토큰 갱신 성공')
          return newAccessToken
        } else {
          throw new Error('새로운 액세스 토큰을 받지 못했습니다')
        }
      } else if (response.status === 401) {
        // Refresh Token도 만료된 경우
        console.warn('❌ Refresh Token 만료 또는 쿠키 누락 (401)')
        throw new Error('Refresh token expired')
      } else if (response.status === 403) {
        console.warn('❌ Refresh Token 접근 거부 (403)')
        throw new Error('Refresh token forbidden')
      } else {
        const errorText = await response.text()
        console.error('⚠ 토큰 갱신 실패:', response.status, errorText)
        throw new Error(`토큰 갱신 실패: ${response.status}`)
      }
    } catch (error) {
      console.error('🔥 토큰 갱신 중 네트워크 또는 서버 오류:', error)
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
        // 토큰 갱신 실패 시 에러 메시지와 함께 응답 반환 (자동 로그아웃 방지)
        throw new Error('토큰 갱신에 실패했습니다')
      }
    }

    return response
  }
}

// 싱글톤 인스턴스 내보내기
export const tokenManager = TokenManager.getInstance()
