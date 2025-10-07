import { useEffect, useMemo, useRef, useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { createStompClient } from '../lib/socket'

export default function Detail() {
  const { ticker = '' } = useParams()
  const [lastPrice, setLastPrice] = useState<number | undefined>(undefined)
  const [lastTime, setLastTime] = useState<string | undefined>(undefined)
  const [companyName, setCompanyName] = useState<string>('')
  const [logoUrl, setLogoUrl] = useState<string | undefined>(undefined)
  const stompRef = useRef<ReturnType<typeof createStompClient> | null>(null)

  // 회사 정보 로드 함수
  const loadCompanyInfo = async () => {
    if (!ticker) return
    
    try {
      const response = await fetch(`/api/v1/stocks`)
      if (response.ok) {
        const result = await response.json()
        const stocks = result.data
        if (stocks && Array.isArray(stocks)) {
          const stock = stocks.find(s => s.ticker === ticker)
          if (stock) {
            if (stock.companyName) {
              setCompanyName(stock.companyName)
              // 로고 URL을 기업명으로 직접 생성
              setLogoUrl(`/logos/${stock.companyName}.png`)
            }
          }
        }
      }
    } catch (error) {
      console.error('회사 정보 로드 실패:', error)
    }
  }

  const onTick = useMemo(() => (payload: any) => {
    const code = String(payload?.ticker ?? payload?.symbol ?? '')
    if (code !== ticker) return
    const price = toNum(payload?.price ?? payload?.stck_prpr)
    const tradeTime = String(payload?.tradeTime ?? payload?.stck_cntg_hour ?? '')
    if (price == null) return
    
    // 장 마감 시간(15:30) 이후에는 실시간 업데이트 중단
    if (!isMarketOpen()) return
    
    setLastPrice(price)
    setLastTime(tradeTime)
  }, [ticker])

  useEffect(() => {
    loadCompanyInfo()
    const client = createStompClient(onTick)
    client.activate()
    stompRef.current = client
    return () => { client.deactivate() }
  }, [onTick])


  return (
    <div style={{ maxWidth: 960, margin: '0 auto', padding: 16 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12 }}>
        <Link to="/" style={{ textDecoration: 'none' }}>← 목록</Link>
        <LogoCell name={companyName || ticker} ticker={ticker} logoUrl={logoUrl} />
        <h2 style={{ margin: 0 }}>{companyName || ticker}</h2>
        {lastPrice != null && <span style={{ color: '#6b7280' }}>{lastPrice.toLocaleString()}원</span>}
      </div>
      
      <div style={{ marginBottom: 16 }}>
        <Link 
          to={`/stocks/${ticker}/chart`} 
          style={{
            display: 'inline-block',
            padding: '12px 24px',
            background: '#2962FF',
            color: 'white',
            textDecoration: 'none',
            borderRadius: 6,
            fontWeight: 'bold'
          }}
        >
          📈 차트 보기
        </Link>
      </div>
      
      <div style={{ padding: 20, border: '1px solid #ddd', borderRadius: 8, background: '#f9f9f9' }}>
        <p>실시간 가격 정보가 여기에 표시됩니다.</p>
        {lastPrice != null && (
          <div>
            <p><strong>현재가:</strong> {lastPrice.toLocaleString()}원</p>
            {lastTime && <p><strong>거래시간:</strong> {lastTime}</p>}
          </div>
        )}
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
  const kst = new Date(now.getTime() + (9 * 60 * 60 * 1000)) // UTC+9
  const dayOfWeek = kst.getDay()
  const hours = kst.getHours()
  const minutes = kst.getMinutes()
  
  // 주말 체크
  if (dayOfWeek === 0 || dayOfWeek === 6) return false
  
  // 장 시간 체크 (09:00 ~ 15:30)
  const currentTime = hours * 60 + minutes
  const marketOpen = 9 * 60 // 09:00
  const marketClose = 15 * 60 + 30 // 15:30
  
  return currentTime >= marketOpen && currentTime <= marketClose
}

function LogoCell({ name, ticker, logoUrl }: { name: string; ticker: string; logoUrl?: string }) {
  const fallbackBg = '#e5e7eb'
  const initials = (name || ticker || '?').slice(0, 2)
  
  // 로고 URL 우선 사용, 없으면 기업명으로 생성
  const src = logoUrl || `/logos/${name || ticker}.png`
  
  console.log('로고 로딩 시도:', { name, ticker, logoUrl, src })
  
  const onError = (e: any) => {
    console.log('로고 로딩 실패:', src)
    e.currentTarget.style.display = 'none'
    const sib = e.currentTarget.nextSibling as HTMLElement | null
    if (sib) sib.style.display = 'flex'
  }
  
  return (
    <div style={{ display: 'flex', alignItems: 'center' }}>
      <img 
        src={src} 
        alt={name} 
        width={40} 
        height={40} 
        style={{ 
          borderRadius: 8, 
          objectFit: 'contain', 
          background: '#fff', 
          border: '1px solid #e5e7eb' 
        }} 
        onError={onError}
        onLoad={() => console.log('로고 로딩 성공:', src)}
      />
      <div style={{ 
        display: 'none', 
        width: 40, 
        height: 40, 
        borderRadius: 8, 
        alignItems: 'center', 
        justifyContent: 'center', 
        background: fallbackBg, 
        color: '#374151', 
        fontWeight: 600, 
        fontSize: 14, 
        border: '1px solid #e5e7eb' 
      }}>
        {initials}
      </div>
    </div>
  )
}
