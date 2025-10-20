import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { createStompClient } from '../lib/socket'

type Row = {
  ticker: string
  name: string
  price: number
  changeRate: number
  logoUrl?: string
  volume: number
}

export default function Home() {
  const [rows, setRows] = useState<Row[]>([])
  const [page, setPage] = useState(0)
  const [hasMore, setHasMore] = useState(true)
  const [userInfo, setUserInfo] = useState<{userId: string, email: string, name: string} | null>(null)
  const loaderRef = useRef<HTMLDivElement | null>(null)
  const stompRef = useRef<ReturnType<typeof createStompClient> | null>(null)
  const [searchParams, setSearchParams] = useSearchParams()

  // OAuth 토큰 처리
  useEffect(() => {
    const token = searchParams.get('token')
    if (token) {
      console.log('OAuth 토큰 받음:', token)
      
      // 토큰을 localStorage에 저장
      localStorage.setItem('accessToken', token)
      
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
        setUserInfo(userInfo)
        
        console.log('OAuth 로그인 완료:', userInfo)
        
        // URL에서 토큰 파라미터 제거
        setSearchParams({})
      } catch (error) {
        console.error('토큰 파싱 오류:', error)
      }
    }
  }, [searchParams, setSearchParams])

  // 로그인 상태 확인
  useEffect(() => {
    const storedUserInfo = localStorage.getItem('userInfo')
    if (storedUserInfo) {
      try {
        setUserInfo(JSON.parse(storedUserInfo))
      } catch (e) {
        console.error('사용자 정보 파싱 오류:', e)
        localStorage.removeItem('userInfo')
        localStorage.removeItem('accessToken')
        localStorage.removeItem('refreshToken')
      }
    }
  }, [])

  // 로그아웃 함수
  const handleLogout = () => {
    localStorage.removeItem('userInfo')
    localStorage.removeItem('accessToken')
    localStorage.removeItem('refreshToken')
    setUserInfo(null)
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
        setRows(normalized)
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
      return found
        ? prev.map(r =>
            r.ticker === code
              ? { ...r, price, changeRate: changeRate ?? r.changeRate, name: companyName ?? r.name, logoUrl: logoUrl ?? r.logoUrl, volume: volume ?? r.volume }
              : r
          )
        : [...prev, { ticker: code, name: companyName ?? code, price, changeRate: changeRate ?? 0, logoUrl, volume: volume ?? 0 }]
    })
  }, [])

  useEffect(() => {
    const client = createStompClient(onTick)
    client.activate()
    stompRef.current = client
    return () => { client.deactivate() }
  }, [onTick])

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
    <div style={{ maxWidth: 960, margin: '0 auto', padding: 16 }}>
      {/* 헤더 - 제목과 로그인/회원가입 버튼 또는 사용자 정보 */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <h2 style={{ margin: 0 }}>실시간 차트 (정렬 기준: 거래량 순)</h2>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          {userInfo ? (
            <>
              <span style={{ fontSize: 14, color: '#374151' }}>
                안녕하세요, <strong>{userInfo.name}</strong>님!
              </span>
              <button
                onClick={handleLogout}
                style={{
                  padding: '8px 16px',
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
                로그아웃
              </button>
            </>
          ) : (
            <>
              <Link 
                to="/login" 
                style={{
                  padding: '8px 16px',
                  border: '1px solid #d1d5db',
                  borderRadius: 6,
                  background: 'white',
                  color: '#374151',
                  textDecoration: 'none',
                  fontSize: 14,
                  fontWeight: '500',
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
                로그인
              </Link>
              <Link 
                to="/signup" 
                style={{
                  padding: '8px 16px',
                  border: '1px solid #2962FF',
                  borderRadius: 6,
                  background: '#2962FF',
                  color: 'white',
                  textDecoration: 'none',
                  fontSize: 14,
                  fontWeight: '500',
                  transition: 'all 0.2s'
                }}
                onMouseOver={(e) => {
                  e.currentTarget.style.background = '#1d4ed8'
                  e.currentTarget.style.borderColor = '#1d4ed8'
                }}
                onMouseOut={(e) => {
                  e.currentTarget.style.background = '#2962FF'
                  e.currentTarget.style.borderColor = '#2962FF'
                }}
              >
                회원가입
              </Link>
            </>
          )}
        </div>
      </div>
      <div style={{ border: '1px solid #e5e7eb', borderRadius: 8, overflow: 'hidden' }}>
        {/* 헤더 라벨 제거 (로고 | 종목명 | 현재가 | 등락률 | 거래량) */}
        <div style={{ display: 'grid', gridTemplateColumns: '56px 1fr 140px 120px 140px', alignItems: 'center', padding: '8px 16px', background: '#f8fafc' }} />
        {rows.map(row => (
          <Link key={row.ticker} to={`/stocks/${row.ticker}/chart`} style={{ display: 'grid', gridTemplateColumns: '56px 1fr 140px 120px 140px', alignItems: 'center', gap: 12, padding: '12px 16px', borderTop: '1px solid #f1f5f9', textDecoration: 'none', color: 'inherit' }}>
            <LogoCell name={row.name} ticker={row.ticker} logoUrl={row.logoUrl} />
            <div>{row.name}</div>


            <div style={{ textAlign: 'right' }}>
              {row.price != null ? `${row.price.toLocaleString()}원` : '-'}
            </div>

            <div
              style={{
                textAlign: 'right',
                color:
                  row.changeRate != null
                    ? row.changeRate > 0
                      ? '#ef4444' // 양수: 빨강
                      : row.changeRate < 0
                      ? '#3b82f6' // 음수: 파랑 (Tailwind의 blue-500)
                      : '#374151' // 0일 때: 회색
                    : '#374151'
              }}
            >
              {row.changeRate != null ? `${row.changeRate.toFixed(2)}%` : '-'}
            </div>
            <div style={{ textAlign: 'right' }}>
              {row.volume != null ? `${row.volume.toLocaleString()}주` : '-'}
            </div>
          </Link>
        ))}
        <div ref={loaderRef} style={{ height: 24 }} />
      </div>
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


