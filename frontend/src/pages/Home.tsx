import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { createStompClient } from '../lib/socket'
import { tokenManager } from '../lib/tokenManager'

type Row = {
  ticker: string
  name: string
  price: number
  changeRate: number
  logoUrl?: string
  volume: number
}

type UserStock = {
  ticker: string
  companyName: string
  totalQuantity: number
  avgPrice: number
  currentPrice: number
  changeRate?: number
  logoUrl?: string
}

export default function Home() {
  const [rows, setRows] = useState<Row[]>([])
  const [page, setPage] = useState(0)
  const [hasMore, setHasMore] = useState(true)
  const [userInfo, setUserInfo] = useState<{userId: string, email: string, name: string} | null>(null)
  const [showDropdown, setShowDropdown] = useState(false)
  const [userStocks, setUserStocks] = useState<UserStock[]>([])
  const [sortBy, setSortBy] = useState<'volume' | 'price' | 'rise' | 'fall'>('volume')
  const [userStockSortBy, setUserStockSortBy] = useState<'quantity' | 'changeRate'>('quantity')
  const loaderRef = useRef<HTMLDivElement | null>(null)
  const stompRef = useRef<ReturnType<typeof createStompClient> | null>(null)
  const dropdownRef = useRef<HTMLDivElement | null>(null)
  const [searchParams, setSearchParams] = useSearchParams()

  // OAuth 토큰 처리
  useEffect(() => {
    const token = searchParams.get('token')
    if (token) {
      console.log('OAuth 토큰 받음:', token)
      
      // 토큰을 메모리에 저장 (localStorage에서 기존 토큰 제거)
      localStorage.removeItem('accessToken')
      tokenManager.setTokens(token)
      
      // 사용자 정보를 토큰에서 추출 (JWT 디코딩)
      try {
        const payload = decodeJwtPayload(token)
        console.log('JWT 페이로드:', payload)
        console.log('JWT에서 추출한 name:', payload.name, '(타입:', typeof payload.name, ', 길이:', payload.name ? payload.name.length : 0, ')')
        
        const userInfo = {
          userId: payload.sub,
          email: payload.email,
          name: payload.name
        }
        
        localStorage.setItem('userInfo', JSON.stringify(userInfo))
        localStorage.setItem('loginMethod', 'oauth')
        setUserInfo(userInfo)
        
        console.log('OAuth 로그인 완료:', userInfo)
        
        // URL에서 토큰 파라미터 제거
        setSearchParams({})
      } catch (error) {
        console.error('토큰 파싱 오류:', error)
      }
    }
  }, [searchParams, setSearchParams])

  // 보유 종목 조회 함수
  const fetchUserStocks = async () => {
    if (!userInfo) return
    try {
      console.log('보유 종목 API 호출:', '/api/v1/userstocks')
      const response = await tokenManager.authenticatedFetch('/api/v1/userstocks')
      console.log('보유 종목 API 응답 상태:', response.status)
      if (response.ok) {
        const result = await response.json()
        console.log('보유 종목 API 응답 데이터:', result)
        const stocks = result.data || result || []
        // 현재가와 등락률을 계산하기 위해 전체 주식 목록에서 정보 가져오기
        const stocksWithInfo = stocks.map((stock: UserStock) => {
          const fullStock = rows.find(r => r.ticker === stock.ticker)
          return {
            ...stock,
            changeRate: fullStock?.changeRate ?? 0,
            logoUrl: fullStock?.logoUrl
          }
        })
        // 선택한 정렬 기준으로 정렬
        const sorted = sortUserStocks(stocksWithInfo, userStockSortBy)
        console.log('보유 종목 처리 결과:', sorted)
        setUserStocks(sorted)
      } else {
        console.error('보유 종목 API 실패:', response.status)
        setUserStocks([])
      }
    } catch (err) {
      console.error('보유 종목 조회 오류:', err)
      setUserStocks([])
    }
  }

  // 로그인 상태 확인
  useEffect(() => {
    const storedUserInfo = localStorage.getItem('userInfo')
    if (storedUserInfo) {
      try {
        const parsed = JSON.parse(storedUserInfo)
        setUserInfo(parsed)
      } catch (e) {
        console.error('사용자 정보 파싱 오류:', e)
        localStorage.removeItem('userInfo')
        localStorage.removeItem('accessToken')
        localStorage.removeItem('refreshToken')
      }
    }
  }, [])

  // 로그인 상태가 변경되거나 rows가 로드된 후 보유 종목 조회
  useEffect(() => {
    if (userInfo && rows.length > 0) {
      fetchUserStocks()
    }
  }, [userInfo, rows.length])

  // 외부 클릭 시 드롭다운 닫기
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setShowDropdown(false)
      }
    }

    if (showDropdown) {
      document.addEventListener('mousedown', handleClickOutside)
    }

    return () => {
      document.removeEventListener('mousedown', handleClickOutside)
    }
  }, [showDropdown])

  // 정렬 함수
  const sortRows = (data: Row[], sortType: 'volume' | 'price' | 'rise' | 'fall'): Row[] => {
    const sorted = [...data]
    switch (sortType) {
      case 'volume':
        return sorted.sort((a, b) => (b.volume || 0) - (a.volume || 0))
      case 'price':
        return sorted.sort((a, b) => (b.price || 0) - (a.price || 0))
      case 'rise':
        return sorted.sort((a, b) => (b.changeRate || 0) - (a.changeRate || 0))
      case 'fall':
        return sorted.sort((a, b) => (a.changeRate || 0) - (b.changeRate || 0))
      default:
        return sorted
    }
  }

  // 보유 종목 정렬 함수
  const sortUserStocks = (data: UserStock[], sortType: 'quantity' | 'changeRate'): UserStock[] => {
    const sorted = [...data]
    switch (sortType) {
      case 'quantity':
        return sorted.sort((a, b) => (b.totalQuantity || 0) - (a.totalQuantity || 0))
      case 'changeRate':
        return sorted.sort((a, b) => (b.changeRate || 0) - (a.changeRate || 0))
      default:
        return sorted
    }
  }

  // 로그아웃 함수
  const handleLogout = async () => {
    console.log('메인 페이지 로그아웃 함수가 호출되었습니다!')
    
    try {
      // 백엔드 로그아웃 API 호출
      console.log('로그아웃 API 호출 시작')
      await tokenManager.authenticatedFetch('/api/v1/users/logout', {
        method: 'POST'
      })
      console.log('로그아웃 API 호출 완료')
    } catch (error) {
      console.error('로그아웃 API 호출 실패:', error)
      // API 호출 실패해도 로컬 로그아웃은 진행
    } finally {
      // 로컬 스토리지 정리
      console.log('로컬 스토리지 정리 시작')
      tokenManager.clearTokens()
      setUserInfo(null)
      console.log('메인 페이지 로그아웃 완료')
    }
  }

  // 초기 페이지 로드
  useEffect(() => {
    fetch(`/api/v1/stocks`)
      .then(r => r.ok ? r.json() : Promise.reject(r))
      .then(json => {
        const raw = Array.isArray(json?.data) ? json.data : (Array.isArray(json) ? json : [])
        const normalized: Row[] = raw.map((it: any) => {
          const ticker = String(it?.ticker ?? it?.stockCode ?? it?.code ?? it?.symbol ?? '')
          const name = String(it?.name ?? it?.companyName ?? it?.stockName ?? '')
          const price = toNum(it?.price ?? it?.stck_prpr ?? it?.currentPrice) ?? 0
          const changeRate = toNum(it?.changeRate ?? it?.prdy_ctrt ?? it?.rate) ?? 0
          const logoUrl = it?.logoUrl as string | undefined

          const volume = toNum(it?.volume ?? it?.acml_vol ?? it?.accumulatedVolume) ?? 0
          return { ticker, name, price, changeRate, logoUrl, volume }
//           return { ticker, name, price, changeRate, logoUrl}
        }).filter((r: Row) => r.ticker && r.name)
        // 선택한 정렬 기준으로 정렬
        const sorted = sortRows(normalized, sortBy)
        setRows(sorted)
        setHasMore(false) // 서버 페이징 구현 전까지 false
      })
      .catch(err => {
        console.error("주식 데이터 불러오기 실패", err)
        // setRows([]) // 실패 시 빈 값
      })
  }, [])

  // STOMP 실시간 업데이트 반영 (Redis Pub/Sub -> 백엔드 브로드캐스트를 전제로 /topic/stocks 구독)

  const onTick = useMemo(() => (payload: any, raw: string) => {
    const code = String(payload?.ticker ?? '')
    const price = toNum(payload?.price)
    const changeRate = toNum(payload?.changeRate)
    const companyName = payload?.companyName as string | undefined
    const logoUrl = payload?.logoUrl as string | undefined
    const volume = toNum(payload?.volume ?? payload?.accumulatedVolume ?? payload?.acml_vol)

    if (companyName) {
      console.log("실시간 수신:", { companyName, price, changeRate })
    } else {
      console.log("실시간 수신(이름없음):", { ticker: code, price, changeRate })
    }

    if (!code || price == null) return
    
    // 장 마감 시간(15:30) 이후에는 실시간 업데이트 중단
//     if (!isMarketOpen()) return
    
    setRows(prev => {
      const found = prev.some(r => r.ticker === code)
      const updated = found
        ? prev.map(r =>
            r.ticker === code
              ? { ...r, price, changeRate: changeRate ?? r.changeRate, name: companyName ?? r.name, logoUrl: logoUrl ?? r.logoUrl, volume: volume ?? r.volume }
              : r
          )
        : [...prev, { ticker: code, name: companyName ?? code, price, changeRate: changeRate ?? 0, logoUrl, volume: volume ?? 0 }]
      // 선택한 정렬 기준으로 정렬
      const sorted = sortRows(updated, sortBy)
      
      // 보유 종목이 있으면 실시간 정보 업데이트
      if (userStocks.length > 0) {
        const updatedStocks = userStocks.map(stock => {
          if (stock.ticker === code) {
            const matched = sorted.find(r => r.ticker === code)
            return {
              ...stock,
              currentPrice: price ?? stock.currentPrice,
              changeRate: changeRate ?? stock.changeRate,
              logoUrl: logoUrl ?? stock.logoUrl
            }
          }
          return stock
        })
        // 선택한 정렬 기준으로 정렬
        const sortedStocks = sortUserStocks(updatedStocks, userStockSortBy)
        setUserStocks(sortedStocks)
      }
      
      return sorted
    })
  }, [sortBy, userStockSortBy])

  useEffect(() => {
    const client = createStompClient(onTick)
    client.activate()
    stompRef.current = client
    return () => { client.deactivate() }
  }, [onTick])

  // sortBy 변경 시 rows 재정렬
  useEffect(() => {
    if (rows.length > 0) {
      const sorted = sortRows(rows, sortBy)
      setRows(sorted)
    }
  }, [sortBy])

  // userStockSortBy 변경 시 보유 종목 재정렬
  useEffect(() => {
    if (userStocks.length > 0) {
      const sorted = sortUserStocks(userStocks, userStockSortBy)
      setUserStocks(sorted)
    }
  }, [userStockSortBy])

  // rows 변경 시 보유 종목 정보 업데이트
  useEffect(() => {
    if (userStocks.length > 0) {
      const updated = userStocks.map(stock => {
        const matched = rows.find(r => r.ticker === stock.ticker)
        if (matched) {
          return {
            ...stock,
            currentPrice: matched.price,
            changeRate: matched.changeRate,
            logoUrl: matched.logoUrl
          }
        }
        return stock
      })
      // 선택한 정렬 기준으로 정렬
      const sorted = sortUserStocks(updated, userStockSortBy)
      setUserStocks(sorted)
    }
  }, [rows])

  // 무한 스크롤 옵저버 (서버 페이징 연결 시 활성화)
  useEffect(() => {
    if (!loaderRef.current || !hasMore) return
    const io = new IntersectionObserver(entries => {
      if (entries.some(e => e.isIntersecting)) {
        setPage(p => p + 1)
        // TODO: /api/stocks/summary?page=... 로 확장 가능
      }
    })
    io.observe(loaderRef.current)
    return () => io.disconnect()
  }, [hasMore])

  return (
    <div style={{ background: 'radial-gradient(1000px 400px at 50% -100px, #e0e7ff 0%, #ffffff 60%)' }}>
      {/* 상단 우측 액션 바 */}
      <div style={{ 
        position: 'fixed', 
        top: 12, 
        right: 16, 
        zIndex: 50, 
        display: 'flex', 
        gap: 8, 
        alignItems: 'center',
        backdropFilter: 'blur(10px)',
        background: 'rgba(255, 255, 255, 0.8)',
        padding: '8px 12px',
        borderRadius: 12,
        boxShadow: '0 4px 12px rgba(0, 0, 0, 0.1)'
      }}>
        {userInfo ? (
          <div ref={dropdownRef} style={{ position: 'relative' }}>
            <button
              onClick={() => setShowDropdown(!showDropdown)}
              style={{
                width: 40,
                height: 40,
                borderRadius: '50%',
                background: showDropdown ? '#f9fafb' : 'white',
                color: '#111827',
                border: '1px solid #e5e7eb',
                transition: 'all 0.2s ease',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: 20,
                cursor: 'pointer'
              }}
              onMouseOver={(e) => {
                if (!showDropdown) {
                  e.currentTarget.style.background = '#f9fafb'
                  e.currentTarget.style.transform = 'translateY(-1px)'
                  e.currentTarget.style.boxShadow = '0 2px 4px rgba(0,0,0,0.1)'
                }
              }}
              onMouseOut={(e) => {
                if (!showDropdown) {
                  e.currentTarget.style.background = 'white'
                  e.currentTarget.style.transform = 'translateY(0)'
                  e.currentTarget.style.boxShadow = 'none'
                }
              }}
              title="메뉴"
            >
              👤
            </button>
            
            {showDropdown && (
              <div style={{
                position: 'absolute',
                top: '48px',
                right: 0,
                background: 'white',
                borderRadius: 12,
                boxShadow: '0 8px 24px rgba(0, 0, 0, 0.15)',
                minWidth: 160,
                border: '1px solid #e5e7eb',
                overflow: 'hidden',
                zIndex: 1000
              }}>
                <Link
                  to="/mypage"
                  onClick={() => setShowDropdown(false)}
                  style={{
                    display: 'block',
                    padding: '12px 16px',
                    textDecoration: 'none',
                    color: '#111827',
                    fontSize: 14,
                    fontWeight: 600,
                    transition: 'background 0.2s ease',
                    borderBottom: '1px solid #f1f5f9'
                  }}
                  onMouseOver={(e) => {
                    e.currentTarget.style.background = '#f9fafb'
                  }}
                  onMouseOut={(e) => {
                    e.currentTarget.style.background = 'white'
                  }}
                >
                  마이페이지
                </Link>
                <Link
                  to="/order-management"
                  onClick={() => setShowDropdown(false)}
                  style={{
                    display: 'block',
                    padding: '12px 16px',
                    textDecoration: 'none',
                    color: '#111827',
                    fontSize: 14,
                    fontWeight: 600,
                    transition: 'background 0.2s ease',
                    borderBottom: '1px solid #f1f5f9'
                  }}
                  onMouseOver={(e) => {
                    e.currentTarget.style.background = '#f9fafb'
                  }}
                  onMouseOut={(e) => {
                    e.currentTarget.style.background = 'white'
                  }}
                >
                  주문 내역 관리
                </Link>
                <button
                  onClick={() => {
                    setShowDropdown(false)
                    handleLogout()
                  }}
                  style={{
                    width: '100%',
                    padding: '12px 16px',
                    textAlign: 'left',
                    background: 'white',
                    border: 'none',
                    color: '#dc2626',
                    fontSize: 14,
                    fontWeight: 600,
                    cursor: 'pointer',
                    transition: 'background 0.2s ease'
                  }}
                  onMouseOver={(e) => {
                    e.currentTarget.style.background = '#fee2e2'
                  }}
                  onMouseOut={(e) => {
                    e.currentTarget.style.background = 'white'
                  }}
                >
                  로그아웃
                </button>
              </div>
            )}
          </div>
        ) : (
          <>
            <Link
              to="/login"
              style={{
                padding: '10px 14px',
                borderRadius: 10,
                background: 'white',
                color: '#111827',
                textDecoration: 'none',
                fontSize: 14,
                fontWeight: 700,
                border: '1px solid #e5e7eb',
                transition: 'all 0.2s ease',
                display: 'inline-block'
              }}
              onMouseOver={(e) => {
                e.currentTarget.style.background = '#f9fafb'
                e.currentTarget.style.transform = 'translateY(-1px)'
                e.currentTarget.style.boxShadow = '0 2px 4px rgba(0,0,0,0.1)'
              }}
              onMouseOut={(e) => {
                e.currentTarget.style.background = 'white'
                e.currentTarget.style.transform = 'translateY(0)'
                e.currentTarget.style.boxShadow = 'none'
              }}
            >
              로그인
            </Link>
            <Link
              to="/signup"
              style={{
                padding: '10px 14px',
                borderRadius: 10,
                background: '#2962FF',
                color: 'white',
                textDecoration: 'none',
                fontSize: 14,
                fontWeight: 800,
                border: '1px solid #2962FF',
                transition: 'all 0.2s ease',
                display: 'inline-block'
              }}
              onMouseOver={(e) => {
                e.currentTarget.style.background = '#1d4ed8'
                e.currentTarget.style.borderColor = '#1d4ed8'
                e.currentTarget.style.transform = 'translateY(-1px)'
                e.currentTarget.style.boxShadow = '0 2px 8px rgba(41,98,255,0.3)'
              }}
              onMouseOut={(e) => {
                e.currentTarget.style.background = '#2962FF'
                e.currentTarget.style.borderColor = '#2962FF'
                e.currentTarget.style.transform = 'translateY(0)'
                e.currentTarget.style.boxShadow = 'none'
              }}
            >
              회원가입
            </Link>
          </>
        )}
      </div>
      {/* 상단 히어로 (센터 정렬, 대형 타이틀) */}
      <div style={{ maxWidth: 1120, margin: '0 auto', padding: '64px 16px 24px', textAlign: 'center' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 0, marginBottom: 4 }}>
          <img 
            src="/logos/Stock King2.jpg" 
            alt="Stock King Logo" 
            style={{ 
              width: 120, 
              height: 120, 
              objectFit: 'contain',
              background: 'transparent',
              marginTop: '38px'
            }} 
            onError={(e) => {
              // 이미지 로드 실패 시 숨김 처리
              e.currentTarget.style.display = 'none'
            }}
          />
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start', gap: 6 }}>
            <div style={{ display: 'inline-flex', alignItems: 'center', padding: '6px 12px', borderRadius: 999, background: '#eef2ff', color: '#4338ca', fontSize: 12, fontWeight: 700, letterSpacing: 0.2, marginBottom: 0 }}>
              모의 주식 투자 플랫폼
            </div>
            <h1 style={{
              margin: 0,
              padding: 0,
              fontSize: 72,
              lineHeight: 1.05,
              letterSpacing: -1.5,
              fontWeight: 900,
              background: 'linear-gradient(135deg, #1e40af 0%, #3b82f6 50%, #60a5fa 100%)',
              WebkitBackgroundClip: 'text',
              backgroundClip: 'text',
              color: 'transparent',
              textShadow: '0 2px 8px rgba(59, 130, 246, 0.3)',
              fontFamily: 'system-ui, -apple-system, sans-serif',
              display: 'flex',
              alignItems: 'center'
            }}>
              Stock King
            </h1>
          </div>
        </div>
        <p style={{ margin: '14px auto 0', fontSize: 18, color: '#475569', maxWidth: 680 }}>
          실시간 차트와 주문, 포트폴리오까지. 빠르고 가벼운 트레이딩 경험을 제공합니다.
        </p>
        
        {/* 하이라이트 카드 (새 디자인) */}
        <div style={{ display: 'grid', gap: 16, gridTemplateColumns: 'repeat(3, minmax(0, 1fr))', marginTop: 28 }}>
          <FeatureCard title="실시간 차트" desc="정교한 캔들 · 거래량 · 툴팁" emoji="📈" />
          <FeatureCard title="주문/알림" desc="즉시·예약 주문과 체결 알림" emoji="⚡" />
          <FeatureCard title="포트폴리오" desc="총자산·수익률을 실시간 추적" emoji="💼" />
        </div>
      </div>

      {/* 통계 섹션 */}
      <div style={{ maxWidth: 1120, margin: '40px auto', padding: '0 16px' }}>
        <StatisticsSection rows={rows} />
      </div>

      {/* 최고 상승/하락 종목 하이라이트 */}
      {rows.length > 0 && (
        <div style={{ maxWidth: 1120, margin: '32px auto', padding: '0 16px' }}>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
            <TopStockCard 
              title="🔥 상승률 TOP 3"
              rows={rows.filter(r => r.changeRate > 0).sort((a, b) => b.changeRate - a.changeRate).slice(0, 3)}
              color="#dc2626"
            />
            <TopStockCard 
              title="📉 하락률 TOP 3"
              rows={rows.filter(r => r.changeRate < 0).sort((a, b) => a.changeRate - b.changeRate).slice(0, 3)}
              color="#2563eb"
            />
          </div>
        </div>
      )}

      {/* 보유 종목 섹션 */}
      {userInfo && userStocks.length > 0 && (
        <div style={{ maxWidth: 1120, margin: '32px auto', padding: '0 16px' }}>
          <div style={{ 
            display: 'flex', 
            alignItems: 'center', 
            gap: 12, 
            marginBottom: 16,
            padding: '16px 20px',
            background: 'linear-gradient(135deg, #f8fafc 0%, #ffffff 100%)',
            borderRadius: 12,
            border: '1px solid #e5e7eb'
          }}>
            <div style={{
              width: 40,
              height: 40,
              borderRadius: 10,
              background: 'linear-gradient(135deg, #3b82f6 0%, #2563eb 100%)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: 20
            }}>
              💼
            </div>
            <div style={{ flex: 1 }}>
              <h2 style={{ margin: 0, fontSize: 20, fontWeight: 800, color: '#0f172a' }}>
                내 보유 종목
              </h2>
              <p style={{ margin: '4px 0 0', fontSize: 13, color: '#6b7280' }}>
                보유하고 있는 종목의 실시간 시세를 확인하세요
              </p>
            </div>
            <div style={{ display: 'flex', gap: 8 }}>
              {[
                { key: 'quantity', label: '보유량' },
                { key: 'changeRate', label: '등락률' }
              ].map(({ key, label }) => (
                <button
                  key={key}
                  onClick={() => setUserStockSortBy(key as 'quantity' | 'changeRate')}
                  style={{
                    padding: '8px 16px',
                    border: 'none',
                    borderRadius: 4,
                    background: userStockSortBy === key ? '#e5e7eb' : 'white',
                    color: userStockSortBy === key ? '#374151' : '#333',
                    cursor: 'pointer',
                    fontSize: 14,
                    fontWeight: userStockSortBy === key ? 'bold' : 'normal',
                    boxShadow: '0 1px 3px rgba(0, 0, 0, 0.1)'
                  }}
                >
                  {label}
                </button>
              ))}
            </div>
          </div>
          <div style={{ border: '1px solid #e5e7eb', borderRadius: 12, overflow: 'hidden', background: 'white' }}>
            {/* 헤더 */}
            <div style={{ display: 'grid', gridTemplateColumns: '56px 1fr 100px 120px 120px', alignItems: 'center', gap: 12, padding: '12px 14px', background: '#f8fafc', color: '#475569', fontSize: 12, fontWeight: 700 }}>
              <div />
              <div style={{ transform: 'translateX(8px)' }}>종목</div>
              <div style={{ textAlign: 'right' }}>보유량</div>
              <div style={{ textAlign: 'right', transform: 'translateX(-8px)' }}>현재가</div>
              <div style={{ textAlign: 'right', transform: 'translateX(-8px)' }}>등락률</div>
            </div>
            {userStocks.map((stock, idx) => {
              const changeRate = stock.changeRate ?? 0
              return (
                <Link
                  key={stock.ticker}
                  to={`/stocks/${stock.ticker}/chart`}
                  style={{
                    display: 'grid',
                    gridTemplateColumns: '56px 1fr 100px 120px 120px',
                    alignItems: 'center',
                    gap: 12,
                    padding: '12px 14px',
                    borderTop: '1px solid #f1f5f9',
                    textDecoration: 'none',
                    color: 'inherit',
                    background: idx % 2 ? '#ffffff' : '#fcfcfd',
                    transition: 'all 0.2s ease-in-out',
                    transform: 'translateY(0)'
                  }}
                  onMouseOver={(e) => {
                    e.currentTarget.style.background = '#fafcff'
                    e.currentTarget.style.boxShadow = 'inset 0 0 0 1px #e5e7eb'
                    e.currentTarget.style.transform = 'translateY(-1px)'
                  }}
                  onMouseOut={(e) => {
                    e.currentTarget.style.background = idx % 2 ? '#ffffff' : '#fcfcfd'
                    e.currentTarget.style.boxShadow = 'none'
                    e.currentTarget.style.transform = 'translateY(0)'
                  }}
                >
                  <LogoCell name={stock.companyName} ticker={stock.ticker} logoUrl={stock.logoUrl} />
                  <div style={{ display: 'flex', flexDirection: 'column' }}>
                    <span style={{ fontWeight: 700, color: '#0f172a' }}>{stock.companyName}</span>
                    <span style={{ fontSize: 12, color: '#94a3b8', marginTop: 2 }}>{stock.ticker}</span>
                  </div>
                  <div style={{ textAlign: 'right', fontVariantNumeric: 'tabular-nums', color: '#334155', fontWeight: 600 }}>
                    {stock.totalQuantity.toLocaleString()}주
                  </div>
                  <div style={{ 
                    textAlign: 'right', 
                    fontVariantNumeric: 'tabular-nums', 
                    fontWeight: 700, 
                    color: changeRate > 0 ? '#dc2626' : changeRate < 0 ? '#2563eb' : '#0f172a',
                    transition: 'color 0.3s ease'
                  }}>
                    {stock.currentPrice != null ? `${stock.currentPrice.toLocaleString()}원` : '-'}
                  </div>
                  <div style={{ textAlign: 'right', display: 'flex', justifyContent: 'flex-end' }}>
                    {changeRate != null ? (
                      <span
                        style={{
                          display: 'inline-flex',
                          alignItems: 'center',
                          padding: '4px 10px',
                          borderRadius: 999,
                          fontWeight: 700,
                          fontSize: 12,
                          color: changeRate > 0 ? '#b91c1c' : changeRate < 0 ? '#1d4ed8' : '#374151',
                          background: changeRate > 0 ? '#fee2e2' : changeRate < 0 ? '#dbeafe' : '#f3f4f6',
                          transition: 'all 0.3s ease'
                        }}
                      >
                        {changeRate > 0 ? '▲' : changeRate < 0 ? '▼' : ''} {changeRate.toFixed(2)}%
                      </span>
                    ) : (
                      '-'
                    )}
                  </div>
                </Link>
              )
            })}
          </div>
        </div>
      )}

      {/* 목록 섹션 */}
      <div style={{ maxWidth: 1120, margin: '24px auto 40px', padding: '0 16px' }}>
        <div style={{ 
          display: 'flex', 
          alignItems: 'center', 
          gap: 12, 
          marginBottom: 16,
          padding: '16px 20px',
          background: 'linear-gradient(135deg, #f8fafc 0%, #ffffff 100%)',
          borderRadius: 12,
          border: '1px solid #e5e7eb'
        }}>
          <div style={{
            width: 40,
            height: 40,
            borderRadius: 10,
            background: 'linear-gradient(135deg, #3b82f6 0%, #2563eb 100%)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: 20
          }}>
            📈
          </div>
          <div style={{ flex: 1 }}>
            <h2 style={{ margin: 0, fontSize: 20, fontWeight: 800, color: '#0f172a' }}>
              실시간 차트
            </h2>
            <p style={{ margin: '4px 0 0', fontSize: 13, color: '#6b7280' }}>
              모든 종목의 실시간 시세 정보를 확인하세요
            </p>
          </div>
          <div style={{ display: 'flex', gap: 8 }}>
            {[
              { key: 'volume', label: '거래량' },
              { key: 'price', label: '가격' },
              { key: 'rise', label: '급상승' },
              { key: 'fall', label: '급하락' }
            ].map(({ key, label }) => (
              <button
                key={key}
                onClick={() => setSortBy(key as 'volume' | 'price' | 'rise' | 'fall')}
                style={{
                  padding: '8px 16px',
                  border: 'none',
                  borderRadius: 4,
                  background: sortBy === key ? '#e5e7eb' : 'white',
                  color: sortBy === key ? '#374151' : '#333',
                  cursor: 'pointer',
                  fontSize: 14,
                  fontWeight: sortBy === key ? 'bold' : 'normal',
                  boxShadow: '0 1px 3px rgba(0, 0, 0, 0.1)'
                }}
              >
                {label}
              </button>
            ))}
          </div>
        </div>
        <div style={{ border: '1px solid #e5e7eb', borderRadius: 12, overflow: 'hidden', background: 'white' }}>
          {/* 헤더 */}
          <div style={{ display: 'grid', gridTemplateColumns: '50px 56px 1fr 120px 120px 120px', alignItems: 'center', gap: 12, padding: '12px 14px', background: '#f8fafc', color: '#475569', fontSize: 12, fontWeight: 700 }}>
            <div style={{ textAlign: 'center' }}>순위</div>
            <div />
            <div style={{ transform: 'translateX(8px)' }}>종목</div>
            <div style={{ textAlign: 'right', transform: 'translateX(-8px)' }}>현재가</div>
            <div style={{ textAlign: 'right', transform: 'translateX(-8px)' }}>등락률</div>
            <div style={{ textAlign: 'right', transform: 'translateX(-8px)' }}>거래량</div>
          </div>
          {rows.map((row, idx) => (
            <Link
              key={row.ticker}
              to={`/stocks/${row.ticker}/chart`}
              style={{
                display: 'grid',
                gridTemplateColumns: '50px 56px 1fr 120px 120px 120px',
                alignItems: 'center',
                gap: 12,
                padding: '12px 14px',
                borderTop: '1px solid #f1f5f9',
                textDecoration: 'none',
                color: 'inherit',
                background: idx % 2 ? '#ffffff' : '#fcfcfd',
                transition: 'all 0.2s ease-in-out',
                transform: 'translateY(0)'
              }}
              onMouseOver={(e) => {
                e.currentTarget.style.background = '#fafcff'
                e.currentTarget.style.boxShadow = 'inset 0 0 0 1px #e5e7eb'
                e.currentTarget.style.transform = 'translateY(-1px)'
              }}
              onMouseOut={(e) => {
                e.currentTarget.style.background = idx % 2 ? '#ffffff' : '#fcfcfd'
                e.currentTarget.style.boxShadow = 'none'
                e.currentTarget.style.transform = 'translateY(0)'
              }}
            >
              {/* 순위 */}
              <div style={{ 
                textAlign: 'center',
                fontSize: 14,
                fontWeight: idx < 3 ? 800 : 600,
                color: idx < 3 ? (idx === 0 ? '#dc2626' : idx === 1 ? '#f59e0b' : '#2563eb') : '#6b7280'
              }}>
                {idx + 1}
              </div>
              <LogoCell name={row.name} ticker={row.ticker} logoUrl={row.logoUrl} />
              <div style={{ display: 'flex', flexDirection: 'column' }}>
                <span style={{ fontWeight: 700, color: '#0f172a' }}>{row.name}</span>
                <span style={{ fontSize: 12, color: '#94a3b8', marginTop: 2 }}>{row.ticker}</span>
              </div>
              <div style={{ 
                textAlign: 'right', 
                fontVariantNumeric: 'tabular-nums', 
                fontWeight: 700, 
                color: row.changeRate > 0 ? '#dc2626' : row.changeRate < 0 ? '#2563eb' : '#0f172a',
                transition: 'color 0.3s ease'
              }}>
                {row.price != null ? `${row.price.toLocaleString()}원` : '-'}
              </div>
              <div style={{ textAlign: 'right', display: 'flex', justifyContent: 'flex-end' }}>
                {row.changeRate != null ? (
                  <span
                    style={{
                      display: 'inline-flex',
                      alignItems: 'center',
                      padding: '4px 10px',
                      borderRadius: 999,
                      fontWeight: 700,
                      fontSize: 12,
                      color: row.changeRate > 0 ? '#b91c1c' : row.changeRate < 0 ? '#1d4ed8' : '#374151',
                      background: row.changeRate > 0 ? '#fee2e2' : row.changeRate < 0 ? '#dbeafe' : '#f3f4f6',
                      transition: 'all 0.3s ease'
                    }}
                  >
                    {row.changeRate > 0 ? '▲' : row.changeRate < 0 ? '▼' : ''} {row.changeRate.toFixed(2)}%
                  </span>
                ) : (
                  '-'
                )}
              </div>
              <div style={{ textAlign: 'right', fontVariantNumeric: 'tabular-nums', color: '#334155' }}>
                {row.volume != null ? `${row.volume.toLocaleString()}주` : '-'}
              </div>
            </Link>
          ))}
          <div ref={loaderRef} style={{ height: 24 }} />
        </div>
      </div>

      {/* 푸터 */}
      <footer style={{
        background: 'linear-gradient(180deg, #ffffff 0%, #f8fafc 100%)',
        borderTop: '1px solid #e5e7eb',
        padding: '40px 16px',
        marginTop: '60px',
        textAlign: 'center'
      }}>
        <div style={{ maxWidth: 1120, margin: '0 auto' }}>
          <div style={{ 
            display: 'flex', 
            alignItems: 'center', 
            justifyContent: 'center', 
            gap: 8,
            marginBottom: 16
          }}>
            <img 
              src="/logos/Stock King2.jpg" 
              alt="Stock King Logo" 
              style={{ 
                width: 32, 
                height: 32, 
                objectFit: 'contain',
                background: 'transparent'
              }} 
              onError={(e) => {
                e.currentTarget.style.display = 'none'
              }}
            />
            <span style={{ 
              fontSize: 18, 
              fontWeight: 700, 
              background: 'linear-gradient(135deg, #1e40af 0%, #3b82f6 50%, #60a5fa 100%)',
              WebkitBackgroundClip: 'text',
              backgroundClip: 'text',
              color: 'transparent'
            }}>
              Stock King
            </span>
          </div>
          <p style={{ 
            fontSize: 14, 
            color: '#6b7280', 
            margin: '8px 0 16px',
            lineHeight: 1.6
          }}>
            모의 주식 투자 플랫폼으로 안전하게 실전 투자를 연습하세요.
            <br />
            실시간 차트와 주문 시스템을 통해 전문가 수준의 트레이딩 경험을 제공합니다.
          </p>
          <div style={{ 
            paddingTop: '20px', 
            borderTop: '1px solid #e5e7eb',
            fontSize: 12,
            color: '#9ca3b8'
          }}>
            © 2025 Stock King. All rights reserved.
          </div>
        </div>
      </footer>
    </div>
  )
}

