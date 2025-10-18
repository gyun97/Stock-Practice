import { useState } from 'react'
import { Link } from 'react-router-dom'

export default function Login() {
  const [formData, setFormData] = useState({
    email: '',
    password: ''
  })
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

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
