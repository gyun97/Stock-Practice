import { useState, useEffect, useRef } from 'react'
import { Link } from 'react-router-dom'
import { createStompClient } from '../lib/socket'
import { tokenManager } from '../lib/tokenManager'

type UserInfo = {
  userId: number
  email: string
  name: string
  balance: number
}

type PortfolioInfo = {
  balance: number
  stockAsset: number
  totalAsset: number
  holdCount: number
  totalQuantity: number
  returnRate: number
}

type UserStock = {
  ticker: string
  companyName: string
  totalQuantity: number
  avgPrice: number
  currentPrice: number
  currentAsset: number
  profitLoss: number
  returnRate: number
}

export default function MyPage() {
  const [userInfo, setUserInfo] = useState<UserInfo | null>(null)
  const [portfolioInfo, setPortfolioInfo] = useState<PortfolioInfo | null>(null)
  const [userStocks, setUserStocks] = useState<UserStock[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [serverStatus, setServerStatus] = useState<'checking' | 'online' | 'offline'>('checking')
  const [pwModalOpen, setPwModalOpen] = useState(false)
  const [pwForm, setPwForm] = useState({ currentPassword: '', newPassword: '', checkNewPassword: '' })
  const [pwSubmitting, setPwSubmitting] = useState(false)
  const [pwMessage, setPwMessage] = useState('')
  const [deleteModalOpen, setDeleteModalOpen] = useState(false)
  const [deleteSubmitting, setDeleteSubmitting] = useState(false)
  const [deleteMessage, setDeleteMessage] = useState('')
  const [notifications, setNotifications] = useState<Array<{id: number, message: string, type: string, timestamp: Date}>>([])
  
  // WebSocket 연결을 위한 ref
  const stompRef = useRef<any>(null)
  const canvasRef = useRef<HTMLCanvasElement>(null)

  // 총 수익 계산 함수
  const calculateTotalProfit = () => {
    if (!userStocks.length) return 0
    return userStocks.reduce((total, stock) => total + stock.profitLoss, 0)
  }

  // 도넛 그래프 그리기 함수
  const drawDonutChart = () => {
    const canvas = canvasRef.current
    if (!canvas || !userStocks.length) return

    const ctx = canvas.getContext('2d')
    if (!ctx) return

    // 캔버스 크기 설정
    canvas.width = 300
    canvas.height = 300

    const centerX = canvas.width / 2
    const centerY = canvas.height / 2
    const radius = 100
    const innerRadius = 60

    // 전체 주식 자산 계산
    const totalStockAsset = userStocks.reduce((sum, stock) => sum + stock.currentAsset, 0)
    
    if (totalStockAsset === 0) return

    // 색상 팔레트
    const colors = [
      '#3B82F6', '#EF4444', '#10B981', '#F59E0B', '#8B5CF6',
      '#06B6D4', '#84CC16', '#F97316', '#EC4899', '#6366F1'
    ]

    let currentAngle = -Math.PI / 2 // 시작 각도 (12시 방향)

    // 각 주식별 비중 계산 및 그리기
    userStocks.forEach((stock, index) => {
      const percentage = (stock.currentAsset / totalStockAsset) * 100
      
      // 비중이 1% 미만이면 건너뛰기
      if (percentage < 1) return

      const sliceAngle = (stock.currentAsset / totalStockAsset) * 2 * Math.PI
      
      // 호 그리기
      ctx.beginPath()
      ctx.arc(centerX, centerY, radius, currentAngle, currentAngle + sliceAngle)
      ctx.arc(centerX, centerY, innerRadius, currentAngle + sliceAngle, currentAngle, true)
      ctx.closePath()
      
      ctx.fillStyle = colors[index % colors.length]
      ctx.fill()
      
      // 텍스트 라벨 그리기 (비중이 3% 이상인 경우만)
      if (percentage >= 3) {
        const labelAngle = currentAngle + sliceAngle / 2
        const labelRadius = (radius + innerRadius) / 2
        const labelX = centerX + Math.cos(labelAngle) * labelRadius
        const labelY = centerY + Math.sin(labelAngle) * labelRadius
        
        ctx.fillStyle = 'white'
        ctx.font = 'bold 10px Arial'
        ctx.textAlign = 'center'
        ctx.textBaseline = 'middle'
        
        // 회사 이름 표시 (간단한 이름으로 축약)
        const shortName = stock.companyName.length > 4 ? 
          stock.companyName.substring(0, 4) : stock.companyName
        ctx.fillText(shortName, labelX, labelY - 5)
        
        // 비중 표시
        ctx.font = 'bold 8px Arial'
        ctx.fillText(`${percentage.toFixed(1)}%`, labelX, labelY + 8)
      }
      
      currentAngle += sliceAngle
    })

    // 중앙에 총 자산 표시
    ctx.fillStyle = '#374151'
    ctx.font = 'bold 16px Arial'
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
    ctx.fillText('총 주식 자산', centerX, centerY - 10)
    
    ctx.fillStyle = '#6B7280'
    ctx.font = '14px Arial'
    ctx.fillText(`${totalStockAsset.toLocaleString()}원`, centerX, centerY + 10)
  }

  // 포트폴리오 수익률 실시간 업데이트 핸들러
  const onPortfolioUpdate = (payload: any, raw: string) => {
    console.log('포트폴리오 업데이트:', { payload, raw })
    
    try {
      // JSON 형태의 데이터인지 확인
      if (typeof raw === 'string' && raw.startsWith('{')) {
        const portfolioData = JSON.parse(raw)
        console.log('포트폴리오 JSON 데이터:', portfolioData)
        
        // portfolioInfo가 null이어도 WebSocket 데이터로 업데이트
        setPortfolioInfo(prev => {
          if (prev) {
            return {
              ...prev,
              returnRate: portfolioData.returnRate || prev.returnRate,
              stockAsset: portfolioData.stockAsset || prev.stockAsset,
              totalAsset: portfolioData.totalAsset || prev.totalAsset,
              balance: portfolioData.balance || prev.balance
            }
          } else {
            // portfolioInfo가 null인 경우 WebSocket 데이터로 새로 생성
            return {
              totalAsset: portfolioData.totalAsset || 0,
              returnRate: portfolioData.returnRate || 0,
              balance: portfolioData.balance || 0,
              stockAsset: portfolioData.stockAsset || 0,
              holdCount: 0,
              totalQuantity: 0
            }
          }
        })
      } else if (typeof payload === 'number') {
        // 기존 숫자 형태의 수익률만 업데이트
        setPortfolioInfo(prev => {
          if (prev) {
            return {
              ...prev,
              returnRate: payload
            }
          } else {
            // portfolioInfo가 null인 경우 기본값으로 생성
            return {
              totalAsset: 0,
              returnRate: payload,
              balance: 0,
              stockAsset: 0,
              holdCount: 0,
              totalQuantity: 0
            }
          }
        })
      }
    } catch (error) {
      console.error('포트폴리오 업데이트 파싱 오류:', error)
      // 파싱 실패 시 기존 로직으로 폴백
      if (typeof payload === 'number') {
        setPortfolioInfo(prev => {
          if (prev) {
            return {
              ...prev,
              returnRate: payload
            }
          } else {
            // portfolioInfo가 null인 경우 기본값으로 생성
            return {
              totalAsset: 0,
              returnRate: payload,
              balance: 0,
              stockAsset: 0,
              holdCount: 0,
              totalQuantity: 0
            }
          }
        })
      }
    }
  }

  useEffect(() => {
    const checkServerStatus = async () => {
      try {
        const response = await fetch('/api/v1/stocks', { method: 'HEAD' })
        setServerStatus(response.ok ? 'online' : 'offline')
      } catch {
        setServerStatus('offline')
      }
    }

    const fetchUserInfo = async () => {
      try {
        // 먼저 서버 상태 확인
        await checkServerStatus()
        
        const storedUserInfo = localStorage.getItem('userInfo')
        if (!storedUserInfo) {
          setError('로그인이 필요합니다.')
          setLoading(false)
          return
        }

        const parsedUserInfo = JSON.parse(storedUserInfo)
        const accessToken = tokenManager.getAccessToken()
        
        console.log('마이페이지 API 호출:', `/api/v1/users/${parsedUserInfo.userId}`)
        console.log('Access Token:', accessToken ? '존재함' : '없음 (자동 재발급 시도)')
        
        const response = await tokenManager.authenticatedFetch(`/api/v1/users/${parsedUserInfo.userId}`)

        console.log('API 응답 상태:', response.status)
        console.log('API 응답 OK:', response.ok)

        if (response.ok) {
          const result = await response.json()
          console.log('API 응답 데이터:', result)
          setUserInfo(result.data)
          
          // 포트폴리오 정보도 함께 가져오기
          await fetchPortfolioInfo(parsedUserInfo.userId)
          await fetchUserStocks(parsedUserInfo.userId)
        } else if (response.status === 401) {
          // 토큰 갱신이 실패한 경우만 401이 됨
          setError('로그인이 만료되었습니다. 다시 로그인해주세요.')
          tokenManager.clearTokens()
          setTimeout(() => {
            window.location.href = '/login'
          }, 2000)
        } else if (response.status === 0 || !response.ok) {
          setError('백엔드 서버에 연결할 수 없습니다. 서버가 실행 중인지 확인해주세요.')
        } else {
          const errorText = await response.text()
          console.error('API 오류 응답:', errorText)
          setError(`사용자 정보를 불러오는데 실패했습니다. (상태 코드: ${response.status})`)
        }
      } catch (err) {
        console.error('마이페이지 API 호출 오류:', err)
        console.error('에러 타입:', err instanceof Error ? err.constructor.name : typeof err)
        console.error('에러 메시지:', err instanceof Error ? err.message : String(err))
        
        // 토큰 갱신 실패 에러 처리
        if (err instanceof Error && err.message.includes('토큰 갱신')) {
          setError('로그인이 만료되었습니다. 다시 로그인해주세요.')
          tokenManager.clearTokens()
          setTimeout(() => {
            window.location.href = '/login'
          }, 2000)
        } else if (err instanceof TypeError && err.message.includes('fetch')) {
          setError('백엔드 서버에 연결할 수 없습니다. 서버가 실행 중인지 확인해주세요.')
        } else {
          setError(`네트워크 오류가 발생했습니다: ${err instanceof Error ? err.message : '알 수 없는 오류'}`)
        }
      } finally {
        setLoading(false)
      }
    }

    const fetchPortfolioInfo = async (userId: number) => {
      try {
        console.log('포트폴리오 API 호출:', `/api/v1/portfolios/users/${userId}`)
        
        const response = await tokenManager.authenticatedFetch(`/api/v1/portfolios/users/${userId}`)

        console.log('포트폴리오 API 응답 상태:', response.status)

        if (response.ok) {
          const result = await response.json()
          console.log('포트폴리오 API 응답 데이터:', result)
          setPortfolioInfo(result.data)
        } else {
          console.error('포트폴리오 정보를 불러오는데 실패했습니다. (상태 코드:', response.status, ')')
          // 포트폴리오 정보가 없어도 에러로 처리하지 않음
        }
      } catch (err) {
        console.error('포트폴리오 API 호출 오류:', err)
        // 포트폴리오 정보가 없어도 에러로 처리하지 않음
      }
    }

    const fetchUserStocks = async (userId: number) => {
      try {
        console.log('보유 주식 API 호출:', `/api/v1/userstocks`)
        
        const response = await tokenManager.authenticatedFetch(`/api/v1/userstocks`)

        console.log('보유 주식 API 응답 상태:', response.status)

        if (response.ok) {
          const result = await response.json()
          console.log('보유 주식 API 응답 데이터:', result)
          setUserStocks(result.data || [])
        } else {
          console.error('보유 주식 정보를 불러오는데 실패했습니다. (상태 코드:', response.status, ')')
          setUserStocks([])
        }
      } catch (err) {
        console.error('보유 주식 API 호출 오류:', err)
        setUserStocks([])
      }
    }

    fetchUserInfo()
  }, [])

  // 도넛 그래프 그리기 (userStocks 변경 시)
  useEffect(() => {
    if (userStocks.length > 0) {
      // DOM이 업데이트된 후 그래프 그리기
      setTimeout(() => {
        drawDonutChart()
      }, 100)
    }
  }, [userStocks])

  // 브라우저 알림 권한 요청
  useEffect(() => {
    if ('Notification' in window && Notification.permission === 'default') {
      Notification.requestPermission()
    }
  }, [])

  // WebSocket 연결 설정 (포트폴리오 수익률 실시간 업데이트)
  useEffect(() => {
    if (userInfo) {
      console.log('마이페이지 WebSocket 연결 시작')
      const client = createStompClient(onPortfolioUpdate)
      
      // 포트폴리오 업데이트 토픽 구독
      client.onConnect = () => {
        // 사용자별 포트폴리오 업데이트 구독
        const userId = userInfo.userId
        console.log('WebSocket 구독할 사용자 ID:', userId)
        client.subscribe(`/topic/portfolio/updates/${userId}`, (msg) => {
          const raw = msg.body
          console.log('포트폴리오 WebSocket 메시지 수신:', raw)
          
          try {
            // JSON 형태인지 확인
            if (raw.startsWith('{')) {
              const portfolioData = JSON.parse(raw)
              onPortfolioUpdate(portfolioData, raw)
            } else {
              // 숫자 형태인 경우
              const returnRate = parseFloat(raw)
              onPortfolioUpdate(returnRate, raw)
            }
          } catch (error) {
            console.error('포트폴리오 업데이트 파싱 오류:', error)
            // 파싱 실패 시 원본 데이터로 시도
            onPortfolioUpdate(raw, raw)
          }
        })
        
        // 사용자별 보유 주식 업데이트 구독
        client.subscribe(`/topic/userstock/updates/${userId}`, (msg) => {
          const raw = msg.body
          console.log('보유 주식 WebSocket 메시지 수신:', raw)
          
          try {
            const userStockData = JSON.parse(raw)
            console.log('보유 주식 업데이트 데이터:', userStockData)
            
            // 사용자별 토픽이므로 바로 업데이트
            setUserStocks(userStockData.userStocks)
          } catch (error) {
            console.error('보유 주식 업데이트 파싱 오류:', error)
          }
        })
        
        // 주문 알림 구독
        client.subscribe(`/topic/order/notifications/${userId}`, (msg) => {
          const raw = msg.body
          console.log('주문 알림 WebSocket 메시지 수신:', raw)
          
          try {
            const notificationData = JSON.parse(raw)
            console.log('주문 알림 데이터:', notificationData)
            
            // 알림 추가
            setNotifications(prev => {
              const newNotification = {
                id: Date.now(),
                message: notificationData.message || '',
                type: notificationData.type || '',
                timestamp: new Date()
              }
              return [newNotification, ...prev].slice(0, 10) // 최대 10개 유지
            })
            
            // 브라우저 알림 (사용자가 페이지에 있을 때)
            if ('Notification' in window && Notification.permission === 'granted') {
              new Notification('주문 체결 알림', {
                body: notificationData.message,
                icon: '/favicon.ico'
              })
            }
          } catch (error) {
            console.error('주문 알림 파싱 오류:', error)
          }
        })
      }
      
      client.activate()
      stompRef.current = client
      
      return () => {
        console.log('마이페이지 WebSocket 연결 해제')
        client.deactivate()
      }
    }
  }, [userInfo])

  const openPwModal = () => {
    setPwMessage('')
    setPwForm({ currentPassword: '', newPassword: '', checkNewPassword: '' })
    setPwModalOpen(true)
  }

  const closePwModal = () => {
    if (!pwSubmitting) setPwModalOpen(false)
  }

  const openDeleteModal = () => {
    setDeleteMessage('')
    setDeleteModalOpen(true)
  }

  const closeDeleteModal = () => {
    if (!deleteSubmitting) setDeleteModalOpen(false)
  }

  const handlePwChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target
    setPwForm(prev => ({ ...prev, [name]: value }))
  }

  const submitPasswordChange = async (e: React.FormEvent) => {
    e.preventDefault()
    setPwMessage('')

    if (!pwForm.currentPassword || !pwForm.newPassword || !pwForm.checkNewPassword) {
      setPwMessage('모든 항목을 입력해 주세요.')
      return
    }
    if (pwForm.newPassword !== pwForm.checkNewPassword) {
      setPwMessage('새 비밀번호가 일치하지 않습니다.')
      return
    }
    if (pwForm.newPassword.length < 8) {
      setPwMessage('새 비밀번호는 8자 이상이어야 합니다.')
      return
    }

    const accessToken = localStorage.getItem('accessToken')
    if (!accessToken) {
      setPwMessage('로그인이 필요합니다.')
      return
    }

    try {
      setPwSubmitting(true)
      const res = await fetch('/api/v1/users/password', {
        method: 'PATCH',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${accessToken}`
        },
        body: JSON.stringify(pwForm)
      })

      if (res.ok) {
        setPwMessage('비밀번호가 변경되었습니다.')
        setTimeout(() => {
          setPwModalOpen(false)
          setPwMessage('')
        }, 800)
      } else if (res.status === 400 || res.status === 401) {
        const text = await res.text()
        setPwMessage(text || '비밀번호 변경에 실패했습니다.')
      } else {
        setPwMessage(`비밀번호 변경 실패 (상태 코드: ${res.status})`)
      }
    } catch (err) {
      setPwMessage('네트워크 오류가 발생했습니다.')
    } finally {
      setPwSubmitting(false)
    }
  }

  const handleDeleteAccount = async () => {
    setDeleteMessage('')

    const accessToken = localStorage.getItem('accessToken')
    const userInfo = localStorage.getItem('userInfo')
    
    if (!accessToken || !userInfo) {
      setDeleteMessage('로그인이 필요합니다.')
      return
    }

    try {
      setDeleteSubmitting(true)
      const parsedUserInfo = JSON.parse(userInfo)
      const res = await fetch(`/api/v1/users/${parsedUserInfo.userId}`, {
        method: 'DELETE',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${accessToken}`
        }
      })

      if (res.ok) {
        setDeleteMessage('회원탈퇴가 완료되었습니다.')
        // 자동 로그아웃
        setTimeout(() => {
          localStorage.removeItem('userInfo')
          localStorage.removeItem('accessToken')
          localStorage.removeItem('refreshToken')
          localStorage.removeItem('loginMethod')
          window.location.href = '/'
        }, 1500)
      } else if (res.status === 400 || res.status === 401) {
        const text = await res.text()
        setDeleteMessage(text || '회원탈퇴에 실패했습니다.')
      } else {
        setDeleteMessage(`회원탈퇴 실패 (상태 코드: ${res.status})`)
      }
    } catch (err) {
      setDeleteMessage('네트워크 오류가 발생했습니다.')
    } finally {
      setDeleteSubmitting(false)
    }
  }

  if (loading) {
    return (
      <div style={{ 
        minHeight: '100vh', 
        display: 'flex', 
        alignItems: 'center', 
        justifyContent: 'center',
        background: '#f8fafc'
      }}>
        <div style={{ textAlign: 'center' }}>
          <div style={{ 
            width: 40, 
            height: 40, 
            border: '4px solid #e5e7eb', 
            borderTop: '4px solid #2962FF', 
            borderRadius: '50%', 
            animation: 'spin 1s linear infinite',
            margin: '0 auto 16px'
          }}></div>
          <p style={{ color: '#6b7280', fontSize: 14 }}>사용자 정보를 불러오는 중...</p>
        </div>
        <style>{`
          @keyframes spin {
            0% { transform: rotate(0deg); }
            100% { transform: rotate(360deg); }
          }
        `}</style>
      </div>
    )
  }

  if (error) {
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
          <div style={{
            padding: 12,
            background: serverStatus === 'offline' ? '#fef2f2' : '#fef2f2',
            border: serverStatus === 'offline' ? '1px solid #fecaca' : '1px solid #fecaca',
            borderRadius: 6,
            color: serverStatus === 'offline' ? '#dc2626' : '#dc2626',
            fontSize: 14,
            marginBottom: 16
          }}>
            {error}
          </div>
          
          {serverStatus === 'offline' && (
            <div style={{
              padding: 12,
              background: '#f0f9ff',
              border: '1px solid #bae6fd',
              borderRadius: 6,
              color: '#0369a1',
              fontSize: 12,
              marginBottom: 16,
              textAlign: 'left'
            }}>
              <strong>해결 방법:</strong>
              <ul style={{ margin: '8px 0 0 0', paddingLeft: 16 }}>
                <li>백엔드 서버가 실행 중인지 확인</li>
                <li>터미널에서 <code>./gradlew bootRun</code> 실행</li>
                <li>포트 8080이 사용 가능한지 확인</li>
              </ul>
            </div>
          )}
          
          <div style={{ display: 'flex', gap: 12, justifyContent: 'center' }}>
            <Link 
              to="/login" 
              style={{
                padding: '12px 24px',
                background: '#2962FF',
                color: 'white',
                textDecoration: 'none',
                borderRadius: 6,
                fontSize: 14,
                fontWeight: '500',
                display: 'inline-block'
              }}
            >
              로그인하러 가기
            </Link>
            
            <button
              onClick={() => window.location.reload()}
              style={{
                padding: '12px 24px',
                background: '#6b7280',
                color: 'white',
                border: 'none',
                borderRadius: 6,
                fontSize: 14,
                fontWeight: '500',
                cursor: 'pointer'
              }}
            >
              새로고침
            </button>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div style={{ 
      minHeight: '100vh', 
      background: '#f8fafc',
      padding: '20px 0'
    }}>
      <div style={{ 
        maxWidth: 600, 
        margin: '0 auto', 
        padding: '0 16px' 
      }}>
        {/* 헤더 */}
        <div style={{ 
          display: 'flex', 
          justifyContent: 'space-between', 
          alignItems: 'center', 
          marginBottom: 32 
        }}>
          <div>
            <h1 style={{ 
              margin: 0, 
              fontSize: 28, 
              fontWeight: 'bold', 
              color: '#1f2937' 
            }}>
              마이페이지
            </h1>
            {/* 서버 상태 표시 */}
            <div style={{ 
              display: 'flex', 
              alignItems: 'center', 
              gap: 8, 
              marginTop: 8 
            }}>
              <div style={{
                width: 8,
                height: 8,
                borderRadius: '50%',
                backgroundColor: serverStatus === 'online' ? '#10b981' : 
                                serverStatus === 'offline' ? '#ef4444' : '#f59e0b'
              }}></div>
              <span style={{ 
                fontSize: 12, 
                color: serverStatus === 'online' ? '#10b981' : 
                       serverStatus === 'offline' ? '#ef4444' : '#f59e0b'
              }}>
                {serverStatus === 'online' ? '서버 연결됨' : 
                 serverStatus === 'offline' ? '서버 연결 안됨' : '서버 상태 확인 중...'}
              </span>
            </div>
          </div>
          <Link 
            to="/" 
            style={{ 
              color: '#6b7280', 
              textDecoration: 'none', 
              fontSize: 14 
            }}
          >
            ← 메인으로 돌아가기
          </Link>
        </div>

        {/* 주문 알림 표시 */}
        {notifications.length > 0 && (
          <div style={{
            background: 'white',
            borderRadius: 12,
            boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1)',
            padding: 16,
            marginBottom: 24,
            maxHeight: 200,
            overflowY: 'auto'
          }}>
            <div style={{ 
              display: 'flex', 
              justifyContent: 'space-between', 
              alignItems: 'center',
              marginBottom: 12 
            }}>
              <h3 style={{ 
                margin: 0, 
                fontSize: 16, 
                fontWeight: '600', 
                color: '#1f2937' 
              }}>
                📢 주문 체결 알림
              </h3>
              <button
                onClick={() => setNotifications([])}
                style={{
                  background: 'none',
                  border: 'none',
                  color: '#6b7280',
                  fontSize: 12,
                  cursor: 'pointer',
                  padding: '4px 8px'
                }}
              >
                전체 삭제
              </button>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              {notifications.map((notification, index) => (
                <div
                  key={notification.id}
                  style={{
                    padding: 12,
                    background: notification.type === 'BUY' ? '#eff6ff' : '#fef3c7',
                    borderRadius: 8,
                    borderLeft: `4px solid ${notification.type === 'BUY' ? '#3b82f6' : '#f59e0b'}`
                  }}
                >
                  <div style={{ fontSize: 14, color: '#1f2937' }}>
                    {notification.message}
                  </div>
                  <div style={{ 
                    fontSize: 12, 
                    color: '#6b7280', 
                    marginTop: 4 
                  }}>
                    {new Date(notification.timestamp).toLocaleString('ko-KR')}
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* 사용자 정보 카드 */}
        <div style={{
          background: 'white',
          borderRadius: 12,
          boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1)',
          padding: 32,
          marginBottom: 24
        }}>
          <h2 style={{ 
            margin: '0 0 24px 0', 
            fontSize: 20, 
            fontWeight: '600', 
            color: '#1f2937' 
          }}>
            내 정보
          </h2>
          
          <div style={{ display: 'grid', gap: 16 }}>
            <div style={{ 
              display: 'flex', 
              justifyContent: 'space-between', 
              alignItems: 'center',
              padding: '12px 0',
              borderBottom: '1px solid #f1f5f9'
            }}>
              <span style={{ fontSize: 14, color: '#6b7280' }}>이름</span>
              <span style={{ fontSize: 16, fontWeight: '500', color: '#1f2937' }}>
                {userInfo?.name}
              </span>
            </div>
            
            <div style={{ 
              display: 'flex', 
              justifyContent: 'space-between', 
              alignItems: 'center',
              padding: '12px 0'
            }}>
              <span style={{ fontSize: 14, color: '#6b7280' }}>이메일</span>
              <span style={{ fontSize: 16, fontWeight: '500', color: '#1f2937' }}>
                {userInfo?.email}
              </span>
            </div>
          </div>
        </div>

        {/* 포트폴리오 현황 카드 */}
        {portfolioInfo && (
          <div style={{
            background: 'white',
            borderRadius: 12,
            boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1)',
            padding: 32,
            marginBottom: 24
          }}>
            <h2 style={{ 
              margin: '0 0 24px 0', 
              fontSize: 20, 
              fontWeight: '600', 
              color: '#1f2937' 
            }}>
              포트폴리오 현황
            </h2>
            
            {/* 총 자산과 수익률 */}
            <div style={{
              background: '#f8fafc',
              borderRadius: 8,
              padding: 20,
              marginBottom: 20,
              border: '1px solid #e2e8f0'
            }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
                <span style={{ fontSize: 14, color: '#6b7280' }}>총 자산</span>
                <span style={{ fontSize: 24, fontWeight: 'bold', color: '#1f2937' }}>
                  {(portfolioInfo.totalAsset || 0).toLocaleString()}원
                </span>
              </div>
              {/* 총 수익 */}
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span style={{ fontSize: 14, color: '#6b7280' }}>총 수익</span>
                <span style={{ 
                  fontSize: 18, 
                  fontWeight: '600', 
                  color: calculateTotalProfit() >= 0 ? '#dc2626' : '#2563eb'
                }}>
                  {calculateTotalProfit() >= 0 ? '+' : ''}{calculateTotalProfit().toLocaleString()}원
                </span>
              </div>
              
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 12 }}>
                <span style={{ fontSize: 14, color: '#6b7280' }}>수익률</span>
                <span style={{ 
                  fontSize: 18, 
                  fontWeight: '600', 
                  color: (portfolioInfo.returnRate || 0) >= 0 ? '#dc2626' : '#2563eb'
                }}>
                  {(portfolioInfo.returnRate || 0) >= 0 ? '+' : ''}{(portfolioInfo.returnRate || 0).toFixed(2)}%
                </span>
              </div>
            </div>

            {/* 자산 구성 */}
            <div style={{ display: 'grid', gap: 16 }}>
              <div style={{ 
                display: 'flex', 
                justifyContent: 'space-between', 
                alignItems: 'center',
                padding: '12px 0',
                borderBottom: '1px solid #f1f5f9'
              }}>
                <span style={{ fontSize: 14, color: '#6b7280' }}>현금 잔액</span>
                <span style={{ fontSize: 16, fontWeight: '500', color: '#059669' }}>
                  {(portfolioInfo.balance || 0).toLocaleString()}원
                </span>
              </div>
              
              <div style={{ 
                display: 'flex', 
                justifyContent: 'space-between', 
                alignItems: 'center',
                padding: '12px 0',
                borderBottom: '1px solid #f1f5f9'
              }}>
                <span style={{ fontSize: 14, color: '#6b7280' }}>보유 주식 총액</span>
                <span style={{ fontSize: 16, fontWeight: '500', color: '#1f2937' }}>
                  {(portfolioInfo.stockAsset || 0).toLocaleString()}원
                </span>
              </div>
              
              <div style={{ 
                display: 'flex', 
                justifyContent: 'space-between', 
                alignItems: 'center',
                padding: '12px 0',
                borderBottom: '1px solid #f1f5f9'
              }}>
                <span style={{ fontSize: 14, color: '#6b7280' }}>보유 종목 수</span>
                <span style={{ fontSize: 16, fontWeight: '500', color: '#1f2937' }}>
                  {portfolioInfo.holdCount || 0}개
                </span>
              </div>
              
              <div style={{ 
                display: 'flex', 
                justifyContent: 'space-between', 
                alignItems: 'center',
                padding: '12px 0'
              }}>
                <span style={{ fontSize: 14, color: '#6b7280' }}>총 보유 주식 수량</span>
                <span style={{ fontSize: 16, fontWeight: '500', color: '#1f2937' }}>
                  {(portfolioInfo.totalQuantity || 0).toLocaleString()}주
                </span>
              </div>
            </div>
          </div>
        )}

        {/* 보유 주식 현황 */}
        {portfolioInfo && (
          <div style={{
            background: 'white',
            borderRadius: 12,
            boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1)',
            padding: 24,
            marginBottom: 24
          }}>
            <h2 style={{ 
              margin: '0 0 20px 0', 
              fontSize: 18, 
              fontWeight: '600', 
              color: '#1f2937',
              borderBottom: '2px solid #2962FF',
              paddingBottom: 8
            }}>
              보유 주식 현황
            </h2>
            
            {/* 주식 비중 도넛 그래프 */}
            {userStocks.length > 0 && (
              <div style={{ 
                display: 'flex', 
                justifyContent: 'center', 
                marginBottom: 20,
                padding: '20px 0',
                borderBottom: '1px solid #e5e7eb'
              }}>
                <canvas 
                  ref={canvasRef}
                  style={{
                    border: '1px solid #e5e7eb',
                    borderRadius: 8,
                    background: 'white'
                  }}
                />
              </div>
            )}
            
            {userStocks.length > 0 ? (
              <div style={{ overflowX: 'auto' }}>
                <table style={{ 
                  width: '100%', 
                  borderCollapse: 'collapse',
                  whiteSpace: 'nowrap'
                }}>
                  <thead>
                    <tr style={{ background: '#f8fafc' }}>
                      <th style={{ 
                        padding: '12px 8px', 
                        textAlign: 'left', 
                        fontSize: 14, 
                        fontWeight: '600', 
                        color: '#374151',
                        borderBottom: '2px solid #e5e7eb',
                        whiteSpace: 'nowrap'
                      }}>종목명</th>
                      <th style={{ 
                        padding: '12px 8px', 
                        textAlign: 'right', 
                        fontSize: 14, 
                        fontWeight: '600', 
                        color: '#374151',
                        borderBottom: '2px solid #e5e7eb',
                        whiteSpace: 'nowrap'
                      }}>보유수량</th>
                      <th style={{ 
                        padding: '12px 8px', 
                        textAlign: 'right', 
                        fontSize: 14, 
                        fontWeight: '600', 
                        color: '#374151',
                        borderBottom: '2px solid #e5e7eb',
                        whiteSpace: 'nowrap'
                      }}>총 구매액</th>
                      <th style={{ 
                        padding: '12px 8px', 
                        textAlign: 'right', 
                        fontSize: 14, 
                        fontWeight: '600', 
                        color: '#374151',
                        borderBottom: '2px solid #e5e7eb',
                        whiteSpace: 'nowrap'
                      }}>현재자산</th>
                      <th style={{ 
                        padding: '12px 8px', 
                        textAlign: 'right', 
                        fontSize: 14, 
                        fontWeight: '600', 
                        color: '#374151',
                        borderBottom: '2px solid #e5e7eb',
                        whiteSpace: 'nowrap'
                      }}>손익</th>
                      <th style={{ 
                        padding: '12px 8px', 
                        textAlign: 'right', 
                        fontSize: 14, 
                        fontWeight: '600', 
                        color: '#374151',
                        borderBottom: '2px solid #e5e7eb',
                        whiteSpace: 'nowrap'
                      }}>수익률</th>
                    </tr>
                  </thead>
                  <tbody>
                    {userStocks.map((stock, index) => (
                      <tr key={`${stock.ticker}-${index}`} style={{ 
                        borderBottom: '1px solid #f3f4f6'
                      }}>
                        <td style={{ 
                          padding: '12px 8px', 
                          fontSize: 14, 
                          color: '#1f2937',
                          fontWeight: '500',
                          whiteSpace: 'nowrap'
                        }}>{stock.companyName}</td>
                        <td style={{ 
                          padding: '12px 8px', 
                          textAlign: 'right', 
                          fontSize: 14, 
                          color: '#374151',
                          whiteSpace: 'nowrap'
                        }}>{stock.totalQuantity.toLocaleString()}주</td>
                        <td style={{ 
                          padding: '12px 8px', 
                          textAlign: 'right', 
                          fontSize: 14, 
                          color: '#374151',
                          whiteSpace: 'nowrap'
                        }}>{(stock.avgPrice * stock.totalQuantity).toLocaleString()}원</td>
                        <td style={{ 
                          padding: '12px 8px', 
                          textAlign: 'right', 
                          fontSize: 14, 
                          color: '#374151',
                          whiteSpace: 'nowrap'
                        }}>{stock.currentAsset.toLocaleString()}원</td>
                        <td style={{ 
                          padding: '12px 8px', 
                          textAlign: 'right', 
                          fontSize: 14, 
                          fontWeight: '600',
                          color: stock.profitLoss >= 0 ? '#dc2626' : '#2563eb',
                          whiteSpace: 'nowrap'
                        }}>{stock.profitLoss >= 0 ? '+' : ''}{stock.profitLoss.toLocaleString()}원</td>
                        <td style={{ 
                          padding: '12px 8px', 
                          textAlign: 'right', 
                          fontSize: 14, 
                          fontWeight: '600',
                          color: stock.returnRate >= 0 ? '#dc2626' : '#2563eb',
                          whiteSpace: 'nowrap'
                        }}>{stock.returnRate >= 0 ? '+' : ''}{stock.returnRate.toFixed(2)}%</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <div style={{
                textAlign: 'center',
                padding: '40px 20px',
                color: '#6b7280',
                fontSize: 14
              }}>
                보유 주식이 없습니다
              </div>
            )}
          </div>
        )}

        {/* 주문 관리 */}
        <div style={{
          background: 'white',
          borderRadius: 12,
          boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1)',
          padding: 24,
          marginBottom: 24
        }}>
          <h3 style={{ 
            margin: '0 0 16px 0', 
            fontSize: 16, 
            fontWeight: '600', 
            color: '#1f2937' 
          }}>
            주문 관리
          </h3>
          
          <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
            <Link
              to="/order-management"
              style={{
                padding: '12px 24px',
                border: '1px solid #2962FF',
                borderRadius: 6,
                background: 'white',
                color: '#2962FF',
                fontSize: 14,
                fontWeight: '500',
                textDecoration: 'none',
                cursor: 'pointer',
                transition: 'all 0.2s',
                display: 'inline-block'
              }}
              onMouseOver={(e) => {
                e.currentTarget.style.background = '#eff6ff'
                e.currentTarget.style.borderColor = '#1d4ed8'
              }}
              onMouseOut={(e) => {
                e.currentTarget.style.background = 'white'
                e.currentTarget.style.borderColor = '#2962FF'
              }}
            >
              📋 주문 내역 조회 및 취소
            </Link>
          </div>
        </div>

        {/* 액션 버튼들 */}
        <div style={{
          background: 'white',
          borderRadius: 12,
          boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1)',
          padding: 24
        }}>
          <h3 style={{ 
            margin: '0 0 16px 0', 
            fontSize: 16, 
            fontWeight: '600', 
            color: '#1f2937' 
          }}>
            계정 관리
          </h3>
          
          <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
            {/* 소셜 로그인 사용자는 비밀번호가 없을 수 있으므로 버튼 숨김 */}
            {localStorage.getItem('loginMethod') !== 'oauth' && (
            <button
              onClick={openPwModal}
              style={{
                padding: '12px 24px',
                border: '1px solid #d1d5db',
                borderRadius: 6,
                background: 'white',
                color: '#374151',
                fontSize: 14,
                fontWeight: '500',
                cursor: 'pointer',
                transition: 'all 0.2s'
              }}
              onMouseOver={(e) => {
                e.currentTarget.style.background = '#f9fafb'
                e.currentTarget.style.borderColor = '#9ca3af'
              }}
              onMouseOut={(e) => {
                e.currentTarget.style.background = 'white'
                e.currentTarget.style.borderColor = '#d1d5db'
              }}
            >
              비밀번호 변경
            </button>
            )}
            
            <button
              onClick={openDeleteModal}
              style={{
                padding: '12px 24px',
                border: '1px solid #dc2626',
                borderRadius: 6,
                background: 'white',
                color: '#dc2626',
                fontSize: 14,
                fontWeight: '500',
                cursor: 'pointer',
                transition: 'all 0.2s'
              }}
              onMouseOver={(e) => {
                e.currentTarget.style.background = '#fef2f2'
                e.currentTarget.style.borderColor = '#b91c1c'
              }}
              onMouseOut={(e) => {
                e.currentTarget.style.background = 'white'
                e.currentTarget.style.borderColor = '#dc2626'
              }}
            >
              회원탈퇴
            </button>
          </div>
        </div>
      </div>
      {/* 비밀번호 변경 모달 */}
      {pwModalOpen && (
        <div style={{
          position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.4)',
          display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 50
        }}>
          <div style={{
            width: '100%', maxWidth: 420, background: 'white', borderRadius: 12,
            boxShadow: '0 10px 25px rgba(0,0,0,0.15)', padding: 24, margin: 16
          }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
              <h3 style={{ margin: 0, fontSize: 18, fontWeight: 700, color: '#111827' }}>비밀번호 변경</h3>
              <button onClick={closePwModal} disabled={pwSubmitting} style={{
                border: 'none', background: 'transparent', fontSize: 18, cursor: pwSubmitting ? 'not-allowed' : 'pointer', color: '#6b7280'
              }}>✕</button>
            </div>

            {pwMessage && (
              <div style={{
                padding: 12, background: '#f8fafc', border: '1px solid #e5e7eb',
                borderRadius: 8, color: '#374151', fontSize: 13, marginBottom: 12
              }}>
                {pwMessage}
              </div>
            )}

            <form onSubmit={submitPasswordChange}>
              <div style={{ display: 'grid', gap: 12 }}>
                <div>
                  <label style={{ display: 'block', fontSize: 13, color: '#374151', marginBottom: 6 }}>현재 비밀번호</label>
                  <input
                    type="password"
                    name="currentPassword"
                    value={pwForm.currentPassword}
                    onChange={handlePwChange}
                    style={{ width: '100%', padding: 12, border: '1px solid #d1d5db', borderRadius: 8, fontSize: 14, boxSizing: 'border-box' }}
                    placeholder="현재 비밀번호"
                  />
                </div>
                <div>
                  <label style={{ display: 'block', fontSize: 13, color: '#374151', marginBottom: 6 }}>새 비밀번호</label>
                  <input
                    type="password"
                    name="newPassword"
                    value={pwForm.newPassword}
                    onChange={handlePwChange}
                    style={{ width: '100%', padding: 12, border: '1px solid #d1d5db', borderRadius: 8, fontSize: 14, boxSizing: 'border-box' }}
                    placeholder="새 비밀번호"
                  />
                </div>
                <div>
                  <label style={{ display: 'block', fontSize: 13, color: '#374151', marginBottom: 6 }}>새 비밀번호 확인</label>
                  <input
                    type="password"
                    name="checkNewPassword"
                    value={pwForm.checkNewPassword}
                    onChange={handlePwChange}
                    style={{ width: '100%', padding: 12, border: '1px solid #d1d5db', borderRadius: 8, fontSize: 14, boxSizing: 'border-box' }}
                    placeholder="새 비밀번호 확인"
                  />
                </div>
              </div>

              <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end', marginTop: 16 }}>
                <button type="button" onClick={closePwModal} disabled={pwSubmitting} style={{
                  padding: '10px 16px', border: '1px solid #d1d5db', borderRadius: 8, background: 'white', color: '#374151', cursor: pwSubmitting ? 'not-allowed' : 'pointer'
                }}>취소</button>
                <button type="submit" disabled={pwSubmitting} style={{
                  padding: '10px 16px', border: 'none', borderRadius: 8, background: pwSubmitting ? '#9ca3af' : '#2962FF', color: 'white', cursor: pwSubmitting ? 'not-allowed' : 'pointer', fontWeight: 600
                }}>{pwSubmitting ? '변경 중...' : '변경하기'}</button>
              </div>
            </form>
          </div>
        </div>
      )}
      
      {/* 회원탈퇴 모달 */}
      {deleteModalOpen && (
        <div style={{
          position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.4)',
          display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 50
        }}>
          <div style={{
            width: '100%', maxWidth: 420, background: 'white', borderRadius: 12,
            boxShadow: '0 10px 25px rgba(0,0,0,0.15)', padding: 24, margin: 16
          }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
              <h3 style={{ margin: 0, fontSize: 18, fontWeight: 700, color: '#111827' }}>회원탈퇴</h3>
              <button onClick={closeDeleteModal} disabled={deleteSubmitting} style={{
                border: 'none', background: 'transparent', fontSize: 18, cursor: deleteSubmitting ? 'not-allowed' : 'pointer', color: '#6b7280'
              }}>✕</button>
            </div>

            {deleteMessage && (
              <div style={{
                padding: 12, background: deleteMessage.includes('완료') ? '#d1fae5' : '#fef2f2', 
                border: `1px solid ${deleteMessage.includes('완료') ? '#34d399' : '#fecaca'}`,
                borderRadius: 8, color: deleteMessage.includes('완료') ? '#065f46' : '#dc2626', fontSize: 13, marginBottom: 12
              }}>
                {deleteMessage}
              </div>
            )}

            <div style={{ marginBottom: 20 }}>
              <p style={{ margin: '0 0 12px 0', fontSize: 14, color: '#374151', lineHeight: 1.5 }}>
                정말로 회원탈퇴를 하시겠습니까?
              </p>
              <p style={{ margin: 0, fontSize: 13, color: '#6b7280', lineHeight: 1.4 }}>
                • 탈퇴 시 모든 데이터가 삭제되며 복구할 수 없습니다.<br/>
                • 보유 중인 주식과 잔액이 모두 삭제됩니다.<br/>
                • 주문 내역과 거래 기록이 모두 삭제됩니다.
              </p>
            </div>

            <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
              <button type="button" onClick={closeDeleteModal} disabled={deleteSubmitting} style={{
                padding: '10px 16px', border: '1px solid #d1d5db', borderRadius: 8, background: 'white', color: '#374151', cursor: deleteSubmitting ? 'not-allowed' : 'pointer'
              }}>취소</button>
              <button type="button" onClick={handleDeleteAccount} disabled={deleteSubmitting} style={{
                padding: '10px 16px', border: 'none', borderRadius: 8, background: deleteSubmitting ? '#9ca3af' : '#dc2626', color: 'white', cursor: deleteSubmitting ? 'not-allowed' : 'pointer', fontWeight: 600
              }}>{deleteSubmitting ? '탈퇴 중...' : '탈퇴하기'}</button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
