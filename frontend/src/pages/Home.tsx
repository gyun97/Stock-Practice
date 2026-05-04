import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { createStompClient } from '../lib/socket'
import { tokenManager } from '../lib/tokenManager'
import KospiWidget from '../components/KospiWidget'

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
  const [userInfo, setUserInfo] = useState<{ userId: string, email: string, name: string, profileImage?: string } | null>(null)
  const [showDropdown, setShowDropdown] = useState(false)
  const [userStocks, setUserStocks] = useState<UserStock[]>([])
  const [sortBy, setSortBy] = useState<'volume' | 'price' | 'rise' | 'fall'>('volume')
  const [userStockSortBy, setUserStockSortBy] = useState<'quantity' | 'changeRate'>('quantity')
  const [rankings, setRankings] = useState<Array<{ userId: number, userName: string, totalAsset: number, returnRate: number, rank: number }>>([])
  const loaderRef = useRef<HTMLDivElement | null>(null)
  const stompRef = useRef<ReturnType<typeof createStompClient> | null>(null)
  const dropdownRef = useRef<HTMLDivElement | null>(null)
  const [searchParams, setSearchParams] = useSearchParams()

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

  // OAuth 토큰 처리
  useEffect(() => {
    const token = searchParams.get('token')
    if (token) {
      console.log('OAuth 토큰 받음:', token)

      // 토큰을 메모리에 저장 (localStorage에서 기존 토큰 제거)
      localStorage.removeItem('accessToken')
      tokenManager.setTokens(token)

      // 사용자 정보를 서버에서 가져와 저장
      const fetchUserInfo = async () => {
        try {
          const response = await tokenManager.authenticatedFetch('/api/v1/users/me')
          if (response.ok) {
            const result = await response.json()
            const data = result.data || result
            const userInfo = {
              userId: String(data.userId || ''),
              email: data.email || '',
              name: data.name || '',
              profileImage: data.profileImage || ''
            }
            localStorage.setItem('userInfo', JSON.stringify(userInfo))
            localStorage.setItem('loginMethod', 'oauth')
            setUserInfo(userInfo)
            console.log('OAuth 로그인 및 정보 로드 완료:', userInfo)
          }
        } catch (error) {
          console.error('사용자 정보 로드 실패:', error)
        }
      }

      fetchUserInfo()
      setSearchParams({})
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

  // 게스트 로그인 함수
  const handleGuestLogin = async () => {
    const apiBase = import.meta.env.VITE_API_BASE_URL || ''
    try {
      const response = await fetch(`${apiBase}/api/v1/users/guest-login`, {
        method: 'POST',
        credentials: 'include'
      })
      if (response.ok) {
        const result = await response.json()
        const data = result.data || result
        const newUserInfo = {
          userId: String(data.userId || ''),
          email: data.email || '',
          name: data.name || '',
          profileImage: data.profileImage || ''
        }
        localStorage.removeItem('accessToken')
        tokenManager.setTokens(data.accessToken)
        localStorage.setItem('userInfo', JSON.stringify(newUserInfo))
        localStorage.setItem('loginMethod', 'guest')
        // 게스트 계정 보관 정책 안내
        alert('체험용 계정은 보안 및 정책상 2일 후 자동 삭제됩니다.\n데이터를 보존하려면 정식 회원가입을 이용해 주세요.')
        // 메인 페이지 새로고침하여 앱 전체 상태 갱신
        window.location.href = '/'
      } else {
        const errorMsg = `게스트 로그인 실패: ${response.status}`
        console.error(errorMsg)
        alert('게스트 로그인에 실패했습니다. 서버 상태를 확인해주세요.')
      }
    } catch (err) {
      console.error('게스트 로그인 오류:', err)
      alert('네트워크 오류가 발생했습니다.')
    }
  }

  // 랭킹 데이터 가져오기
  const fetchRankings = async () => {
    const apiBase = import.meta.env.VITE_API_BASE_URL || ''
    try {
      console.log('랭킹 API 호출 시작:', `${apiBase}/api/v1/portfolios/ranking?limit=10`)
      const response = await fetch(`${apiBase}/api/v1/portfolios/ranking?limit=10`)
      console.log('랭킹 API 응답 상태:', response.status)
      if (response.ok) {
        const result = await response.json()
        console.log('랭킹 API 응답 데이터:', result)
        const rankingsData = result.data || []
        console.log('랭킹 데이터:', rankingsData)
        setRankings(rankingsData)
      } else {
        const errorText = await response.text()
        console.error('랭킹 조회 실패:', response.status, errorText)
      }
    } catch (err) {
      console.error('랭킹 조회 오류:', err)
    }
  }

  // 초기 페이지 로드
  useEffect(() => {
    const apiBase = import.meta.env.VITE_API_BASE_URL || ''
    fetch(`${apiBase}/api/v1/stocks`)
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

    // 랭킹 데이터도 함께 가져오기
    fetchRankings()
  }, [])

  // STOMP 실시간 업데이트 반영 (Redis Pub/Sub -> 백엔드 브로드캐스트를 전제로 /topic/stocks 구독)

  // 매 렌더마다 ref를 최신 함수로 갱신 → STOMP 클라이언트 재생성 없이 항상 최신 sortBy/userStocks 참조 가능
  const onTickRef = useRef<(payload: any, raw: string) => void>(null!)
  onTickRef.current = (payload: any, raw: string) => {
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

    setRows(prev => {
      const found = prev.some(r => r.ticker === code)
      const updated = found
        ? prev.map(r =>
          r.ticker === code
            ? { ...r, price, changeRate: changeRate ?? r.changeRate, name: companyName ?? r.name, logoUrl: logoUrl ?? r.logoUrl, volume: volume ?? r.volume }
            : r
        )
        : [...prev, { ticker: code, name: companyName ?? code, price, changeRate: changeRate ?? 0, logoUrl, volume: volume ?? 0 }]
      const sorted = sortRows(updated, sortBy)

      if (userStocks.length > 0) {
        const updatedStocks = userStocks.map(stock => {
          if (stock.ticker === code) {
            return {
              ...stock,
              currentPrice: price ?? stock.currentPrice,
              changeRate: changeRate ?? stock.changeRate,
              logoUrl: logoUrl ?? stock.logoUrl
            }
          }
          return stock
        })
        const sortedStocks = sortUserStocks(updatedStocks, userStockSortBy)
        setUserStocks(sortedStocks)
      }

      return sorted
    })
  }

  // STOMP 클라이언트: 마운트 시 1회만 생성 (sortBy 등 state가 바뀌어도 재연결하지 않음)
  // stableHandler는 변경되지 않고, 내부에서 onTickRef.current를 호출해 항상 최신 콜백 사용
  useEffect(() => {
    const stableHandler = (payload: any, raw: string) => onTickRef.current(payload, raw)
    const client = createStompClient(stableHandler)
    client.activate()
    stompRef.current = client
    return () => { client.deactivate() }
  }, [])

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
    <div className="home-wrapper">

      {/* ── GNB ── */}
      <nav className="gnb">
        <div className="gnb-inner">
          <div className="gnb-brand">
            <img src="/logos/Stock King2.jpg" alt="Stock King" className="gnb-brand-logo"
              onError={(e) => { e.currentTarget.style.display = 'none' }} />
            <span className="gnb-brand-name">Stock King</span>
            <span style={{ fontSize: 11, fontWeight: 600, color: '#868e96', marginLeft: 6, padding: '2px 7px', border: '1px solid #dee2e6', borderRadius: 3 }}>모의투자</span>
          </div>
          <div className="gnb-actions">
            {userInfo ? (
              <div ref={dropdownRef} style={{ position: 'relative' }}>
                <button
                  onClick={() => setShowDropdown(!showDropdown)}
                  style={{
                    width: 44, height: 44, borderRadius: '50%',
                    background: showDropdown ? '#f1f3f5' : '#f8f9fa',
                    border: '2px solid #dee2e6',
                    cursor: 'pointer', display: 'flex', alignItems: 'center',
                    justifyContent: 'center', transition: 'all 0.2s', padding: 0,
                    overflow: 'hidden'
                  }}
                  onMouseOver={(e) => { e.currentTarget.style.borderColor = '#adb5bd' }}
                  onMouseOut={(e) => { e.currentTarget.style.borderColor = '#dee2e6' }}
                >
                  {userInfo.profileImage
                    ? <img src={userInfo.profileImage} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} onError={(e) => { e.currentTarget.style.display = 'none' }} />
                    : <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#868e96" strokeWidth="2"><circle cx="12" cy="8" r="4"/><path d="M4 20c0-4 3.6-7 8-7s8 3 8 7"/></svg>
                  }
                </button>
                {showDropdown && (
                  <div style={{ position: 'absolute', top: 50, right: 0, background: 'white', borderRadius: 8, boxShadow: '0 4px 16px rgba(0,0,0,0.12)', minWidth: 160, border: '1px solid #e8eaed', overflow: 'hidden', zIndex: 1000 }}>
                    <div style={{ padding: '12px 16px', borderBottom: '1px solid #f1f3f5' }}>
                      <div style={{ fontSize: 13, fontWeight: 700, color: '#1e2329' }}>{userInfo.name || '사용자'}</div>
                      <div style={{ fontSize: 12, color: '#868e96', marginTop: 2 }}>{userInfo.email}</div>
                    </div>
                    {[{ to: '/mypage', label: '마이페이지' }, { to: '/order-management', label: '주문 내역' }].map(({ to, label }) => (
                      <Link key={to} to={to} onClick={() => setShowDropdown(false)}
                        style={{ display: 'block', padding: '11px 16px', textDecoration: 'none', color: '#1e2329', fontSize: 13, fontWeight: 500, borderBottom: '1px solid #f1f3f5' }}
                        onMouseOver={(e) => { e.currentTarget.style.background = '#f8f9fa' }}
                        onMouseOut={(e) => { e.currentTarget.style.background = 'white' }}
                      >{label}</Link>
                    ))}
                    <button onClick={() => { setShowDropdown(false); handleLogout() }}
                      style={{ width: '100%', padding: '11px 16px', textAlign: 'left', background: 'white', border: 'none', color: '#e03131', fontSize: 13, fontWeight: 500, cursor: 'pointer', fontFamily: 'inherit' }}
                      onMouseOver={(e) => { e.currentTarget.style.background = '#fff5f5' }}
                      onMouseOut={(e) => { e.currentTarget.style.background = 'white' }}
                    >로그아웃</button>
                  </div>
                )}
              </div>
            ) : (
              <div style={{ display: 'flex', gap: 6 }}>
                <button onClick={handleGuestLogin}
                  style={{ padding: '7px 14px', borderRadius: 6, background: 'white', border: '1px solid #dee2e6', fontSize: 13, fontWeight: 600, color: '#1e2329', cursor: 'pointer', fontFamily: 'inherit' }}
                  onMouseOver={(e) => { e.currentTarget.style.background = '#f1f3f5' }}
                  onMouseOut={(e) => { e.currentTarget.style.background = 'white' }}
                >게스트 체험</button>
                <Link to="/login"
                  style={{ padding: '7px 14px', borderRadius: 6, background: '#1971c2', color: 'white', textDecoration: 'none', fontSize: 13, fontWeight: 600, border: '1px solid #1971c2', display: 'inline-flex', alignItems: 'center' }}
                  onMouseOver={(e) => { e.currentTarget.style.background = '#1864ab' }}
                  onMouseOut={(e) => { e.currentTarget.style.background = '#1971c2' }}
                >로그인</Link>
              </div>
            )}
          </div>
        </div>
      </nav>

      {/* ── 페이지 본문 ── */}
      <div className="container">

        {/* 히어로 */}
        <div className="hero-section">
          <h1 className="hero-title">국내 주식</h1>
          <p className="hero-desc">실시간 시세 · 차트 · 주문을 한 곳에서 관리하세요</p>
        </div>

        {/* 코스피 위젯 */}
        <div style={{ marginBottom: 16 }}>
          <KospiWidget />
        </div>

        {/* 시장 현황 */}
        <div style={{ marginBottom: 24 }}>
          <StatisticsSection rows={rows} />
        </div>

        {/* 상승/하락 TOP 3 */}
        {rows.length > 0 && (
          <div style={{ marginBottom: 20 }}>
            <div className="grid-2">
              <TopStockCard title="상승률 TOP 3" rows={rows.filter(r => r.changeRate > 0).sort((a, b) => b.changeRate - a.changeRate).slice(0, 3)} color="#c92a2a" />
              <TopStockCard title="하락률 TOP 3" rows={rows.filter(r => r.changeRate < 0).sort((a, b) => a.changeRate - b.changeRate).slice(0, 3)} color="#1864ab" />
            </div>
          </div>
        )}

        {/* 유저 랭킹 */}
        <div style={{ marginBottom: 20 }}>
          <div className="section-header">
            <div>
              <div className="section-title">랭킹</div>
              <div className="section-sub">총 자산 기준 상위 10명</div>
            </div>
          </div>
          <div style={{ border: '1px solid #e8eaed', borderRadius: 8, overflow: 'hidden', background: 'white' }}>
            <div className="ranking-columns stock-table-header">
              <div style={{ textAlign: 'center' }}>순위</div>
              <div>사용자</div>
              <div className="mobile-hidden" style={{ textAlign: 'right' }}>총 자산</div>
              <div style={{ textAlign: 'right' }}>수익률</div>
            </div>
            {rankings.map((ranking, idx) => (
              <div key={ranking.userId} className="ranking-columns stock-table-row"
                style={{ borderBottom: idx < rankings.length - 1 ? '1px solid #f1f3f5' : 'none', background: 'white' }}
                onMouseOver={(e) => { e.currentTarget.style.background = '#f8f9fa' }}
                onMouseOut={(e) => { e.currentTarget.style.background = 'white' }}
              >
                <div style={{ textAlign: 'center' }}>
                  <span style={{ display: 'inline-flex', alignItems: 'center', justifyContent: 'center', width: 24, height: 24, borderRadius: 4, fontSize: 12, fontWeight: 800, background: ranking.rank === 1 ? '#c92a2a' : ranking.rank === 2 ? '#868e96' : ranking.rank === 3 ? '#9c6b00' : '#f1f3f5', color: ranking.rank <= 3 ? 'white' : '#495057' }}>{ranking.rank}</span>
                </div>
                <div style={{ fontSize: 14, fontWeight: 600, color: '#1e2329' }}>{ranking.userName}</div>
                <div className="mobile-hidden" style={{ textAlign: 'right', fontSize: 14, fontWeight: 600, color: '#1e2329' }}>{ranking.totalAsset.toLocaleString()}원</div>
                <div style={{ textAlign: 'right' }}>
                  <span style={{ display: 'inline-flex', alignItems: 'center', padding: '3px 8px', borderRadius: 3, fontWeight: 700, fontSize: 12, color: ranking.returnRate >= 0 ? '#c92a2a' : '#1864ab', background: ranking.returnRate >= 0 ? '#fff0f0' : '#e8f0fe' }}>
                    {ranking.returnRate >= 0 ? '+' : ''}{ranking.returnRate.toFixed(2)}%
                  </span>
                </div>
              </div>
            ))}
            {rankings.length === 0 && (
              <div style={{ padding: '32px', textAlign: 'center', color: '#868e96', fontSize: 13 }}>랭킹 데이터를 불러오는 중...</div>
            )}
          </div>
        </div>

        {/* 보유 종목 */}
        {userInfo && userStocks.length > 0 && (
          <div style={{ marginBottom: 20 }}>
            <div className="section-header">
              <div>
                <div className="section-title">내 보유 종목</div>
                <div className="section-sub">실시간 시세 기준</div>
              </div>
              <div className="sort-buttons">
                {[{ key: 'quantity', label: '보유량' }, { key: 'changeRate', label: '등락률' }].map(({ key, label }) => (
                  <button key={key} className={`sort-btn${userStockSortBy === key ? ' active' : ''}`}
                    onClick={() => setUserStockSortBy(key as 'quantity' | 'changeRate')}
                  >{label}</button>
                ))}
              </div>
            </div>
            <div style={{ border: '1px solid #e8eaed', borderRadius: 8, overflow: 'hidden', background: 'white' }}>
              <div className="user-stocks-columns stock-table-header">
                <div />
                <div>종목</div>
                <div className="optional-col" style={{ textAlign: 'right' }}>보유량</div>
                <div style={{ textAlign: 'right' }}>현재가</div>
                <div style={{ textAlign: 'right' }}>등락률</div>
              </div>
              {userStocks.map((stock, idx) => {
                const cr = stock.changeRate ?? 0
                return (
                  <Link key={stock.ticker} to={`/stocks/${stock.ticker}/chart`} className="user-stocks-columns stock-table-row"
                    style={{ textDecoration: 'none', color: 'inherit', background: idx % 2 ? '#fff' : '#fcfcfd' }}
                    onMouseOver={(e) => { e.currentTarget.style.background = '#f8f9fa' }}
                    onMouseOut={(e) => { e.currentTarget.style.background = idx % 2 ? '#fff' : '#fcfcfd' }}
                  >
                    <LogoCell name={stock.companyName} ticker={stock.ticker} logoUrl={stock.logoUrl} />
                    <div style={{ display: 'flex', flexDirection: 'column', minWidth: 0, marginLeft: 4 }}>
                      <span className="name-text" style={{ fontWeight: 700, color: '#1e2329', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{stock.companyName}</span>
                      <span className="ticker-text" style={{ fontSize: 12, color: '#868e96', marginTop: 2 }}>{stock.ticker}</span>
                    </div>
                    <div className="optional-col" style={{ textAlign: 'right', fontVariantNumeric: 'tabular-nums', color: '#495057', fontWeight: 600 }}>{stock.totalQuantity.toLocaleString()}주</div>
                    <div className="price-cell" style={{ textAlign: 'right', fontVariantNumeric: 'tabular-nums', fontWeight: 700, color: '#1e2329' }}>{stock.currentPrice != null ? `${stock.currentPrice.toLocaleString()}원` : '-'}</div>
                    <div className="change-cell" style={{ textAlign: 'right', display: 'flex', justifyContent: 'flex-end' }}>
                      <span className="change-rate-badge" style={{ padding: '3px 8px', borderRadius: 3, fontWeight: 700, fontSize: 12, color: cr > 0 ? '#c92a2a' : cr < 0 ? '#1864ab' : '#495057', background: cr > 0 ? '#fff0f0' : cr < 0 ? '#e8f0fe' : '#f1f3f5' }}>
                        {cr > 0 ? '▲' : cr < 0 ? '▼' : ''} {cr.toFixed(2)}%
                      </span>
                    </div>
                  </Link>
                )
              })}
            </div>
          </div>
        )}

        {/* 전체 종목 목록 */}
        <div style={{ marginBottom: 40 }}>
          <div className="section-header">
            <div>
              <div className="section-title">종목 목록</div>
              <div className="section-sub">실시간 시세 정보</div>
            </div>
            <div className="sort-buttons">
              {[{ key: 'volume', label: '거래량' }, { key: 'price', label: '가격' }, { key: 'rise', label: '상승' }, { key: 'fall', label: '하락' }].map(({ key, label }) => (
                <button key={key} className={`sort-btn${sortBy === key ? ' active' : ''}`}
                  onClick={() => setSortBy(key as 'volume' | 'price' | 'rise' | 'fall')}
                >{label}</button>
              ))}
            </div>
          </div>
          <div className="table-wrapper">
            <div style={{ border: '1px solid #e8eaed', borderRadius: 8, background: 'white' }}>
              <div className="all-stocks-columns stock-table-header">
                <div style={{ textAlign: 'center' }}>순위</div>
                <div />
                <div>종목</div>
                <div style={{ textAlign: 'right' }}>현재가</div>
                <div style={{ textAlign: 'right' }}>등락률</div>
                <div className="volume-cell" style={{ textAlign: 'right' }}>거래량</div>
              </div>
              {rows.map((row, idx) => (
                <Link key={row.ticker} to={`/stocks/${row.ticker}/chart`} className="all-stocks-columns stock-table-row"
                  style={{ textDecoration: 'none', color: 'inherit', background: idx % 2 ? '#fff' : '#fcfcfd' }}
                  onMouseOver={(e) => { e.currentTarget.style.background = '#f8f9fa' }}
                  onMouseOut={(e) => { e.currentTarget.style.background = idx % 2 ? '#fff' : '#fcfcfd' }}
                >
                  <div className="rank-cell" style={{ textAlign: 'center', fontSize: 13, fontWeight: idx < 3 ? 800 : 600, color: idx === 0 ? '#c92a2a' : idx === 1 ? '#868e96' : idx === 2 ? '#9c6b00' : '#adb5bd' }}>{idx + 1}</div>
                  <LogoCell name={row.name} ticker={row.ticker} logoUrl={row.logoUrl} />
                  <div style={{ display: 'flex', flexDirection: 'column', minWidth: 0, marginLeft: 4 }}>
                    <span className="name-text" style={{ fontWeight: 700, color: '#1e2329', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{row.name}</span>
                    <span className="ticker-text" style={{ fontSize: 12, color: '#868e96', marginTop: 2 }}>{row.ticker}</span>
                  </div>
                  <div className="price-cell" style={{ textAlign: 'right', fontVariantNumeric: 'tabular-nums', fontWeight: 700, color: '#1e2329' }}>{row.price != null ? `${row.price.toLocaleString()}원` : '-'}</div>
                  <div className="change-cell" style={{ textAlign: 'right', display: 'flex', justifyContent: 'flex-end' }}>
                    {row.changeRate != null ? (
                      <span className="change-rate-badge" style={{ padding: '3px 8px', borderRadius: 3, fontWeight: 700, fontSize: 12, color: row.changeRate > 0 ? '#c92a2a' : row.changeRate < 0 ? '#1864ab' : '#495057', background: row.changeRate > 0 ? '#fff0f0' : row.changeRate < 0 ? '#e8f0fe' : '#f1f3f5' }}>
                        {row.changeRate > 0 ? '▲' : row.changeRate < 0 ? '▼' : ''} {row.changeRate.toFixed(2)}%
                      </span>
                    ) : '-'}
                  </div>
                  <div className="volume-cell" style={{ textAlign: 'right', fontVariantNumeric: 'tabular-nums', color: '#495057' }}>{row.volume != null ? `${row.volume.toLocaleString()}주` : '-'}</div>
                </Link>
              ))}
              <div ref={loaderRef} style={{ height: 24 }} />
            </div>
          </div>
        </div>

      </div>{/* /container */}

      <footer style={{ background: '#fff', borderTop: '1px solid #e8eaed', padding: '24px 20px' }}>
        <div className="container" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 12 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <img src="/logos/Stock King2.jpg" alt="" style={{ width: 20, height: 20, objectFit: 'contain', borderRadius: 4 }} onError={(e) => { e.currentTarget.style.display = 'none' }} />
            <span style={{ fontSize: 14, fontWeight: 700, color: '#1e2329' }}>Stock King</span>
            <span style={{ fontSize: 12, color: '#868e96', marginLeft: 4 }}>모의 주식 투자 플랫폼</span>
          </div>
          <div style={{ fontSize: 12, color: '#adb5bd' }}>© 2025 Stock King. All rights reserved.</div>
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
  const s3BaseUrl = import.meta.env.VITE_S3_URL || ''
  const defaultSrc = s3BaseUrl ? `${s3BaseUrl}/logos/${safeName}.png` : `/logos/${safeName}.png`
  const src = logoUrl ?? defaultSrc
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
  const flatCount = rows.filter(r => r.changeRate === 0).length
  const fallingCount = rows.filter(r => r.changeRate < 0).length
  const avgChangeRate = rows.length > 0
    ? rows.reduce((sum, r) => sum + (r.changeRate || 0), 0) / rows.length
    : 0
  const riseRatio = totalStocks > 0 ? (risingCount / totalStocks) * 100 : 0
  const flatRatio = totalStocks > 0 ? (flatCount / totalStocks) * 100 : 0
  const fallRatio = totalStocks > 0 ? (fallingCount / totalStocks) * 100 : 0

  return (
    <div style={{ background: 'white', border: '1px solid #e8eaed', borderRadius: 8, padding: '20px 28px' }}>
      {/* 데스크탑: 1줄 가로 배치 / 모바일: 2줄 */}
      <div style={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: '16px 0', rowGap: 16 }}>

        {/* 레이블 */}
        <div style={{ fontSize: 12, fontWeight: 700, color: '#adb5bd', letterSpacing: 0.8, textTransform: 'uppercase', whiteSpace: 'nowrap', paddingRight: 24 }}>
          시장 현황
        </div>
        <div style={{ width: 1, height: 48, background: '#e8eaed', marginRight: 24, flexShrink: 0 }} />

        {/* 상승/보합/하락 */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 28, paddingRight: 28 }}>
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 4 }}>
            <span style={{ fontSize: 13, fontWeight: 700, color: '#c92a2a', letterSpacing: 0.4 }}>상승</span>
            <span style={{ fontSize: 36, fontWeight: 800, color: '#c92a2a', fontVariantNumeric: 'tabular-nums', lineHeight: 1 }}>{risingCount}</span>
          </div>
          <div style={{ width: 1, height: 32, background: '#e8eaed', flexShrink: 0 }} />
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 4 }}>
            <span style={{ fontSize: 13, fontWeight: 700, color: '#868e96', letterSpacing: 0.4 }}>보합</span>
            <span style={{ fontSize: 36, fontWeight: 800, color: '#868e96', fontVariantNumeric: 'tabular-nums', lineHeight: 1 }}>{flatCount}</span>
          </div>
          <div style={{ width: 1, height: 32, background: '#e8eaed', flexShrink: 0 }} />
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 4 }}>
            <span style={{ fontSize: 13, fontWeight: 700, color: '#1864ab', letterSpacing: 0.4 }}>하락</span>
            <span style={{ fontSize: 36, fontWeight: 800, color: '#1864ab', fontVariantNumeric: 'tabular-nums', lineHeight: 1 }}>{fallingCount}</span>
          </div>
        </div>

        <div style={{ width: 1, height: 48, background: '#e8eaed', marginRight: 24, flexShrink: 0 }} />

        {/* 비율 바 */}
        <div style={{ flex: 1, minWidth: 120 }}>
          <div style={{ display: 'flex', borderRadius: 4, overflow: 'hidden', height: 9 }}>
            <div style={{ width: `${riseRatio}%`, background: '#c92a2a', transition: 'width 0.5s' }} />
            <div style={{ width: `${flatRatio}%`, background: '#dee2e6', transition: 'width 0.5s' }} />
            <div style={{ width: `${fallRatio}%`, background: '#1864ab', transition: 'width 0.5s' }} />
          </div>
          <div style={{ fontSize: 12, color: '#adb5bd', marginTop: 6, textAlign: 'center', fontVariantNumeric: 'tabular-nums' }}>
            전체 {totalStocks}종목
          </div>
        </div>

        <div style={{ width: 1, height: 48, background: '#e8eaed', margin: '0 24px', flexShrink: 0 }} />

        {/* 평균 등락률 */}
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', whiteSpace: 'nowrap' }}>
          <div style={{ fontSize: 13, color: '#adb5bd', fontWeight: 600, letterSpacing: 0.4, marginBottom: 4 }}>평균 등락률</div>
          <div style={{ fontSize: 36, fontWeight: 800, fontVariantNumeric: 'tabular-nums', lineHeight: 1, color: avgChangeRate > 0 ? '#c92a2a' : avgChangeRate < 0 ? '#1864ab' : '#495057' }}>
            {avgChangeRate >= 0 ? '+' : ''}{avgChangeRate.toFixed(2)}%
          </div>
        </div>

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