function toNum(v: any): number | undefined {
  if (v == null) return undefined
  const n = Number(String(v).replace(/[^0-9.-]/g, ''))
  return Number.isFinite(n) ? n : undefined
}

// 장 마감 시간 체크 (한국 시간 기준)
function isMarketOpen(): boolean {
  const now = new Date()
  const dayOfWeek = now.getDay()
  const hours = now.getHours()
  const minutes = now.getMinutes()

  // 주말 체크
  if (dayOfWeek === 0 || dayOfWeek === 6) return false

  // 장 시간 체크 (09:00 ~ 15:30)
  const currentTime = hours * 60 + minutes
  const marketOpen = 9 * 60 // 09:00
  const marketClose = 15 * 60 + 30 // 15:30

  return currentTime >= marketOpen && currentTime <= marketClose
}

// JWT 페이로드를 UTF-8로 안전하게 디코딩
function decodeJwtPayload(token: string): any {
  const part = token.split('.')[1]
  const b64 = part.replace(/-/g, '+').replace(/_/g, '/')
  // 패딩 보정
  const padLen = (4 - (b64.length % 4)) % 4
  const padded = b64 + '='.repeat(padLen)
  const binary = atob(padded)
  const bytes = Uint8Array.from(binary, c => c.charCodeAt(0))
  const json = new TextDecoder('utf-8').decode(bytes)
  return JSON.parse(json)
}

