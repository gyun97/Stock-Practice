import { useState } from 'react'
import { Link } from 'react-router-dom'
import { tokenManager } from '../lib/tokenManager'

export default function Login() {
  const [formData, setFormData] = useState({
    email: '',
    password: ''
  })
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  // JWT 페이로드를 UTF-8로 안전하게 디코딩
  const decodeJwtPayload = (token: string): any => {
    try {
      const part = token.split('.')[1]
      const b64 = part.replace(/-/g, '+').replace(/_/g, '/')
      const padLen = (4 - (b64.length % 4)) % 4
      const padded = b64 + '='.repeat(padLen)
      const binary = atob(padded)
      const bytes = Uint8Array.from(binary, c => c.charCodeAt(0))
      const json = new TextDecoder('utf-8').decode(bytes)
      return JSON.parse(json)
    } catch (e) {
      console.error('JWT 디코딩 실패:', e)
      return null
    }
  }

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target
    setFormData(prev => ({
      ...prev,
      [name]: value
    }))
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setLoading(true)
    setError('')

    try {
      const response = await fetch('/api/v1/users/login', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(formData),
      })

      if (response.ok) {
        const result = await response.json()
        console.log('로그인 성공:', result)
        const data = result?.data ?? result
        // 응답에서 토큰 추출 및 저장 (리프레시 토큰은 쿠키에 저장됨)
        const accessToken = data?.accessToken ?? result?.accessToken

        // 액세스 토큰을 메모리에 저장 (리프레시 토큰은 쿠키에 httpOnly로 저장됨)
        if (accessToken) {
          // localStorage에 남아있는 기존 토큰 제거
          localStorage.removeItem('accessToken')
          tokenManager.setTokens(accessToken)
        }

        // JWT에서 사용자 정보 복원하여 저장
        if (accessToken) {
          const payload = decodeJwtPayload(accessToken)
          if (payload) {
            const userInfo = {
              userId: String(payload.sub ?? data?.userId ?? ''),
              email: String(payload.email ?? data?.email ?? ''),
              name: String(payload.name ?? data?.name ?? '')
            }
            localStorage.setItem('userInfo', JSON.stringify(userInfo))
          } else {
            // 페이로드 파싱 실패 시 백엔드 응답의 보조 필드 사용 시도
            if (data?.email || data?.name) {
              const fallback = { userId: String(data?.userId ?? ''), email: data?.email ?? '', name: data?.name ?? '' }
              localStorage.setItem('userInfo', JSON.stringify(fallback))
            }
          }
        }

        // 일반 로그인 플래그 저장
        localStorage.setItem('loginMethod', 'local')
        // 로그인 성공 시 메인 페이지로 리다이렉트
        window.location.href = '/'
      } else {
        const errorData = await response.json()
        setError(errorData.message || '로그인에 실패했습니다.')
      }
    } catch (err) {
      setError('네트워크 오류가 발생했습니다.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{
      minHeight: '100vh',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      background: '#f8fafc'
    }}>
      <div style={{
        width: '100%',
        maxWidth: 400,
        padding: 32,
        background: 'white',
        borderRadius: 12,
        boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1)',
        margin: 16
      }}>
        {/* 헤더 */}
        <div style={{ textAlign: 'center', marginBottom: 32 }}>
          <h1 style={{
            margin: 0,
            fontSize: 28,
            fontWeight: 'bold',
            color: '#1f2937',
            marginBottom: 8
          }}>
            로그인
          </h1>
          <p style={{
            margin: 0,
            color: '#6b7280',
            fontSize: 14
          }}>
            계정에 로그인하여 주식 정보를 확인하세요
          </p>
        </div>

        {/* 에러 메시지 */}
        {error && (
          <div style={{
            padding: 12,
            background: '#fef2f2',
            border: '1px solid #fecaca',
            borderRadius: 6,
            color: '#dc2626',
            fontSize: 14,
            marginBottom: 16
          }}>
            {error}
          </div>
        )}

        {/* 로그인 폼 */}
        <form onSubmit={handleSubmit}>
          <div style={{ marginBottom: 16 }}>
            <label style={{
              display: 'block',
              fontSize: 14,
              fontWeight: '500',
              color: '#374151',
              marginBottom: 6
            }}>
              이메일
            </label>
            <input
              type="email"
              name="email"
              value={formData.email}
              onChange={handleChange}
              required
              style={{
                width: '100%',
                padding: '12px',
                border: '1px solid #d1d5db',
                borderRadius: 6,
                fontSize: 14,
                boxSizing: 'border-box'
              }}
              placeholder="이메일을 입력하세요"
            />
          </div>

          <div style={{ marginBottom: 24 }}>
            <label style={{
              display: 'block',
              fontSize: 14,
              fontWeight: '500',
              color: '#374151',
              marginBottom: 6
            }}>
              비밀번호
            </label>
            <input
              type="password"
              name="password"
              value={formData.password}
              onChange={handleChange}
              required
              style={{
                width: '100%',
                padding: '12px',
                border: '1px solid #d1d5db',
                borderRadius: 6,
                fontSize: 14,
                boxSizing: 'border-box'
              }}
              placeholder="비밀번호를 입력하세요"
            />
          </div>

          <button
            type="submit"
            disabled={loading}
            style={{
              width: '100%',
              padding: '12px',
              background: loading ? '#9ca3af' : '#2962FF',
              color: 'white',
              border: 'none',
              borderRadius: 6,
              fontSize: 14,
              fontWeight: '500',
              cursor: loading ? 'not-allowed' : 'pointer',
              marginBottom: 16
            }}
          >
            {loading ? '로그인 중...' : '로그인'}
          </button>
        </form>

        {/* 구분선 */}
        <div style={{
          display: 'flex',
          alignItems: 'center',
          margin: '16px 0',
          color: '#9ca3af',
          fontSize: 14
        }}>
          <div style={{ flex: 1, height: 1, background: '#e5e7eb' }}></div>
          <span style={{ margin: '0 16px' }}>또는</span>
          <div style={{ flex: 1, height: 1, background: '#e5e7eb' }}></div>
        </div>

        {/* 카카오 로그인 버튼 */}
        <button
          onClick={() => window.location.href = '/oauth2/authorization/kakao'}
          style={{
            width: '100%',
            padding: '12px',
            background: 'linear-gradient(135deg, #FEE500 0%, #FFEB3B 100%)',
            color: '#3C1E1E',
            border: 'none',
            borderRadius: 6,
            fontSize: 14,
            fontWeight: '500',
            cursor: 'pointer',
            marginBottom: 16,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            gap: 8,
            boxShadow: '0 2px 4px rgba(254, 229, 0, 0.2)',
            transition: 'all 0.2s ease'
          }}
          onMouseEnter={(e) => {
            e.currentTarget.style.transform = 'translateY(-1px)'
            e.currentTarget.style.boxShadow = '0 4px 8px rgba(254, 229, 0, 0.3)'
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.transform = 'translateY(0)'
            e.currentTarget.style.boxShadow = '0 2px 4px rgba(254, 229, 0, 0.2)'
          }}
        >
          {/* 카카오 아이콘 */}
          <div style={{
            width: 16,
            height: 16,
            background: '#3C1E1E',
            borderRadius: '50%',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: 10,
            fontWeight: 'bold',
            color: '#FEE500'
          }}>
            K
          </div>
          카카오로 시작하기
        </button>

        {/* 네이버 로그인 버튼 */}
        <button
          onClick={() => window.location.href = '/oauth2/authorization/naver'}
          style={{
            width: '100%',
            padding: '12px',
            background: 'linear-gradient(135deg, #03C75A 0%, #06d566 100%)',
            color: '#ffffff',
            border: 'none',
            borderRadius: 6,
            fontSize: 14,
            fontWeight: '500',
            cursor: 'pointer',
            marginBottom: 16,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            gap: 8,
            boxShadow: '0 2px 4px rgba(3, 199, 90, 0.25)',
            transition: 'all 0.2s ease'
          }}
          onMouseEnter={(e) => {
            e.currentTarget.style.transform = 'translateY(-1px)'
            e.currentTarget.style.boxShadow = '0 4px 8px rgba(3, 199, 90, 0.35)'
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.transform = 'translateY(0)'
            e.currentTarget.style.boxShadow = '0 2px 4px rgba(3, 199, 90, 0.25)'
          }}
        >
          {/* 네이버 아이콘 */}
          <div style={{
            width: 16,
            height: 16,
            background: '#ffffff',
            borderRadius: 4,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: 10,
            fontWeight: 'bold',
            color: '#03C75A'
          }}>
            N
          </div>
          네이버로 시작하기
        </button>

        {/* 회원가입 링크 */}
        <div style={{ textAlign: 'center' }}>
          <span style={{ color: '#6b7280', fontSize: 14 }}>
            계정이 없으신가요?{' '}
          </span>
          <Link
            to="/signup"
            style={{
              color: '#2962FF',
              textDecoration: 'none',
              fontSize: 14,
              fontWeight: '500'
            }}
          >
            회원가입
          </Link>
        </div>

        {/* 메인 페이지로 돌아가기 */}
        <div style={{ textAlign: 'center', marginTop: 16 }}>
          <Link
            to="/"
            style={{
              color: '#6b7280',
              textDecoration: 'none',
              fontSize: 14
            }}
          >
            ← 메인 페이지로 돌아가기
          </Link>
        </div>
      </div>
    </div>
  )
}
