import { useState } from 'react'
import { Link } from 'react-router-dom'
import { tokenManager } from '../lib/tokenManager'

export default function Login() {
  const [error, setError] = useState('')

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

        {/* 소셜 로그인 버튼 영역 */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
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
              marginBottom: 0,
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
              marginBottom: 0,
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