function LogoCell({ name, ticker, logoUrl }: { name: string; ticker: string; logoUrl?: string }) {
  const fallbackBg = '#e5e7eb'
  const initials = (name || ticker || '?').slice(0, 2)
  const safeName = (name || '').replace(/[\\/#?&%:"*<>|]/g, '').replace(/\s+/g, '')
  const src = logoUrl ?? `/logos/${safeName}.png`
  const onError = (e: any) => {
    e.currentTarget.style.display = 'none'
    const sib = e.currentTarget.nextSibling as HTMLElement | null
    if (sib) sib.style.display = 'flex'
  }
  return (
    <div style={{ display: 'flex', alignItems: 'center' }}>
      <img src={src} alt={name} width={32} height={32} style={{ borderRadius: 6, objectFit: 'contain', background: '#fff', border: '1px solid #e5e7eb' }} onError={onError} />
      <div style={{ display: 'none', width: 32, height: 32, borderRadius: 6, alignItems: 'center', justifyContent: 'center', background: fallbackBg, color: '#374151', fontWeight: 600, fontSize: 12, border: '1px solid #e5e7eb' }}>
        {initials}
      </div>
    </div>
  )
}


function FeatureCard({ title, desc, emoji }: { title: string; desc: string; emoji: string }) {
  return (
    <div 
      style={{
        border: '1px solid #e5e7eb',
        borderRadius: 14,
        background: 'linear-gradient(180deg, #ffffff 0%, #f8fafc 100%)',
        padding: 18,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        gap: 10,
        boxShadow: '0 1px 2px rgba(0,0,0,0.04)',
        transition: 'all 0.3s ease',
        cursor: 'default'
      }}
      onMouseOver={(e) => {
        e.currentTarget.style.transform = 'translateY(-4px)'
        e.currentTarget.style.boxShadow = '0 8px 16px rgba(0,0,0,0.12)'
        e.currentTarget.style.borderColor = '#c7d2fe'
      }}
      onMouseOut={(e) => {
        e.currentTarget.style.transform = 'translateY(0)'
        e.currentTarget.style.boxShadow = '0 1px 2px rgba(0,0,0,0.04)'
        e.currentTarget.style.borderColor = '#e5e7eb'
      }}
    >
      <div style={{ 
        width: 48, 
        height: 48, 
        borderRadius: 999, 
        background: '#eef2ff', 
        display: 'flex', 
        alignItems: 'center', 
        justifyContent: 'center', 
        fontSize: 22,
        transition: 'transform 0.3s ease'
      }}>
        {emoji}
      </div>
      <div style={{ textAlign: 'center' }}>
        <div style={{ fontWeight: 800, color: '#0f172a', marginBottom: 6, fontSize: 16 }}>{title}</div>
        <div style={{ color: '#475569', fontSize: 13, lineHeight: 1.5 }}>{desc}</div>
      </div>
    </div>
  )
}

function StatisticsSection({ rows }: { rows: Row[] }) {
  const totalStocks = rows.length
  const risingCount = rows.filter(r => r.changeRate > 0).length
  const fallingCount = rows.filter(r => r.changeRate < 0).length
  const avgChangeRate = rows.length > 0 
    ? rows.reduce((sum, r) => sum + (r.changeRate || 0), 0) / rows.length 
    : 0
  const totalVolume = rows.reduce((sum, r) => sum + (r.volume || 0), 0)

  const stats = [
    { label: '전체 종목', value: totalStocks.toLocaleString(), icon: '📊', color: '#3b82f6' },
    { label: '상승 종목', value: risingCount.toLocaleString(), icon: '📈', color: '#dc2626' },
    { label: '하락 종목', value: fallingCount.toLocaleString(), icon: '📉', color: '#2563eb' },
    { label: '평균 등락률', value: `${avgChangeRate >= 0 ? '+' : ''}${avgChangeRate.toFixed(2)}%`, icon: '📊', color: avgChangeRate >= 0 ? '#dc2626' : '#2563eb' },
  ]

  return (
    <div style={{ 
      background: 'linear-gradient(135deg, #f1f5f9 0%, #e2e8f0 50%, #cbd5e1 100%)',
      borderRadius: 16,
      padding: '24px',
      boxShadow: '0 8px 24px rgba(148, 163, 184, 0.2)'
    }}>
      <h3 style={{ 
        margin: '0 0 20px', 
        fontSize: 20, 
        fontWeight: 700, 
        color: '#0f172a',
        textAlign: 'center'
      }}>
        📊 실시간 시장 현황
      </h3>
      <div style={{ 
        display: 'grid', 
        gridTemplateColumns: 'repeat(4, 1fr)', 
        gap: 16 
      }}>
        {stats.map((stat, idx) => (
          <div
            key={idx}
            style={{
              background: 'rgba(255, 255, 255, 0.95)',
              borderRadius: 12,
              padding: '20px',
              textAlign: 'center',
              transition: 'all 0.3s ease',
              cursor: 'default'
            }}
            onMouseOver={(e) => {
              e.currentTarget.style.transform = 'translateY(-4px)'
              e.currentTarget.style.boxShadow = '0 8px 16px rgba(0,0,0,0.15)'
            }}
            onMouseOut={(e) => {
              e.currentTarget.style.transform = 'translateY(0)'
              e.currentTarget.style.boxShadow = 'none'
            }}
          >
            <div style={{ fontSize: 28, marginBottom: 8 }}>{stat.icon}</div>
            <div style={{ 
              fontSize: 24, 
              fontWeight: 800, 
              color: stat.color,
              marginBottom: 4
            }}>
              {stat.value}
            </div>
            <div style={{ 
              fontSize: 13, 
              color: '#6b7280',
              fontWeight: 600
            }}>
              {stat.label}
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}

function TopStockCard({ title, rows, color }: { title: string; rows: Row[]; color: string }) {
  if (rows.length === 0) return null

  return (
    <div
      style={{
        background: 'white',
        borderRadius: 16,
        padding: '20px',
        border: `2px solid ${color}20`,
        boxShadow: '0 4px 12px rgba(0,0,0,0.08)',
        transition: 'all 0.3s ease'
      }}
      onMouseOver={(e) => {
        e.currentTarget.style.transform = 'translateY(-2px)'
        e.currentTarget.style.boxShadow = `0 8px 20px ${color}30`
      }}
      onMouseOut={(e) => {
        e.currentTarget.style.transform = 'translateY(0)'
        e.currentTarget.style.boxShadow = '0 4px 12px rgba(0,0,0,0.08)'
      }}
    >
      <h3 style={{ 
        margin: '0 0 16px', 
        fontSize: 18, 
        fontWeight: 800, 
        color: '#0f172a',
        display: 'flex',
        alignItems: 'center',
        gap: 8
      }}>
        <span>{title}</span>
      </h3>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        {rows.map((row, idx) => (
          <Link
            key={row.ticker}
            to={`/stocks/${row.ticker}/chart`}
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              padding: '12px',
              background: idx === 0 ? `${color}10` : '#f9fafb',
              borderRadius: 10,
              textDecoration: 'none',
              color: 'inherit',
              transition: 'all 0.2s ease'
            }}
            onMouseOver={(e) => {
              e.currentTarget.style.background = idx === 0 ? `${color}20` : '#f3f4f6'
              e.currentTarget.style.transform = 'translateX(4px)'
            }}
            onMouseOut={(e) => {
              e.currentTarget.style.background = idx === 0 ? `${color}10` : '#f9fafb'
              e.currentTarget.style.transform = 'translateX(0)'
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
              <div style={{
                width: 32,
                height: 32,
                borderRadius: 8,
                background: color,
                color: 'white',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: 14,
                fontWeight: 800
              }}>
                {idx + 1}
              </div>
              <div>
                <div style={{ fontWeight: 700, color: '#0f172a', fontSize: 14 }}>
                  {row.name}
                </div>
                <div style={{ fontSize: 12, color: '#6b7280', marginTop: 2 }}>
                  {row.ticker}
                </div>
              </div>
            </div>
            <div style={{ textAlign: 'right' }}>
              <div style={{ 
                fontSize: 16, 
                fontWeight: 800, 
                color: color,
                marginBottom: 2
              }}>
                {row.changeRate > 0 ? '+' : ''}{row.changeRate.toFixed(2)}%
              </div>
              <div style={{ fontSize: 12, color: '#6b7280' }}>
                {row.price?.toLocaleString()}원
              </div>
            </div>
          </Link>
        ))}
      </div>
    </div>
  )
}


