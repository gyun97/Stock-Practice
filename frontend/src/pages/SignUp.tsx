import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'

export default function SignUp() {
  const [formData, setFormData] = useState({
    email: '',
    password: '',
    confirmPassword: '',
    name: ''
  })
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState(false)
  const navigate = useNavigate()

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

    // 비밀번호 확인
    if (formData.password !== formData.confirmPassword) {
      setError('비밀번호가 일치하지 않습니다.')
      setLoading(false)
      return
    }

    // 비밀번호 길이 확인
    if (formData.password.length < 8) {
      setError('비밀번호는 8자 이상이어야 합니다.')
      setLoading(false)
      return
    }

    // 비밀번호 복잡도 확인 (알파벳, 숫자, 특수문자 각각 하나씩 포함)
    const hasLetter = /[a-zA-Z]/.test(formData.password)
    const hasNumber = /[0-9]/.test(formData.password)
    const hasSpecial = /[!@#$%^&*(),.?":{}|<>]/.test(formData.password)
    
    if (!hasLetter || !hasNumber || !hasSpecial) {
      setError('비밀번호는 알파벳, 숫자, 특수문자를 각각 하나씩 포함해야 합니다.')
      setLoading(false)
      return
    }

    try {
      const response = await fetch('/api/v1/users/sign-up', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          email: formData.email,
          password: formData.password,
          name: formData.name
        }),
      })

              if (response.ok || response.status === 302) {
                // 성공 응답 (200) 또는 리다이렉트 응답 (302) 모두 성공으로 처리
                console.log('회원가입 성공:', response.status)
                
                // 회원가입 성공 시 자동 로그인 처리
                try {
                  const result = await response.json()
                  console.log('회원가입 응답:', result)
                  
                  // JWT 토큰을 로컬 스토리지에 저장
                  if (result.data && result.data.accessToken) {
                    localStorage.setItem('accessToken', result.data.accessToken)
                    localStorage.setItem('refreshToken', result.data.refreshToken)
                    localStorage.setItem('userInfo', JSON.stringify({
                      userId: result.data.userId,
                      email: result.data.email,
                      name: result.data.name
                    }))
                    
                    console.log('자동 로그인 완료:', result.data.userId)
                    
                    // 메인 페이지로 리다이렉트
                    navigate('/')
                  } else {
                    // 토큰이 없는 경우 로그인 페이지로 이동
                    setSuccess(true)
                    setTimeout(() => {
                      navigate('/login')
                    }, 2000)
                  }
                } catch (parseError) {
                  console.error('응답 파싱 오류:', parseError)
                  // 파싱 실패 시 로그인 페이지로 이동
                  setSuccess(true)
                  setTimeout(() => {
                    navigate('/login')
                  }, 2000)
                }
      } else {
        // 에러 응답인 경우에만 JSON 파싱 시도
        try {
          const errorData = await response.json()
          setError(errorData.message || '회원가입에 실패했습니다.')
        } catch {
          setError(`회원가입에 실패했습니다. (상태 코드: ${response.status})`)
        }
      }
    } catch (err) {
      console.error('회원가입 요청 실패:', err)
      setError('서버와 통신 중 오류가 발생했습니다.')
    } finally {
      setLoading(false)
    }
  }

  if (success) {
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
          margin: 16,
          textAlign: 'center'
        }}>
          <div style={{ fontSize: 48, marginBottom: 16 }}>✅</div>
          <h1 style={{ 
            margin: 0, 
            fontSize: 24, 
            fontWeight: 'bold', 
            color: '#059669',
            marginBottom: 8
          }}>
            회원가입 완료!
          </h1>
          <p style={{ 
            margin: 0, 
            color: '#6b7280', 
            fontSize: 14,
            marginBottom: 16
          }}>
            로그인 페이지로 이동합니다...
          </p>
          <Link 
            to="/login" 
            style={{
              display: 'inline-block',
              padding: '8px 16px',
              background: '#2962FF',
              color: 'white',
              textDecoration: 'none',
              borderRadius: 6,
              fontSize: 14,
              fontWeight: '500'
            }}
          >
            로그인하기
          </Link>
        </div>
      </div>
    )
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
            회원가입
          </h1>
          <p style={{ 
            margin: 0, 
            color: '#6b7280', 
            fontSize: 14 
          }}>
            새로운 계정을 만들어 주식 정보를 확인하세요
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

        {/* 회원가입 폼 */}
        <form onSubmit={handleSubmit}>
          <div style={{ marginBottom: 16 }}>
            <label style={{ 
              display: 'block', 
              fontSize: 14, 
              fontWeight: '500', 
              color: '#374151',
              marginBottom: 6
            }}>
              이름
            </label>
            <input
              type="text"
              name="name"
              value={formData.name}
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
              placeholder="이름을 입력하세요"
            />
          </div>

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

          <div style={{ marginBottom: 16 }}>
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
              placeholder="8자 이상, 알파벳+숫자+특수문자 포함"
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
              비밀번호 확인
            </label>
            <input
              type="password"
              name="confirmPassword"
              value={formData.confirmPassword}
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
              placeholder="비밀번호를 다시 입력하세요"
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
            {loading ? '회원가입 중...' : '회원가입'}
          </button>
        </form>

        {/* 로그인 링크 */}
        <div style={{ textAlign: 'center' }}>
          <span style={{ color: '#6b7280', fontSize: 14 }}>
            이미 계정이 있으신가요?{' '}
          </span>
          <Link 
            to="/login" 
            style={{ 
              color: '#2962FF', 
              textDecoration: 'none', 
              fontSize: 14,
              fontWeight: '500'
            }}
          >
            로그인
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